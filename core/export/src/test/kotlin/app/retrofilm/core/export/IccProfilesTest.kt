package app.retrofilm.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IccProfilesTest {
    @Test
    fun profilesAreWellFormed() {
        for ((icc, name) in listOf(IccProfiles.srgb() to "sRGB", IccProfiles.displayP3() to "Display P3")) {
            assertEquals(icc.size, java.nio.ByteBuffer.wrap(icc).getInt(0))
            assertEquals("acsp", String(icc, 36, 4, Charsets.US_ASCII))
            assertTrue(IccProfiles.description(icc)!!.startsWith(name))
            val cs = java.awt.color.ICC_Profile.getInstance(icc)
            assertEquals(java.awt.color.ColorSpace.TYPE_RGB, cs.colorSpaceType)
        }
    }
}
