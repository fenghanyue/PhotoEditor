package io.github.fenghanyue.photoeditor.ui.editor

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import io.github.fenghanyue.photoeditor.R
import io.github.fenghanyue.photoeditor.geo.Region
import io.github.fenghanyue.photoeditor.geo.TestRegions
import io.github.fenghanyue.photoeditor.meta.DeviceCatalog
import io.github.fenghanyue.photoeditor.meta.CaptureParameters
import io.github.fenghanyue.photoeditor.meta.GeoPoint
import io.github.fenghanyue.photoeditor.meta.PhotoMeta
import io.github.fenghanyue.photoeditor.render.TemplateKind
import io.github.fenghanyue.photoeditor.ui.theme.PhotoEditorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class EditorContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun snapshot(name: String, tag: String) {
        if (tag == DEVICE_NAME_EDITOR_TAG) composeRule.onNodeWithTag(tag).performScrollTo()
        // 等模式切换和弹层动画结束，再保存当前界面的实际绘制结果。
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val directory = File("build/device-picker-confirm-samples").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun showDeviceEditor(deviceName: DeviceNameState): MutableState<EditorState> {
        val state = mutableStateOf(EditorState(loading = false, meta = PhotoMeta(make = "OnePlus"), deviceName = deviceName))
        composeRule.setContent {
            PhotoEditorTheme {
                EditorContent(
                    state = state.value,
                    onBack = {},
                    onOpenInfo = {},
                    onOptionsChange = {},
                    onDeviceNameChange = { state.value = state.value.copy(deviceName = state.value.deviceName.edit(it)) },
                    onRegionSelect = {},
                    onSave = {},
                    onSaveResultShown = {},
                    onDeviceNameModeChange = { state.value = state.value.copy(deviceName = state.value.deviceName.useMode(it)) },
                    onDeviceModelSelect = { state.value = state.value.copy(deviceName = state.value.deviceName.select(it)) },
                )
            }
        }
        return state
    }

    private fun choosePhoneDraft() {
        composeRule.onNodeWithTag("deviceType-PHONE").performScrollTo().performClick()
        composeRule.onNodeWithTag(DEVICE_BRAND_FIELD_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag("deviceBrand-NIKON").assertDoesNotExist()
        composeRule.onNodeWithTag("deviceBrand-ONEPLUS").performClick()
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag("deviceModel-nikon-z5-ii").assertDoesNotExist()
        composeRule.onNodeWithTag("deviceModel-oneplus-15t").performClick()
    }

    @Test
    fun captureSheetCancelKeepsOriginalAndConfirmUpdatesTheSummary() {
        var state by mutableStateOf(EditorState(loading = false, meta = PhotoMeta(iso = 100, fNumber = 8.0)))
        composeRule.setContent {
            PhotoEditorTheme {
                EditorContent(
                    state = state, onBack = {}, onOpenInfo = {}, onOptionsChange = {},
                    onDeviceNameChange = {}, onRegionSelect = {}, onSave = {}, onSaveResultShown = {},
                    onDeviceNameModeChange = {}, onDeviceModelSelect = {},
                    onCaptureParametersChange = { state = state.copy(parameters = it) },
                )
            }
        }
        composeRule.onNodeWithTag("capture-edit").performScrollTo().performClick()
        composeRule.onNodeWithTag("capture-iso").performScrollTo().performTextReplacement("800")
        composeRule.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        assertEquals(100, state.captureParameters.iso)
        composeRule.onNodeWithTag("capture-edit").performScrollTo().performClick()
        composeRule.onNodeWithTag("capture-iso").performScrollTo().performTextReplacement("800")
        snapshot("capture-sheet", "capture-editor")
        composeRule.onNodeWithTag("capture-confirm").assertIsDisplayed().performClick()
        assertEquals(CaptureParameters(iso = 800, fNumber = 8.0), state.captureParameters)
        composeRule.onNodeWithText("f/8  ISO 800").performScrollTo().assertIsDisplayed()
        snapshot("capture-summary", "capture-summary")
    }

    @Test
    fun switchingTemplateChangesOptionsAndSaveCallsBack() {
        var state by mutableStateOf(
            EditorState(
                loading = false,
                meta = PhotoMeta(gps = GeoPoint(39.9, 116.4, null)),
                deviceName = DeviceNameState(manualName = "Nikon Z5II"),
            ),
        )
        var saved = false
        composeRule.setContent {
            PhotoEditorTheme {
                EditorContent(
                    state = state,
                    onBack = {},
                    onOpenInfo = {},
                    onOptionsChange = { change -> state = state.copy(options = change(state.options)) },
                    onDeviceNameChange = { state = state.copy(deviceName = state.deviceName.edit(it)) },
                    onRegionSelect = {},
                    onSave = { saved = true },
                    onSaveResultShown = {},
                    onDeviceNameModeChange = { state = state.copy(deviceName = state.deviceName.useMode(it)) },
                    onDeviceModelSelect = { state = state.copy(deviceName = state.deviceName.select(it)) },
                )
            }
        }

        // 参数边框：有边框颜色和机型名称（选项区能滚动，测试屏幕小，先滚到那里）
        composeRule.onNodeWithText(context.getString(R.string.option_frame_color)).assertIsDisplayed()
        composeRule.onNodeWithText("Nikon Z5II").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.template_overlay)).performScrollTo().performClick()
        assertEquals(TemplateKind.OVERLAY, state.options.template)
        // 信息叠加：有位置选择；照片带 GPS，所以有"保留 GPS"开关
        composeRule.onNodeWithText(context.getString(R.string.option_backdrop)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.option_keep_gps)).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.save)).performClick()
        assertTrue(saved)
    }

    @Test
    fun deviceSelectionIsExclusiveAndBothModesKeepTheirContents() {
        val state by showDeviceEditor(DeviceNameState(manualName = "OnePlus 15T"))

        composeRule.onNodeWithTag(DEVICE_NAME_INPUT_TAG).performScrollTo().performTextReplacement("我的相机")
        snapshot("manual", DEVICE_NAME_EDITOR_TAG)
        composeRule.onNodeWithTag("deviceNameMode-PICKER").performScrollTo().performClick()
        // 即使已经选好型号，关闭弹层也不覆盖手填内容或切换生效模式。
        choosePhoneDraft()
        snapshot("phone-picker", DEVICE_PICKER_TAG)
        composeRule.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        composeRule.onNodeWithTag(DEVICE_NAME_INPUT_TAG).performScrollTo().assertIsDisplayed()
        assertEquals("我的相机", state.deviceName.displayName)
        assertEquals(DeviceNameMode.MANUAL, state.deviceName.mode)

        // 相机分支选中后，编辑框消失，名称和品牌一起进入水印内容。
        composeRule.onNodeWithTag("deviceNameMode-PICKER").performScrollTo().performClick()
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).assertIsNotEnabled()
        snapshot("unselected", DEVICE_PICKER_TAG)
        composeRule.onNodeWithTag(DEVICE_BRAND_FIELD_TAG).performScrollTo().performClick()
        snapshot("camera-brand-menu", "$DEVICE_BRAND_FIELD_TAG-menu")
        composeRule.onNodeWithTag("deviceBrand-NIKON").performClick()
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).performScrollTo().performClick()
        snapshot("camera-model-menu", "$DEVICE_MODEL_FIELD_TAG-menu")
        composeRule.onNodeWithTag("deviceModel-nikon-z5-ii").performClick()
        snapshot("camera-picker", DEVICE_PICKER_TAG)
        assertEquals(DeviceNameMode.MANUAL, state.deviceName.mode)
        assertEquals("我的相机", state.deviceName.displayName)
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).performClick()
        composeRule.onNodeWithTag(DEVICE_NAME_INPUT_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(DEVICE_NAME_SELECTION_TAG).performScrollTo().assertIsDisplayed()
        assertEquals("Nikon Z5 II", state.deviceName.displayName)
        assertEquals("Nikon", state.deviceName.brandOverride)
        snapshot("selected-nikon", DEVICE_NAME_EDITOR_TAG)

        composeRule.onNodeWithTag("deviceNameMode-MANUAL").performScrollTo().performClick()
        composeRule.onNodeWithTag(DEVICE_NAME_INPUT_TAG).performScrollTo().assertIsDisplayed()
        assertEquals("我的相机", state.deviceName.displayName)
        assertEquals(null, state.deviceName.brandOverride)
        composeRule.onNodeWithTag("deviceNameMode-PICKER").performScrollTo().performClick()
        composeRule.onNodeWithTag(DEVICE_PICKER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag("deviceNameMode-PICKER").assertIsSelected()
        assertEquals("Nikon Z5 II", state.deviceName.displayName)

        // 更换时走手机分支，选择相机时的型号不会混进来。
        composeRule.onNodeWithText(context.getString(R.string.device_change)).performScrollTo().performClick()
        composeRule.onNodeWithTag(DEVICE_BRAND_FIELD_TAG).assertTextEquals("尼康 Nikon")
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).assertTextEquals("Z5 II")
        choosePhoneDraft()
        assertEquals("Nikon Z5 II", state.deviceName.displayName)
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).performClick()
        assertEquals(DeviceCatalog.models.last(), state.deviceName.selectedModel)
        assertEquals("OnePlus 15T", state.deviceName.displayName)
        assertEquals("OnePlus", state.deviceName.brandOverride)
        composeRule.onNodeWithTag("deviceNameMode-PICKER").assertIsSelected()
        composeRule.onNodeWithTag("deviceNameMode-MANUAL").assertIsNotSelected()
        snapshot("selected-oneplus", DEVICE_NAME_EDITOR_TAG)
    }

    @Test
    fun systemBackDiscardsDraftAndReopeningRestoresLastConfirmedModel() {
        val original = DeviceCatalog.models.first()
        val state by showDeviceEditor(DeviceNameState(manualName = "我的相机").select(original))
        composeRule.onNodeWithText(context.getString(R.string.device_change)).performScrollTo().performClick()
        choosePhoneDraft()
        // 等下拉菜单完全收起，再把系统返回操作交给机型弹层。
        composeRule.onNodeWithTag("deviceModel-oneplus-15t").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        pressBack()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DEVICE_PICKER_TAG).assertDoesNotExist()
        assertEquals(original, state.deviceName.selectedModel)
        assertEquals("Nikon Z5 II", state.deviceName.displayName)

        composeRule.onNodeWithText(context.getString(R.string.device_change)).performScrollTo().performClick()
        composeRule.onNodeWithTag(DEVICE_BRAND_FIELD_TAG).assertTextEquals("尼康 Nikon")
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).assertTextEquals("Z5 II")
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).assertIsEnabled()
    }

    @Test
    fun gpsNoteOnlyWhenLookupFindsNothing() {
        val index = TestRegions.index
        val gps = PhotoMeta(gps = GeoPoint(40.0886, 94.6705, null))
        var state by mutableStateOf(EditorState(loading = false, meta = gps, regionIndex = index))
        composeRule.setContent {
            PhotoEditorTheme {
                EditorContent(state, {}, {}, {}, {}, {}, {}, {}, onDeviceNameModeChange = {}, onDeviceModelSelect = {})
            }
        }
        val notFound = context.getString(R.string.region_gps_not_found)
        composeRule.onNodeWithText(notFound).performScrollTo().assertIsDisplayed()

        // 按 GPS 查到了、但被手动清除：只显示"未选择"，不说查不到
        state = state.copy(gpsRegion = index.find(620982))
        composeRule.onNodeWithText(context.getString(R.string.region_none)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(notFound).assertDoesNotExist()
    }

    @Test
    fun pickRegionFromSheet() {
        val index = TestRegions.index
        var state by mutableStateOf(EditorState(loading = false, meta = PhotoMeta(), regionIndex = index))
        composeRule.setContent {
            PhotoEditorTheme {
                EditorContent(
                    state = state,
                    onBack = {},
                    onOpenInfo = {},
                    onOptionsChange = { change -> state = state.copy(options = change(state.options)) },
                    onDeviceNameChange = {},
                    onRegionSelect = { region: Region? ->
                        state = state.copy(region = region, regionSource = region?.let { RegionSource.MANUAL })
                    },
                    onSave = {},
                    onSaveResultShown = {},
                    onDeviceNameModeChange = {},
                    onDeviceModelSelect = {},
                )
            }
        }

        // 照片没有 GPS，也没有上一张可以沿用：显示"未选择"
        composeRule.onNodeWithText(context.getString(R.string.region_none)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.region_pick)).performScrollTo().performClick()
        composeRule.onNodeWithTag(REGION_SEARCH_TAG).performTextInput("敦煌")
        composeRule.onNodeWithText("敦煌市").performClick()
        composeRule.waitForIdle()

        assertEquals(620982, state.region?.code)
        composeRule.onNodeWithText("甘肃省敦煌市").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.region_source_manual)).assertIsDisplayed()
        // 选了地区才有"写法"，换成省市区
        composeRule.onNodeWithText(context.getString(R.string.region_style_full)).performScrollTo().performClick()
        composeRule.onNodeWithText("甘肃省酒泉市敦煌市").assertIsDisplayed()
    }
}
