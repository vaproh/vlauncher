package app.vlauncher.helper

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.util.LruCache
import androidx.core.content.res.ResourcesCompat
import app.vlauncher.R
import app.vlauncher.data.Prefs
import org.xmlpull.v1.XmlPullParser
import kotlin.math.abs

/**
 * Resolves drawer icons from the selected icon pack, then the app's own icon,
 * then a generated monogram so no row is ever blank.
 */
object Icons {

    /** Theme actions/categories declared by the common launcher icon pack formats. */
    private val PACK_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.gau.go.launcherex.theme",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
    )

    /** Apex exposes its theme as a category on ACTION_MAIN rather than an action. */
    private val PACK_CATEGORIES = listOf("com.anddoes.launcher.THEME")

    data class Pack(val packageName: String, val label: String)

    private val cache = object : LruCache<String, Drawable>(128) {}

    @Volatile
    private var filterPack: String? = null

    @Volatile
    private var filter: AppFilter? = null

    fun packs(context: Context): List<Pack> {
        val packageManager = context.packageManager
        val found = LinkedHashMap<String, String>()
        val add: (android.content.pm.ResolveInfo) -> Unit = { activity ->
            val packageName = activity.activityInfo.packageName
            if (packageName !in found) found[packageName] = activity.loadLabel(packageManager).toString()
        }
        PACK_ACTIONS.forEach { action ->
            packageManager.queryIntentActivities(Intent(action), 0).forEach(add)
        }
        PACK_CATEGORIES.forEach { category ->
            packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0).forEach(add)
        }
        return found.map { Pack(it.key, it.value) }.sortedBy { it.label.lowercase() }
    }

    /** Parses the selected pack's appfilter off the main thread after startup. */
    fun warmUp(context: Context) {
        val pack = Prefs(context).iconPack
        if (pack.isEmpty() || filterPack == pack) return
        val appContext = context.applicationContext
        Thread { appFilter(appContext, pack) }.start()
    }

    fun forApp(
        context: Context,
        packageName: String,
        className: String?,
        user: UserHandle,
        label: String,
    ): Drawable {
        val pack = Prefs(context).iconPack
        val key = "$pack|${user.hashCode()}|$packageName|$className|$label"
        cache.get(key)?.let { return it }
        val icon = packIcon(context, pack, packageName, className)
            ?: systemIcon(context, packageName, className, user)
            ?: monogram(context, label, packageName)
        cache.put(key, icon)
        return icon
    }

    /** Wraps a drawable with a fixed intrinsic size, for compound drawables. */
    fun fixedSize(drawable: Drawable, sizePx: Int): Drawable = FixedSizeDrawable(drawable, sizePx)

    private class FixedSizeDrawable(private val inner: Drawable, private val size: Int) : Drawable() {
        override fun draw(canvas: Canvas) {
            inner.setBounds(bounds)
            inner.draw(canvas)
        }

        override fun setAlpha(alpha: Int) {
            inner.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            inner.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Drawable")
        override fun getOpacity(): Int = inner.opacity

        override fun getIntrinsicWidth(): Int = size

        override fun getIntrinsicHeight(): Int = size
    }

    private fun packIcon(context: Context, pack: String, packageName: String, className: String?): Drawable? {
        if (pack.isEmpty()) return null
        val drawableName = appFilter(context, pack)?.drawableFor(packageName, className) ?: return null
        return try {
            val resources = context.packageManager.getResourcesForApplication(pack)
            var id = resources.getIdentifier(drawableName, "drawable", pack)
            if (id == 0) id = resources.getIdentifier(drawableName, "mipmap", pack)
            if (id == 0) null else ResourcesCompat.getDrawable(resources, id, null)
        } catch (e: Exception) {
            null
        }
    }

    private fun systemIcon(context: Context, packageName: String, className: String?, user: UserHandle): Drawable? {
        return try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val activities = launcherApps.getActivityList(packageName, user)
            val match = activities.firstOrNull { className == null || it.componentName.className == className }
                ?: activities.firstOrNull()
            match?.getIcon(0)
        } catch (e: Exception) {
            null
        }
    }

    /** Rounded tile with the first letter, colored from the package name. */
    private fun monogram(context: Context, label: String, seed: String): Drawable {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.HSVToColor(floatArrayOf((abs(seed.hashCode()) % 360).toFloat(), 0.45f, 0.55f))
        val corner = size * 0.28f
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), corner, corner, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size * 0.5f
        paint.typeface = typeface(context)
        val metrics = paint.fontMetrics
        canvas.drawText(
            label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            size / 2f,
            size / 2f - (metrics.ascent + metrics.descent) / 2f,
            paint,
        )
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun typeface(context: Context): Typeface = when (Prefs(context).fontFamily) {
        Fonts.MONOSPACE -> Typeface.MONOSPACE
        Fonts.HACK -> ResourcesCompat.getFont(context, R.font.hack) ?: Typeface.DEFAULT
        Fonts.JETBRAINS_MONO -> ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.DEFAULT
        Fonts.IOSEVKA -> ResourcesCompat.getFont(context, R.font.iosevka) ?: Typeface.DEFAULT
        Fonts.INTER -> ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.DEFAULT
        else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private fun appFilter(context: Context, pack: String): AppFilter? {
        if (filterPack == pack) return filter
        synchronized(this) {
            if (filterPack == pack) return filter
            val parsed = try {
                val resources = context.packageManager.getResourcesForApplication(pack)
                val id = resources.getIdentifier("appfilter", "xml", pack)
                val result = AppFilter()
                if (id != 0) {
                    val parser = resources.getXml(id)
                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG && parser.name == "item") {
                            val component = parser.getAttributeValue(null, "component")
                            val drawable = parser.getAttributeValue(null, "drawable")
                            if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) result.add(component, drawable)
                        }
                        event = parser.next()
                    }
                    parser.close()
                }
                result
            } catch (e: Exception) {
                AppFilter()
            }
            filter = parsed
            filterPack = pack
            return parsed
        }
    }
}
