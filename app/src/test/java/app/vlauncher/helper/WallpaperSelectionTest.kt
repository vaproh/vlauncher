package app.vlauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperSelectionTest {

    @Test
    fun `only daily uses the date key`() {
        assertTrue(WallpaperSelection.isDateKeyed(24))
        assertFalse(WallpaperSelection.isDateKeyed(23))
        assertFalse(WallpaperSelection.isDateKeyed(168))
    }

    @Test
    fun `first ten days use their own key then month and day`() {
        assertEquals("0_0", WallpaperSelection.keyFor(0, 10, 19))
        assertEquals("0_9", WallpaperSelection.keyFor(9, 10, 19))
        assertEquals("10_19", WallpaperSelection.keyFor(10, 10, 19))
    }

    @Test
    fun `cursor cycles through the curated list`() {
        val urls = listOf("a", "b", "c")
        assertEquals("a" to 1, WallpaperSelection.cursorUrl(urls, 0))
        assertEquals("b" to 2, WallpaperSelection.cursorUrl(urls, 1))
        assertEquals("c" to 3, WallpaperSelection.cursorUrl(urls, 2))
        assertEquals("a" to 4, WallpaperSelection.cursorUrl(urls, 3))
    }

    @Test
    fun `empty list has no wallpaper`() {
        assertNull(WallpaperSelection.cursorUrl(emptyList(), 0))
    }
}
