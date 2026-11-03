package com.watchyourself.mensaplan.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.watchyourself.mensaplan.R
import com.watchyourself.mensaplan.data.MensaRepository
import com.watchyourself.mensaplan.data.MensaSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Complication, die den Preis des ersten heutigen Gerichts zeigt
 * (in der in den Einstellungen gewaehlten Preisgruppe).
 */
class MainComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return createComplicationData("4,60 €")
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val text = withContext(Dispatchers.Default) { todayPrice() }
        return createComplicationData(text)
    }

    private fun todayPrice(): String {
        val settings = MensaSettings(this)
        val today = LocalDate.now()
        val day = MensaRepository.cachedPlan(this)
            ?.daysFor(today)
            ?.firstOrNull { it.date == today }
        val dish = day?.dishes?.firstOrNull() ?: return "--"
        return dish.price(settings.priceCategory).ifEmpty { "--" }
    }

    private fun createComplicationData(text: String) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(
                getString(R.string.complication_content_description, text)
            ).build()
        )
            .setTitle(PlainComplicationText.Builder(getString(R.string.complication_title)).build())
            .build()
}
