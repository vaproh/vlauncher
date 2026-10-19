package app.vlauncher.helper

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.vlauncher.data.Constants
import app.vlauncher.data.Prefs
import kotlinx.coroutines.coroutineScope

class WallpaperWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    private val prefs = Prefs(applicationContext)

    override suspend fun doWork(): Result = coroutineScope {
        val success =
            if (prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && isOlauncherDefault(applicationContext).not())
                true
            else if (prefs.dailyWallpaper) {
                val wallType = checkWallpaperType()
                val (wallpaperUrl, nextCursor) = getTodaysWallpaper(
                    wallType,
                    prefs.firstOpenTime,
                    prefs.wallpaperFrequency,
                    prefs.wallpaperCursor,
                )
                if (prefs.dailyWallpaperUrl == wallpaperUrl) {
                    // Same image; still move on so a repeated entry cannot stall the cycle.
                    prefs.wallpaperCursor = nextCursor
                    true
                } else {
                    val applied = setWallpaper(applicationContext, wallpaperUrl)
                    if (applied) {
                        prefs.wallpaperCursor = nextCursor
                        prefs.dailyWallpaperUrl = wallpaperUrl
                    }
                    applied
                }
            } else
                true

        if (success)
            Result.success()
        else
            Result.retry()
    }

    private fun checkWallpaperType(): String {
        return when (prefs.appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> Constants.WALL_TYPE_DARK
            AppCompatDelegate.MODE_NIGHT_NO -> Constants.WALL_TYPE_LIGHT
            else -> if (applicationContext.isDarkThemeOn())
                Constants.WALL_TYPE_DARK
            else
                Constants.WALL_TYPE_LIGHT
        }
    }
}
