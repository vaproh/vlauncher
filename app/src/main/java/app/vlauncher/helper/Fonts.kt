package app.vlauncher.helper

import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import app.vlauncher.R

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
    )

    val options = listOf(
        Option(DEFAULT, R.string.font_default, R.style.FontOverlayDefault),
        Option(MONOSPACE, R.string.font_monospace, R.style.FontOverlayMonospace),
        Option(HACK, R.string.font_hack, R.style.FontOverlayHack),
        Option(JETBRAINS_MONO, R.string.font_jetbrains_mono, R.style.FontOverlayJetBrainsMono),
        Option(IOSEVKA, R.string.font_iosevka, R.style.FontOverlayIosevka),
        Option(INTER, R.string.font_inter, R.style.FontOverlayInter),
    )

    fun overlayFor(id: String?): Int =
        options.firstOrNull { it.id == id }?.overlay ?: R.style.FontOverlayDefault

    fun labelFor(context: Context, id: String?): String =
        context.getString(options.firstOrNull { it.id == id }?.label ?: R.string.font_default)
}
