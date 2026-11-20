package io.github.fenghanyue.photoeditor.ui.editor

import io.github.fenghanyue.photoeditor.meta.CaptureParameterPresets
import io.github.fenghanyue.photoeditor.meta.CaptureParameters
import io.github.fenghanyue.photoeditor.meta.PhotoMeta
import org.junit.Assert.*
import org.junit.Test

class CaptureParameterDraftTest {
    private val original = CaptureParameters(-1.0 / 3, 1400, 52.0, 52, 5.6, 1.0 / 124.95)

    @Test
    fun editingAnotherFieldPreservesUnroundedShutterAndOriginalEv() {
        val result = CaptureParameterDraft.from(original).copy(iso = "100").validate(original)
        assertEquals(original.copy(iso = 100), result.values)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun missingValuesStayMissingAndZeroEvIsAValue() {
        assertEquals(CaptureParameters(), CaptureParameterDraft().validate(CaptureParameters()).values)
        val filled = CaptureParameterDraft(ev = "0", iso = "100", shutter = "1/250s", focal = "50mm", equivalent = "50")
            .validate(CaptureParameters())
        assertEquals(CaptureParameters(0.0, 100, 50.0, 50, null, 1.0 / 250), filled.values)
    }

    @Test
    fun invalidNumbersAndFractionsCannotBeApplied() {
        val valid = CaptureParameterDraft.from(original)
        listOf(
            valid.copy(iso = "0"), valid.copy(iso = "1.5"), valid.copy(iso = "999999999999"),
            valid.copy(focal = "NaN"), valid.copy(focal = "-50"), valid.copy(focal = "Infinity"),
            valid.copy(equivalent = "2.5"), valid.copy(equivalent = "65536"),
            valid.copy(shutter = "1/0"), valid.copy(shutter = "-1/250"), valid.copy(shutter = "0"),
            valid.copy(shutter = "1/2/3"), valid.copy(shutter = "1/Infinity"),
        ).forEach {
            val result = it.validate(original)
            assertNull("不应接受 $it", result.values)
            assertFalse(result.errors.isEmpty())
        }
    }

    @Test
    fun presetsUseThirdsButKeepNonstandardOriginalValues() {
        val thirds = CaptureParameterPresets.exposureBias(null)
        assertTrue(1.0 / 3 in thirds)
        assertFalse(0.3 in thirds)
        assertFalse(0.5 in thirds)
        assertTrue(0.5 in CaptureParameterPresets.exposureBias(0.5))
        assertTrue(1.7 in CaptureParameterPresets.apertures(PhotoMeta(fNumber = 1.7), 5.56, 1.7))
    }

    @Test
    fun knownZoomLensUsesPublishedEndpointRanges() {
        val meta = PhotoMeta(lensModel = "NIKKOR Z 24-200mm f/4-6.3 VR", fNumber = 8.0)
        val wide = CaptureParameterPresets.apertures(meta, 24.0, 8.0)
        assertEquals(4.0, wide.first(), 0.0)
        assertEquals(22.0, wide.last(), 0.0)
        val tele = CaptureParameterPresets.apertures(meta, 200.0, 8.0)
        assertEquals(6.3, tele.first(), 0.0)
        assertEquals(36.0, tele.last(), 0.0)
    }
}
