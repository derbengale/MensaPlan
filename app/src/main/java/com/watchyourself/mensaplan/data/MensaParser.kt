package com.watchyourself.mensaplan.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Liest den Speiseplan der KIT-Campus-Gastronomie.
 *
 * Die Seite speiseplan/weekly/menu-de.html bettet alle Wochen als JSON in ein
 * Script ein (`const menuData = { ... }`). Dieses JSON wird hier
 * herausgeschnitten und ausgewertet.
 */
object MensaParser {

    private const val MARKER = "const menuData"

    private val WEEKDAYS = listOf(
        "monday" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY,
    )

    /** Schneidet das eingebettete JSON-Objekt aus der HTML-Seite. */
    fun extractJson(html: String): String? {
        val markerIndex = html.indexOf(MARKER)
        if (markerIndex < 0) return null
        val start = html.indexOf('{', markerIndex)
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until html.length) {
            val c = html[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return html.substring(start, i + 1)
                    }
                }
            }
        }
        return null
    }

    fun parse(json: String, fetchedAt: Long): MensaPlan? {
        val weeksObject = runCatching { JSONObject(json).optJSONObject("weeks") }
            .getOrNull() ?: return null

        val weeks = mutableListOf<MensaWeek>()
        for (key in weeksObject.keys()) {
            val weekJson = weeksObject.optJSONObject(key) ?: continue
            val start = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
            val end = runCatching {
                LocalDate.parse(weekJson.stringOrNull("endDate").orEmpty())
            }.getOrNull() ?: start.plusDays(4)
            weeks += MensaWeek(start, end, parseDays(weekJson, start))
        }

        if (weeks.isEmpty()) return null
        return MensaPlan(weeks.sortedBy { it.start }, fetchedAt)
    }

    private fun parseDays(weekJson: JSONObject, start: LocalDate): List<MensaDay> {
        val daysJson = weekJson.optJSONObject("days") ?: return emptyList()
        return WEEKDAYS.mapIndexed { index, (key, _) ->
            MensaDay(
                date = start.plusDays(index.toLong()),
                dishes = parseDishes(daysJson.optJSONArray(key)),
            )
        }
    }

    private fun parseDishes(array: JSONArray?): List<Dish> {
        if (array == null) return emptyList()
        val dishes = mutableListOf<Dish>()
        for (i in 0 until array.length()) {
            val dishJson = array.optJSONObject(i) ?: continue
            if (dishJson.optBoolean("isClosed")) continue

            val description = dishJson.optJSONObject("dishName")
                ?.stringOrNull("de")
                ?.trim()
                .orEmpty()
            if (description.isEmpty()) continue

            val prices = dishJson.optJSONObject("prices")
            dishes += Dish(
                category = dishJson.optJSONObject("category")?.stringOrNull("de")?.trim().orEmpty(),
                description = description,
                allergens = dishJson.stringOrNull("allergens")?.trim().orEmpty(),
                jobFit = dishJson.optBoolean("hasJobFit"),
                studentPrice = prices?.stringOrNull("studierende").orEmpty(),
                staffPrice = prices?.stringOrNull("bedienstete").orEmpty(),
                guestPrice = prices?.stringOrNull("gaeste").orEmpty(),
            )
        }
        return dishes
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        opt(key)?.takeIf { it != JSONObject.NULL }?.toString()
}
