package io.github.fenghanyue.photoeditor.meta

import org.junit.Assert.assertEquals
import org.junit.Test

class ParamFormatterTest {

    @Test
    fun shutter() {
        assertEquals("1/60s", ParamFormatter.shutter(1.0 / 60))
        assertEquals("1/8000s", ParamFormatter.shutter(1.0 / 8000))
        assertEquals("1/3s", ParamFormatter.shutter(1.0 / 3))
        assertEquals("1/2.5s", ParamFormatter.shutter(0.4))
        assertEquals("1s", ParamFormatter.shutter(1.0))
        assertEquals("2.5s", ParamFormatter.shutter(2.5))
        assertEquals("30s", ParamFormatter.shutter(30.0))
    }

    @Test
    fun aperture() {
        assertEquals("f/5.6", ParamFormatter.aperture(5.6))
        assertEquals("f/8", ParamFormatter.aperture(8.0))
        assertEquals("f/11", ParamFormatter.aperture(11.0))
        assertEquals("f/1.8", ParamFormatter.aperture(1.75))
        assertEquals("f/1.9", ParamFormatter.aperture(1.88))
    }

    @Test
    fun focalLength() {
        assertEquals("52mm", ParamFormatter.focalLength(52.0))
        assertEquals("5.6mm", ParamFormatter.focalLength(5.56))
    }

    @Test
    fun iso() {
        assertEquals("ISO 1400", ParamFormatter.iso(1400))
    }

    @Test
    fun exposureBias() {
        assertEquals("+0.3EV", ParamFormatter.exposureBias(1.0 / 3))
        assertEquals("-0.7EV", ParamFormatter.exposureBias(-2.0 / 3))
        assertEquals("-1.3EV", ParamFormatter.exposureBias(-4.0 / 3))
        assertEquals("+1EV", ParamFormatter.exposureBias(1.0))
        assertEquals("0EV", ParamFormatter.exposureBias(0.0))
    }

    @Test
    fun coordinates() {
        assertEquals("39.9087°N, 116.3975°E", ParamFormatter.coordinatesDecimal(39.9087, 116.3975))
        assertEquals("39°54'31\"N 116°23'51\"E", ParamFormatter.coordinatesDms(39.9087, 116.3975))
        assertEquals("33.8688°S, 151.2093°E", ParamFormatter.coordinatesDecimal(-33.8688, 151.2093))
        assertEquals("40°42'46\"N 74°00'22\"W", ParamFormatter.coordinatesDms(40.7128, -74.0060))
    }

    @Test
    fun altitude() {
        assertEquals("44m", ParamFormatter.altitude(44.4))
    }
}
