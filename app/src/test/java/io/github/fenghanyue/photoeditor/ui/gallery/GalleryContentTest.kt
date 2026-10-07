package io.github.fenghanyue.photoeditor.ui.gallery

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.fenghanyue.photoeditor.R
import io.github.fenghanyue.photoeditor.media.MediaAccess
import io.github.fenghanyue.photoeditor.ui.theme.PhotoEditorTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GalleryContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun show(state: GalleryState, deniedOnce: Boolean = false, onRequestAccess: () -> Unit = {}) {
        composeRule.setContent {
            PhotoEditorTheme {
                GalleryContent(
                    state = state,
                    versionName = "test",
                    deniedOnce = deniedOnce,
                    onRequestAccess = onRequestAccess,
                    onOpenSettings = {},
                    onSelectBucket = {},
                    onOpenPhoto = {},
                )
            }
        }
    }

    @Test
    fun noAccessAsksForPermission() {
        var requested = false
        show(GalleryState(access = MediaAccess.NONE), onRequestAccess = { requested = true })

        composeRule.onNodeWithText(context.getString(R.string.permission_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.permission_grant)).performClick()
        assertTrue(requested)
    }

    @Test
    fun deniedOnceOffersSystemSettings() {
        show(GalleryState(access = MediaAccess.NONE), deniedOnce = true)

        composeRule.onNodeWithText(context.getString(R.string.permission_open_settings)).assertIsDisplayed()
    }

    @Test
    fun emptyLibraryShowsMessage() {
        show(GalleryState(access = MediaAccess.FULL))

        composeRule.onNodeWithText(context.getString(R.string.gallery_empty)).assertIsDisplayed()
    }

    @Test
    fun partialAccessShowsBanner() {
        show(GalleryState(access = MediaAccess.PARTIAL))

        composeRule.onNodeWithText(context.getString(R.string.partial_access_message)).assertIsDisplayed()
    }
}
