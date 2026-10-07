package io.github.fenghanyue.photoeditor.ui.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.github.fenghanyue.photoeditor.R
import io.github.fenghanyue.photoeditor.geo.Region
import io.github.fenghanyue.photoeditor.geo.TestRegions
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

@RunWith(RobolectricTestRunner::class)
class EditorContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun switchingTemplateChangesOptionsAndSaveCallsBack() {
        var state by mutableStateOf(
            EditorState(loading = false, meta = PhotoMeta(gps = GeoPoint(39.9, 116.4, null)), deviceName = "Nikon Z5II"),
        )
        var saved = false
        composeRule.setContent {
            PhotoEditorTheme {
                EditorContent(
                    state = state,
                    onBack = {},
                    onOpenInfo = {},
                    onOptionsChange = { change -> state = state.copy(options = change(state.options)) },
                    onDeviceNameChange = { state = state.copy(deviceName = it) },
                    onRegionSelect = {},
                    onSave = { saved = true },
                    onSaveResultShown = {},
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
    fun gpsNoteOnlyWhenLookupFindsNothing() {
        val index = TestRegions.index
        val gps = PhotoMeta(gps = GeoPoint(40.0886, 94.6705, null))
        var state by mutableStateOf(EditorState(loading = false, meta = gps, regionIndex = index))
        composeRule.setContent {
            PhotoEditorTheme {
                EditorContent(state, {}, {}, {}, {}, {}, {}, {})
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
