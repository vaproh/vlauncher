package app.vlauncher.helper

/** Pure selection helpers for the daily wallpaper JSON, kept testable off-device. */
object WallpaperSelection {

    /** First ten days after setup use their own slot, later days use the month and day. */
    fun keyFor(daysSinceFirstOpen: Int, month: Int, day: Int): String =
        if (daysSinceFirstOpen < 10) "0_$daysSinceFirstOpen" else "${month}_$day"

    /** Cycles the curated list; returns the URL and the cursor to use on the next run. */
    fun cursorUrl(urls: List<String>, cursor: Int): Pair<String, Int>? {
        if (urls.isEmpty()) return null
        val index = ((cursor % urls.size) + urls.size) % urls.size
        return urls[index] to cursor + 1
    }
}
