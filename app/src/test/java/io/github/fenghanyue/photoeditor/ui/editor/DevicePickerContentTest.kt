package io.github.fenghanyue.photoeditor.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.fenghanyue.photoeditor.meta.DeviceModel
import io.github.fenghanyue.photoeditor.meta.DeviceCatalog
import io.github.fenghanyue.photoeditor.ui.theme.PhotoEditorTheme
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp")
class DevicePickerContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun singleBrandOpensItsDropdownEvenWhenItIsTheOnlyOption() {
        var applied: DeviceModel? = null
        composeRule.setContent {
            PhotoEditorTheme {
                DevicePickerContent(current = null, onSelect = { applied = it }, onDismiss = {})
            }
        }

        // 未点开时只显示选择框，型号和确定按钮都还不可用。
        composeRule.onNodeWithTag("deviceBrand-NIKON").assertDoesNotExist()
        composeRule.onNodeWithTag("deviceModelField").assertIsNotEnabled()
        composeRule.onNodeWithTag("deviceConfirm").assertIsNotEnabled()

        composeRule.onNodeWithTag("deviceBrandField").performClick()
        composeRule.onNodeWithTag("deviceBrand-NIKON").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("deviceBrand-NIKON").assertDoesNotExist()
        composeRule.onNodeWithTag("deviceBrandField").assertTextEquals("尼康 Nikon")
        assertNull(applied)
    }

    @Test
    fun selectingTheOnlyModelWaitsForConfirmBeforeApplying() {
        var applied: DeviceModel? = null
        var applyCalls = 0
        composeRule.setContent {
            PhotoEditorTheme {
                DevicePickerContent(current = null, onSelect = { applied = it; applyCalls++ }, onDismiss = {})
            }
        }
        composeRule.onNodeWithTag(DEVICE_BRAND_FIELD_TAG).performClick()
        composeRule.onNodeWithTag("deviceBrand-NIKON").performClick()
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).assertIsEnabled().performClick()
        composeRule.onNodeWithTag("deviceModel-nikon-z5-ii").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("deviceModel-nikon-z5-ii").assertDoesNotExist()
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).assertTextEquals("Z5 II")
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).assertIsEnabled()
        assertNull(applied)

        // 已选中的唯一品牌仍可展开；再次选择同一品牌保留匹配的型号。
        composeRule.onNodeWithTag(DEVICE_BRAND_FIELD_TAG).performClick()
        composeRule.onNodeWithTag("deviceBrand-NIKON").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).assertTextEquals("Z5 II")
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).performClick()
        assertEquals(DeviceCatalog.models.first(), applied)
        assertEquals(1, applyCalls)
    }

    @Test
    fun changingDeviceTypeClearsIncompatibleBrandAndModel() {
        var applied: DeviceModel? = null
        composeRule.setContent {
            PhotoEditorTheme {
                DevicePickerContent(current = DeviceCatalog.models.first(), onSelect = { applied = it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).assertTextEquals("Z5 II")
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).assertIsEnabled()
        composeRule.onNodeWithTag("deviceType-PHONE").performClick()
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).assertIsNotEnabled()

        composeRule.onNodeWithTag(DEVICE_BRAND_FIELD_TAG).performClick()
        composeRule.onNodeWithTag("deviceBrand-NIKON").assertDoesNotExist()
        composeRule.onNodeWithTag("deviceBrand-ONEPLUS").performClick()
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).performClick()
        composeRule.onNodeWithTag("deviceModel-nikon-z5-ii").assertDoesNotExist()
        composeRule.onNodeWithTag("deviceModel-oneplus-15t").performClick()
        assertNull(applied)

        // 点当前类型不会清掉刚选好的、仍然匹配的机型。
        composeRule.onNodeWithTag("deviceType-PHONE").performClick()
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).assertTextEquals("15T")
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).performClick()
        assertEquals(DeviceCatalog.models.last(), applied)
    }

    @Test
    @Config(qualifiers = "w320dp-h420dp")
    fun confirmStaysVisibleWhenTheChoicesNeedScrolling() {
        composeRule.setContent {
            PhotoEditorTheme {
                DevicePickerContent(current = DeviceCatalog.models.first(), onSelect = {}, onDismiss = {})
            }
        }
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DEVICE_MODEL_FIELD_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(DEVICE_CONFIRM_TAG).assertIsDisplayed().assertIsEnabled()
    }
}
