package app.vlauncher.helper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import app.vlauncher.data.Constants
import app.vlauncher.data.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

data class WeatherReading(val temperature: Int, val label: String, val updatedAt: Long)

data class WeatherCity(val name: String, val latitude: Double, val longitude: Double)

object Weather {
    const val MAX_AGE_MS = 60 * 60 * 1000L

    // https://open-meteo.com/en/docs#weathervariables
    fun labelForCode(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2, 3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
        71, 73, 75, 77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> "Cloudy"
    }

    fun isStale(updatedAt: Long, now: Long = System.currentTimeMillis(), maxAgeMinutes: Int = 60): Boolean =
        updatedAt <= 0L || now - updatedAt > maxAgeMinutes * 60 * 1000L

    fun format(reading: WeatherReading, unit: String = Constants.Weather.UNIT_CELSIUS): String {
        val temperature = if (unit == Constants.Weather.UNIT_FAHRENHEIT)
            (reading.temperature * 9.0 / 5.0 + 32.0).roundToInt()
        else reading.temperature
        return "$temperature° ${reading.label}"
    }

    fun readCached(prefs: Prefs): WeatherReading? {
        if (prefs.weatherLabel.isBlank()) return null
        return WeatherReading(prefs.weatherTemperature, prefs.weatherLabel, prefs.weatherUpdatedAt)
    }

    fun fetch(latitude: Double, longitude: Double): WeatherReading? {
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code&timezone=auto"
            )
            val current = readJson(url)?.optJSONObject("current") ?: return null
            val temperature = current.optDouble("temperature_2m", Double.NaN)
            if (temperature.isNaN()) return null
            WeatherReading(
                temperature.roundToInt(),
                labelForCode(current.optInt("weather_code", -1)),
                System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun searchCity(name: String): WeatherCity? {
        return try {
            val url = URL(
                "https://geocoding-api.open-meteo.com/v1/search?name=" +
                    URLEncoder.encode(name, "UTF-8") + "&count=1&language=en&format=json"
            )
            val result = readJson(url)?.optJSONArray("results")?.optJSONObject(0) ?: return null
            WeatherCity(
                result.getString("name"),
                result.getDouble("latitude"),
                result.getDouble("longitude"),
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readJson(url: URL): JSONObject? {
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}

object WeatherBreezy {
    const val READ_PERMISSION = "org.breezyweather.READ_PROVIDER"

    // Possible Breezy Weather applicationIds: store/F-Droid build, debug and freenet builds.
    private val PACKAGES = listOf(
        "org.breezyweather",
        "org.breezyweather.debug",
        "org.breezyweather.freenet",
    )

    fun installedPackage(context: Context): String? = PACKAGES.firstOrNull { pkg ->
        try {
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasReadPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, READ_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun isActive(context: Context): Boolean =
        installedPackage(context) != null && hasReadPermission(context)

    /**
     * Pulls the current reading from Breezy Weather's data sharing provider.
     * Breezy serves its own configured location and weather source, so no coordinates are needed.
     */
    fun fetch(context: Context): WeatherReading? {
        val pkg = installedPackage(context) ?: return null
        if (!hasReadPermission(context)) return null
        val uri = "content://$pkg.provider.weather/weather?id=CURRENT_POSITION" +
            "&withDaily=false&withHourly=false&withMinutely=false&withAlerts=false&withNormals=false" +
            "&temperatureUnit=c"
        return try {
            context.contentResolver.query(Uri.parse(uri), null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val index = cursor.getColumnIndex("weather")
                val blob = if (index >= 0 && !cursor.isNull(index)) cursor.getBlob(index) else return null
                parse(GZIPInputStream(blob.inputStream()).use { it.readBytes() }.decodeToString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parse(json: String): WeatherReading? {
        val root = JSONObject(json)
        val current = root.optJSONObject("current") ?: return null
        val temperature = current.optJSONObject("temperature")
            ?.optJSONObject("temperature")?.optDouble("value") ?: return null
        if (temperature.isNaN()) return null
        val label = current.optString("weatherText")
        if (label.isBlank()) return null
        val updatedAt = root.optLong("refreshTime", 0L) * 1000L
        return WeatherReading(
            temperature.roundToInt(),
            label,
            if (updatedAt > 0) updatedAt else System.currentTimeMillis(),
        )
    }
}

object WeatherLocation {
    fun current(context: Context, onLocation: (Double, Double) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        // Before S, GPS_PROVIDER requires ACCESS_FINE_LOCATION; coarse-only apps must use NETWORK.
        val candidates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        else listOf(LocationManager.NETWORK_PROVIDER)
        val provider = candidates.firstOrNull { manager.isProviderEnabled(it) } ?: return
        val lastKnown = lastKnown(manager)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            currentLocation(manager, provider, lastKnown, context.mainExecutor, onLocation)
        } else if (lastKnown != null) {
            onLocation(lastKnown.latitude, lastKnown.longitude)
        } else {
            requestSingleUpdate(manager, provider, onLocation)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun currentLocation(
        manager: LocationManager,
        provider: String,
        lastKnown: Location?,
        executor: java.util.concurrent.Executor,
        onLocation: (Double, Double) -> Unit,
    ) {
        try {
            manager.getCurrentLocation(provider, null, executor) { location ->
                val result = location ?: lastKnown ?: return@getCurrentLocation
                onLocation(result.latitude, result.longitude)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    private fun requestSingleUpdate(
        manager: LocationManager,
        provider: String,
        onLocation: (Double, Double) -> Unit,
    ) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                onLocation(location.latitude, location.longitude)
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        try {
            manager.requestSingleUpdate(
                provider,
                listener,
                Looper.getMainLooper(),
            )
            // Never leave a listener registered when no fix arrives (e.g. indoors).
            android.os.Handler(Looper.getMainLooper()).postDelayed({ manager.removeUpdates(listener) }, 30_000L)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @Suppress("MissingPermission")
    private fun lastKnown(manager: LocationManager): Location? {
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                }
            }
            .maxByOrNull { it.time }
    }
}
