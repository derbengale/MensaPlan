package com.watchyourself.mensaplan.data

import androidx.annotation.StringRes
import com.watchyourself.mensaplan.R
import java.time.LocalDate

/**
 * Wer isst in der Mensa? Bestimmt, welcher der drei Preise angezeigt wird.
 */
enum class PriceCategory(@StringRes val labelRes: Int) {
    STUDENT(R.string.category_student),
    STAFF(R.string.category_staff),
    GUEST(R.string.category_guest);

    companion object {
        fun fromName(name: String?): PriceCategory =
            entries.firstOrNull { it.name == name } ?: STAFF
    }
}

/**
 * Ein einzelnes Gericht einer Menülinie an einem Tag.
 */
data class Dish(
    val category: String,
    val description: String,
    val allergens: String,
    val jobFit: Boolean,
    val studentPrice: String,
    val staffPrice: String,
    val guestPrice: String,
) {
    fun price(category: PriceCategory): String = when (category) {
        PriceCategory.STUDENT -> studentPrice
        PriceCategory.STAFF -> staffPrice
        PriceCategory.GUEST -> guestPrice
    }.asEuro()
}

/** Ein Tag mit allen offenen Gerichten (geschlossene Linien werden weggelassen). */
data class MensaDay(
    val date: LocalDate,
    val dishes: List<Dish>,
)

/** Eine Woche (Montag bis Freitag) aus dem Speiseplan. */
data class MensaWeek(
    val start: LocalDate,
    val end: LocalDate,
    val days: List<MensaDay>,
)

/** Der komplette Speiseplan (mehrere Wochen) inklusive Ladezeitpunkt. */
data class MensaPlan(
    val weeks: List<MensaWeek>,
    val fetchedAt: Long,
) {
    /** Woche, die [date] enthält – sonst die nächste – sonst die letzte. */
    fun weekFor(date: LocalDate): MensaWeek? =
        weeks.firstOrNull { !date.isBefore(it.start) && !date.isAfter(it.end) }
            ?: weeks.firstOrNull { it.start.isAfter(date) }
            ?: weeks.lastOrNull()

    fun daysFor(date: LocalDate): List<MensaDay> = weekFor(date)?.days.orEmpty()
}

/** Wandelt "3.60" bzw. "3,60 €" in "3,60 €" um (leere Preise bleiben leer). */
internal fun String.asEuro(): String {
    val cleaned = trim().removeSuffix("€").trim()
    if (cleaned.isEmpty()) return ""
    return cleaned.replace('.', ',') + " €"
}
