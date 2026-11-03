package com.watchyourself.mensaplan.data

import android.content.Context
import com.watchyourself.mensaplan.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Laedt den Speiseplan aus dem Netz und haelt ihn im Zwischenspeicher,
 * damit App, Tile und Complication ohne Netz sofort etwas anzeigen koennen.
 */
object MensaRepository {

    private const val MENU_URL = "https://cga.se4.kit.edu/speiseplan/weekly/menu-de.html"
    private const val PREFS = "mensa_cache"
    private const val KEY_JSON = "menu_json"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val MAX_AGE_MS = 30L * 60L * 1000L
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) MensaWear/1.0"

    @Volatile
    private var memoryPlan: MensaPlan? = null

    fun lastUpdate(context: Context): Long = prefs(context).getLong(KEY_FETCHED_AT, 0L)

    fun needsRefresh(context: Context): Boolean {
        val fetchedAt = lastUpdate(context)
        return fetchedAt == 0L || System.currentTimeMillis() - fetchedAt > MAX_AGE_MS
    }

    /** Plan aus dem Zwischenspeicher (blockiert nicht, macht kein Netz). */
    fun cachedPlan(context: Context): MensaPlan? {
        memoryPlan?.let { return it }
        val prefs = prefs(context)
        val json = prefs.getString(KEY_JSON, null) ?: return null
        val plan = MensaParser.parse(json, prefs.getLong(KEY_FETCHED_AT, 0L)) ?: return null
        memoryPlan = plan
        return plan
    }

    suspend fun refresh(context: Context): Result<MensaPlan> = withContext(Dispatchers.IO) {
        runCatching {
            val html = download(MENU_URL)
            val json = MensaParser.extractJson(html)
                ?: error(context.getString(R.string.error_no_data))
            val fetchedAt = System.currentTimeMillis()
            val plan = MensaParser.parse(json, fetchedAt)
                ?: error(context.getString(R.string.error_no_data))

            prefs(context).edit()
                .putString(KEY_JSON, json)
                .putLong(KEY_FETCHED_AT, fetchedAt)
                .apply()
            memoryPlan = plan
            plan
        }
    }

    private fun download(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
