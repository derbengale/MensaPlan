package com.watchyourself.mensaplan.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.watchyourself.mensaplan.R
import com.watchyourself.mensaplan.data.Dish
import com.watchyourself.mensaplan.data.MensaDay
import com.watchyourself.mensaplan.data.MensaPlan
import com.watchyourself.mensaplan.data.MensaRepository
import com.watchyourself.mensaplan.data.MensaSettings
import com.watchyourself.mensaplan.data.PriceCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private const val RESOURCES_VERSION = "1"
private const val CLICK_PREFIX = "dish:"
private const val FRESHNESS_MS = 30L * 60L * 1000L
private const val MAIN_ACTIVITY = "com.watchyourself.mensaplan.presentation.MainActivity"

private val Accent = 0xFF00C08B.toInt()
private val TextBright = 0xFFF1F5F3.toInt()
private val TextDim = 0xFF93A59D.toInt()

private val DateShort = DateTimeFormatter.ofPattern("dd.MM.")

/**
 * Kachel mit dem heutigen Speiseplan.
 *
 * Tippen zykelt durch die Gerichte des Tages: jeder Aufbau setzt die ID des
 * Clickables auf die *naechste* Position, der naechste Tile-Request liest
 * diese ID aus und zeigt das passende Gericht.
 */
class MainTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val context = applicationContext
        val settings = MensaSettings(context)

        val clickedIndex = requestParams.currentState.lastClickableId
            .takeIf { it.startsWith(CLICK_PREFIX) }
            ?.removePrefix(CLICK_PREFIX)
            ?.toIntOrNull()

        if (clickedIndex != null) {
            settings.tileDishIndex = clickedIndex
        }

        val plan = MensaRepository.cachedPlan(context)
        if (MensaRepository.needsRefresh(context)) {
            refreshInBackground(context)
        }

        val tile = buildTile(
            context = context,
            plan = plan,
            selectedIndex = clickedIndex ?: settings.tileDishIndex,
            category = settings.priceCategory,
        )
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<Resources> = Futures.immediateFuture(resources(requestParams))

    private fun refreshInBackground(context: Context) {
        scope.launch {
            runCatching { MensaRepository.refresh(context) }
            withContext(Dispatchers.Main) {
                runCatching {
                    TileService.getUpdater(context).requestUpdate(MainTileService::class.java)
                }
            }
        }
    }
}

private fun resources(requestParams: RequestBuilders.ResourcesRequest): Resources =
    Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .build()

private fun buildTile(
    context: Context,
    plan: MensaPlan?,
    selectedIndex: Int,
    category: PriceCategory,
): TileBuilders.Tile {
    val today = LocalDate.now()
    val day = plan?.daysFor(today)?.firstOrNull { it.date == today }
        ?: plan?.daysFor(today)?.firstOrNull()
    val dishes = day?.dishes.orEmpty()
    val index = if (dishes.isEmpty()) 0 else selectedIndex.coerceIn(0, dishes.lastIndex)
    val dish = dishes.getOrNull(index)

    val layout = buildLayout(context, day, dish, index, dishes.size, category)

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setFreshnessIntervalMillis(FRESHNESS_MS)
        .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
        .build()
}

private fun buildLayout(
    context: Context,
    day: MensaDay?,
    dish: Dish?,
    index: Int,
    total: Int,
    category: PriceCategory,
): LayoutElementBuilders.LayoutElement {
    val hasDish = dish != null && total > 0

    val onClick: ActionBuilders.Action = if (hasDish) {
        // Neu laden, ohne die App zu oeffnen.
        ActionBuilders.LoadAction.Builder().build()
    } else {
        // Ohne Daten: App oeffnen, damit sie den Plan holen kann.
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(context.packageName)
                    .setClassName(MAIN_ACTIVITY)
                    .build()
            )
            .build()
    }

    val modifiers = ModifiersBuilders.Modifiers.Builder()
        .setClickable(
            ModifiersBuilders.Clickable.Builder()
                .setId(if (hasDish) CLICK_PREFIX + ((index + 1) % total) else "open")
                .setOnClick(onClick)
                .setMinimumClickableWidth(dp(48f))
                .setMinimumClickableHeight(dp(48f))
                .build()
        )
        .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(8f)).build())
        .build()

    val contents = mutableListOf<LayoutElementBuilders.LayoutElement>()
    if (day != null) {
        contents += text(dayHeader(day.date, today = LocalDate.now()), 13f, TextDim, maxLines = 1)
        contents += spacer(3f)
    }

    if (dish != null) {
        if (dish.category.isNotEmpty()) {
            contents += text(dish.category, 11f, Accent, maxLines = 1)
            contents += spacer(2f)
        }
        contents += text(dish.description, 15f, TextBright, maxLines = 4)
        contents += spacer(4f)
        contents += text(dish.price(category).ifEmpty { "--" }, 20f, Accent, bold = true, maxLines = 1)
        contents += text(context.getString(category.labelRes), 11f, TextDim, maxLines = 1)
        if (total > 1) {
            contents += spacer(2f)
            contents += text("${index + 1}/$total", 10f, TextDim, maxLines = 1)
        }
    } else {
        contents += text(context.getString(R.string.tile_no_data), 14f, TextDim, maxLines = 2)
        contents += spacer(4f)
        contents += text(context.getString(R.string.tile_open_app), 11f, Accent, maxLines = 1)
    }

    val column = LayoutElementBuilders.Column.Builder()
        .setWidth(expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        .apply { contents.forEach { addContent(it) } }
        .build()

    return LayoutElementBuilders.Box.Builder()
        .setWidth(expand())
        .setHeight(expand())
        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        .setModifiers(modifiers)
        .addContent(column)
        .build()
}

private fun text(
    value: String,
    size: Float,
    color: Int,
    bold: Boolean = false,
    maxLines: Int = 0,
): LayoutElementBuilders.Text {
    val fontStyle = LayoutElementBuilders.FontStyle.Builder()
        .setSize(sp(size))
        .setColor(argb(color))
        .apply { if (bold) setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD) }
        .build()

    val builder = LayoutElementBuilders.Text.Builder()
        .setText(value)
        .setFontStyle(fontStyle)
        .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)

    if (maxLines > 0) {
        builder.setMaxLines(maxLines)
        builder.setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
    }
    return builder.build()
}

private fun spacer(heightDp: Float): LayoutElementBuilders.Spacer =
    LayoutElementBuilders.Spacer.Builder().setHeight(dp(heightDp)).build()

private fun dayHeader(date: LocalDate, today: LocalDate): String {
    val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.GERMAN)
    val base = "$weekday ${date.format(DateShort)}"
    return if (date == today) "$base · heute" else base
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData(::resources) {
    buildTile(context, plan = null, selectedIndex = 0, category = PriceCategory.STAFF)
}
