package io.github.fenghanyue.photoeditor.ui.editor

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.fenghanyue.photoeditor.R
import io.github.fenghanyue.photoeditor.meta.CaptureParameters
import io.github.fenghanyue.photoeditor.meta.PhotoMeta
import io.github.fenghanyue.photoeditor.ui.theme.PhotoEditorTheme
import org.junit.Assert.*
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
class CaptureParameterEditorTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val meta = PhotoMeta(
        lensModel = "NIKKOR Z 24-200mm f/4-6.3 VR", focalLengthMm = 200.0,
        focalLength35mm = 200, fNumber = 8.0, iso = 100,
        exposureTimeSec = 1.0 / 250, exposureBiasEv = -1.0 / 3,
    )
    private var applied: CaptureParameters? = null
    private var dismissed = false

    private fun show(source: PhotoMeta = meta, current: CaptureParameters = CaptureParameters.from(source)) {
        compose.setContent {
            PhotoEditorTheme {
                Surface(Modifier.fillMaxSize()) {
                    CaptureParameterEditorContent(source, current, { applied = it }, { dismissed = true }, Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun snapshot(name: String, tag: String = "capture-editor") {
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val directory = File("build/capture-parameter-samples").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun selectionsRemainDraftUntilConfirmAndUseExactThirds() {
        show()
        snapshot("editor")
        compose.onNodeWithTag("capture-ev").performScrollTo().performClick()
        compose.onNodeWithTag("capture-ev-option-${1.0 / 3}").performScrollTo().performClick()
        compose.onNodeWithTag("capture-aperture").performScrollTo().performClick()
        compose.onNodeWithTag("capture-aperture-option-11.0").performScrollTo().performClick()
        compose.onNodeWithTag("capture-iso").performScrollTo().performTextReplacement("200")
        assertNull(applied)
        compose.onNodeWithTag("capture-confirm").assertIsDisplayed().performClick()
        assertEquals(CaptureParameters.from(meta).copy(exposureBiasEv = 1.0 / 3, fNumber = 11.0, iso = 200), applied)
    }

    @Test
    fun closeDiscardsTheDraft() {
        show()
        compose.onNodeWithTag("capture-iso").performScrollTo().performTextReplacement("800")
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        assertTrue(dismissed)
        assertNull(applied)
    }

    @Test
    fun restoreWaitsForConfirmAndKeepsOriginalShutterPrecision() {
        val source = meta.copy(exposureTimeSec = 1.0 / 124.95)
        show(source, CaptureParameters.from(source).copy(iso = 800, exposureTimeSec = 1.0 / 125))
        compose.onNodeWithTag("capture-restore").performClick()
        assertNull(applied)
        compose.onNodeWithTag("capture-confirm").performClick()
        assertEquals(CaptureParameters.from(source), applied)
    }

    @Test
    fun missingScreenshotValuesCanBeFilledAndRestoredToMissing() {
        show(PhotoMeta())
        snapshot("missing")
        compose.onNodeWithTag("capture-iso").performScrollTo().performTextReplacement("100")
        compose.onNodeWithTag("capture-ev").performScrollTo().performClick()
        compose.onNodeWithTag("capture-ev-option-0.0").performScrollTo().performClick()
        compose.onNodeWithTag("capture-restore").performClick()
        compose.onNodeWithTag("capture-confirm").performClick()
        assertEquals(CaptureParameters(), applied)
    }

    @Test
    fun originalHalfEvAndPhoneApertureAreNotRoundedToThePresets() {
        val source = PhotoMeta(exposureBiasEv = 0.5, fNumber = 1.7)
        show(source)
        compose.onNodeWithTag("capture-confirm").performClick()
        assertEquals(CaptureParameters.from(source), applied)
    }

    @Test
    fun invalidInputDisablesConfirmUntilFixed() {
        show()
        compose.onNodeWithTag("capture-shutter").performScrollTo().performTextReplacement("1/0")
        compose.onNodeWithTag("capture-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("capture-shutter").performTextReplacement("1/500")
        compose.onNodeWithTag("capture-confirm").assertIsEnabled().performClick()
        assertEquals(1.0 / 500, requireNotNull(applied?.exposureTimeSec), 1e-9)
    }

    @Test
    @Config(qualifiers = "w360dp-h600dp")
    fun smallScreenKeepsConfirmVisibleWhileFieldsCanScroll() {
        show()
        compose.onNodeWithTag("capture-shutter").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("capture-confirm").assertIsDisplayed()
        compose.onNodeWithTag("capture-ev").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("capture-confirm").assertIsDisplayed()
    }
}
