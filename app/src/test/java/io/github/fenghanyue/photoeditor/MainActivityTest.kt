package io.github.fenghanyue.photoeditor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchWithoutPermissionShowsPromptAndVersion() {
        val activity = composeRule.activity
        composeRule.onNodeWithText(activity.getString(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.version_label, BuildConfig.VERSION_NAME))
            .assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(R.string.permission_title)).assertIsDisplayed()
    }
}
