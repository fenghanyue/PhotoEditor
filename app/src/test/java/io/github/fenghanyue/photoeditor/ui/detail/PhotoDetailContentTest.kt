package io.github.fenghanyue.photoeditor.ui.detail

import android.util.Size
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import io.github.fenghanyue.photoeditor.meta.LocationStatus
import io.github.fenghanyue.photoeditor.meta.MetaSource
import io.github.fenghanyue.photoeditor.meta.PhotoMeta
import io.github.fenghanyue.photoeditor.meta.PhotoReadResult
import io.github.fenghanyue.photoeditor.meta.Sourced
import io.github.fenghanyue.photoeditor.ui.theme.PhotoEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class PhotoDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsFormattedCameraInfo() {
        val meta = PhotoMeta(
            dateTime = Sourced(LocalDateTime.of(2025, 5, 20, 8, 30, 15), MetaSource.EXIF),
            make = "NIKON CORPORATION",
            model = "NIKON Z5_2",
            lensModel = "NIKKOR Z 24-200mm f/4-6.3 VR",
            focalLengthMm = 52.0,
            focalLength35mm = 52,
            fNumber = 5.6,
            exposureTimeSec = 1.0 / 60,
            iso = 1400,
            exposureBiasEv = 1.0 / 3,
            flashFired = false,
            rotationDegrees = 90,
        )
        val state = PhotoDetailState(
            loading = false,
            result = PhotoReadResult(meta, LocationStatus.ABSENT),
            storedSize = Size(6048, 4032),
        )
        composeRule.setContent {
            PhotoEditorTheme { PhotoDetailContent(state = state, onBack = {}, onRequestLocation = {}) }
        }

        val list = composeRule.onNode(hasScrollToNodeAction())
        listOf(
            "2025-05-20 08:30:15 星期二",
            "Nikon Z5II",
            "NIKKOR Z 24-200mm f/4-6.3 VR",
            "52mm",
            "f/5.6",
            "1/60s",
            "ISO 1400",
            "+0.3EV",
            "照片里没有位置信息",
            "6048 × 4032",
            "带方向标记：显示时顺时针转 90°",
        ).forEach { text ->
            list.performScrollToNode(hasText(text))
            composeRule.onNodeWithText(text).assertIsDisplayed()
        }
    }
}
