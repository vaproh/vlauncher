package app.vlauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppFilterTest {

    @Test
    fun `component key expands relative and bare class names`() {
        assertEquals("com.foo/com.foo.MainActivity", AppFilter.componentKey("ComponentInfo{com.foo/.MainActivity}"))
        assertEquals("com.foo/com.foo.MainActivity", AppFilter.componentKey("ComponentInfo{com.foo/com.foo.MainActivity}"))
        assertEquals("com.foo/com.foo.sub.Main", AppFilter.componentKey("com.foo/sub.Main"))
        assertEquals("com.foo/com.foo.Main", AppFilter.componentKey("  com.foo/Main  "))
    }

    @Test
    fun `component key rejects values without both parts`() {
        assertNull(AppFilter.componentKey("com.foo"))
        assertNull(AppFilter.componentKey(""))
        assertNull(AppFilter.componentKey("ComponentInfo{/}"))
        assertNull(AppFilter.componentKey("ComponentInfo{com.foo/}"))
    }

    @Test
    fun `lookup prefers the exact activity then falls back to the package`() {
        val filter = AppFilter()
        filter.add("ComponentInfo{com.foo/.MainActivity}", "foo_main")
        filter.add("ComponentInfo{com.bar/BarActivity}", "bar")

        assertEquals("foo_main", filter.drawableFor("com.foo", "com.foo.MainActivity"))
        assertEquals("bar", filter.drawableFor("com.bar", "com.bar.OtherActivity"))
        assertEquals("bar", filter.drawableFor("com.bar", null))
        assertNull(filter.drawableFor("com.baz", null))
    }
}
