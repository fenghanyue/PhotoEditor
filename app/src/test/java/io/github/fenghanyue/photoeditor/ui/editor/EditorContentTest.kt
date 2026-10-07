package io.github.fenghanyue.photoeditor.ui.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import io.github.fenghanyue.photoeditor.R
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
}
