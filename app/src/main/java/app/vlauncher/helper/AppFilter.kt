package app.vlauncher.helper

/**
 * Package/activity to drawable-name map built from an icon pack's appfilter.xml.
 * Free of Android types so key parsing and lookup precedence can be unit tested.
 */
class AppFilter {

    private val components = HashMap<String, String>()
    private val packages = HashMap<String, String>()

    fun add(component: String, drawable: String) {
        val key = componentKey(component) ?: return
        components[key] = drawable
        packages.putIfAbsent(key.substringBefore('/'), drawable)
    }

    /** Exact activity match first, then any icon the pack maps for the package. */
    fun drawableFor(packageName: String, className: String?): String? {
        if (className != null) components["$packageName/$className"]?.let { return it }
        return packages[packageName]
    }

    companion object {

        /** "ComponentInfo{pkg/.Cls}" or "pkg/Cls" to "pkg/fully.qualified.Cls". */
        fun componentKey(raw: String): String? {
            val value = raw.trim().removePrefix("ComponentInfo{").removeSuffix("}")
            val slash = value.indexOf('/')
            if (slash <= 0 || slash >= value.length - 1) return null
            val pkg = value.substring(0, slash)
            val cls = value.substring(slash + 1)
            return "$pkg/" + when {
                cls.startsWith(".") -> pkg + cls
                cls.startsWith("$pkg.") -> cls
                else -> "$pkg.$cls"
            }
        }
    }
}
