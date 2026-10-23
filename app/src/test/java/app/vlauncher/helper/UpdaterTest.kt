package app.vlauncher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    @Test
    fun `newer versions are detected`() {
        assertTrue(Updater.isNewer("v1.1.0", "v1.2.0"))
        assertTrue(Updater.isNewer("v1.1.0", "v1.10.0"))
        assertTrue(Updater.isNewer("v1.1", "v1.1.1"))
        assertTrue(Updater.isNewer("1.1.0", "v1.1.1"))
    }

    @Test
    fun `same or older versions are not updates`() {
        assertFalse(Updater.isNewer("v1.1.0", "v1.1.0"))
        assertFalse(Updater.isNewer("v1.2.0", "v1.1.0"))
        assertFalse(Updater.isNewer("v2.0.0", "v1.9.9"))
    }
}
