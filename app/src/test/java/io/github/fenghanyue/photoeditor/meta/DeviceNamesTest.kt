package io.github.fenghanyue.photoeditor.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceNamesTest {

    @Test
    fun nikon() {
        assertEquals("Nikon Z5II", DeviceNames.displayName("NIKON CORPORATION", "NIKON Z5_2"))
        assertEquals("Nikon Z 6II", DeviceNames.displayName("NIKON CORPORATION", "NIKON Z 6_2"))
        assertEquals("Nikon Z6III", DeviceNames.displayName("NIKON CORPORATION", "NIKON Z6_3"))
        assertEquals("Nikon D850", DeviceNames.displayName("NIKON CORPORATION", "NIKON D850"))
    }

    @Test
    fun canon() {
        assertEquals("Canon EOS R6 Mark II", DeviceNames.displayName("Canon", "Canon EOS R6m2"))
        assertEquals("Canon EOS R5", DeviceNames.displayName("Canon", "Canon EOS R5"))
    }

    @Test
    fun sony() {
        assertEquals("Sony α7 IV", DeviceNames.displayName("SONY", "ILCE-7M4"))
        assertEquals("Sony α7R V", DeviceNames.displayName("SONY", "ILCE-7RM5"))
        assertEquals("Sony α7C II", DeviceNames.displayName("SONY", "ILCE-7CM2"))
        assertEquals("Sony α7C", DeviceNames.displayName("SONY", "ILCE-7C"))
        assertEquals("Sony α6700", DeviceNames.displayName("SONY", "ILCE-6700"))
        assertEquals("Sony ZV-E10", DeviceNames.displayName("SONY", "ZV-E10"))
    }

    @Test
    fun othersKeepTheirModel() {
        assertEquals("Fujifilm X-T5", DeviceNames.displayName("FUJIFILM", "X-T5"))
        assertEquals("Xiaomi 2211133C", DeviceNames.displayName("Xiaomi", "2211133C"))
        assertEquals("Apple iPhone 15 Pro", DeviceNames.displayName("Apple", "iPhone 15 Pro"))
    }

    @Test
    fun missingParts() {
        assertNull(DeviceNames.displayName(null, null))
        assertEquals("Nikon", DeviceNames.displayName("NIKON CORPORATION", null))
        assertEquals("NIKON Z5_2", DeviceNames.displayName(null, "NIKON Z5_2"))
        assertEquals("Acme Cam", DeviceNames.displayName("Acme", "Cam"))
    }
}
