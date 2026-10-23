package app.vlauncher.helper

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.annotation.FontRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.content.res.ResourcesCompat
import app.vlauncher.R

/** Applies a full [Typeface] to a span of text; works on all API levels. */
class CustomTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateMeasureState(paint: TextPaint) = apply(paint)
    override fun updateDrawState(paint: TextPaint) = apply(paint)

    private fun apply(paint: Paint) {
        paint.typeface = typeface
    }
}

object Fonts {
    const val DEFAULT = "default"
    const val MONOSPACE = "monospace"
    const val HACK = "hack"
    const val JETBRAINS_MONO = "jetbrains_mono"
    const val IOSEVKA = "iosevka"
    const val INTER = "inter"

    data class Option(
        val id: String,
        @StringRes val label: Int,
        @StyleRes val overlay: Int,
        @FontRes val preview: Int?,
    )

    val options = listOf(
        Option(DEFAULT, R.string.font_default, R.style.FontOverlayDefault, null),
        Option(MONOSPACE, R.string.font_monospace, R.style.FontOverlayMonospace, null),
        Option(HACK, R.string.font_hack, R.style.FontOverlayHack, R.font.hack),
        Option(JETBRAINS_MONO, R.string.font_jetbrains_mono, R.style.FontOverlayJetBrainsMono, R.font.jetbrains_mono),
        Option(IOSEVKA, R.string.font_iosevka, R.style.FontOverlayIosevka, R.font.iosevka),
        Option(INTER, R.string.font_inter, R.style.FontOverlayInter, R.font.inter),
    )

    fun overlayFor(id: String?): Int =
        options.firstOrNull { it.id == id }?.overlay ?: R.style.FontOverlayDefault

    fun labelFor(context: Context, id: String?): String =
        context.getString(options.firstOrNull { it.id == id }?.label ?: R.string.font_default)

    /** Typeface used to preview the option in the picker; null falls back to plain text. */
    fun previewTypeface(context: Context, id: String): Typeface? {
        val preview = options.firstOrNull { it.id == id }?.preview
        if (preview != null) {
            try {
                return ResourcesCompat.getFont(context, preview)
            } catch (_: Exception) {
                return null
            }
        }
        return when (id) {
            MONOSPACE -> Typeface.MONOSPACE
            else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
    }

    /** Preloads the preview typefaces off the main thread so the picker opens without a hitch. */
    fun warmUp(context: Context) {
        val app = context.applicationContext
        previewCache = options.associate { it.id to previewTypeface(app, it.id) }
    }

    /** Warmed preview typeface, or null when not yet loaded (or unloadable): use a plain label. */
    fun previewCached(id: String): Typeface? = previewCache[id]

    @Volatile
    private var previewCache: Map<String, Typeface?> = emptyMap()
}
