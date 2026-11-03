package com.watchyourself.mensaplan.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import com.watchyourself.mensaplan.R
import com.watchyourself.mensaplan.data.Dish
import com.watchyourself.mensaplan.data.MensaDay
import com.watchyourself.mensaplan.data.MensaPlan
import com.watchyourself.mensaplan.data.MensaRepository
import com.watchyourself.mensaplan.data.MensaSettings
import com.watchyourself.mensaplan.data.PriceCategory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Date
import java.util.Locale

private val Accent = Color(0xFF00C08B)
private val CardColor = Color(0xFF17211D)
private val CardStroke = Color(0xFF24332C)
private val TextBright = Color(0xFFF1F5F3)
private val TextDim = Color(0xFF93A59D)

private val DateShort = DateTimeFormatter.ofPattern("dd.MM.")
private val TimeFormat = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMAN)

/** Drehweg an der Krone, ab dem ein Gericht weitergeschaltet wird. */
private val RotaryStep = 40.dp

@Composable
fun MensaApp() {
    val context = LocalContext.current
    val settings = remember { MensaSettings(context) }
    val scope = rememberCoroutineScope()

    var category by remember { mutableStateOf(settings.priceCategory) }
    var plan by remember { mutableStateOf(MensaRepository.cachedPlan(context)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var lastUpdate by remember { mutableLongStateOf(MensaRepository.lastUpdate(context)) }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            MensaRepository.refresh(context)
                .onSuccess {
                    plan = it
                    lastUpdate = it.fetchedAt
                    updateTile(context)
                }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (MensaRepository.needsRefresh(context)) refresh()
    }

    if (showSettings) {
        SettingsScreen(
            category = category,
            onCategoryChange = {
                category = it
                settings.priceCategory = it
                updateTile(context)
            },
            loading = loading,
            lastUpdate = lastUpdate,
            error = error,
            onRefresh = { refresh() },
            onBack = { showSettings = false },
        )
    } else {
        PlanScreen(
            plan = plan,
            category = category,
            loading = loading,
            error = error,
            onRefresh = { refresh() },
            onOpenSettings = { showSettings = true },
        )
    }
}

@Composable
private fun PlanScreen(
    plan: MensaPlan?,
    category: PriceCategory,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val today = LocalDate.now()
    val days = plan?.daysFor(today).orEmpty()

    if (days.isEmpty()) {
        MessageScreen(
            text = when {
                error != null -> error
                loading -> stringResource(R.string.loading)
                else -> stringResource(R.string.no_data)
            },
            loading = loading,
            onRefresh = onRefresh,
            onOpenSettings = onOpenSettings,
        )
        return
    }

    val initialPage = days.indexOfFirst { it.date == today }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { days.size }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    val stepPx = with(LocalDensity.current) { RotaryStep.toPx() }
    var rotaryDelta by remember { mutableFloatStateOf(0f) }
    // Gewaehltes Gericht je Tag, damit die Krone es steuern kann.
    val dishIndexByDay = remember { mutableStateMapOf<LocalDate, Int>() }
    // Tag-Modus: Krone wechselt den Tag statt des Gerichts.
    var dayMode by remember { mutableStateOf(false) }

    fun toggleDayMode() {
        dayMode = !dayMode
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun stepDish(delta: Int) {
        val day = days.getOrNull(pagerState.currentPage) ?: return
        val count = day.dishes.size
        if (count < 2) return
        val current = (dishIndexByDay[day.date] ?: 0).coerceIn(0, count - 1)
        dishIndexByDay[day.date] = (current + delta).mod(count)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Krone drehen blaettert durch die Gerichte des Tages.
            .onRotaryScrollEvent { event ->
                if (dayMode) {
                    // Tag-Modus: Krone blaettert durch die Tage.
                    rotaryDelta = 0f
                    scope.launch { pagerState.scrollBy(event.verticalScrollPixels) }
                    return@onRotaryScrollEvent true
                }
                val day = days.getOrNull(pagerState.currentPage)
                if ((day?.dishes?.size ?: 0) > 1) {
                    rotaryDelta += event.verticalScrollPixels
                    var stepped = false
                    while (rotaryDelta >= stepPx) {
                        stepDish(1)
                        rotaryDelta -= stepPx
                        stepped = true
                    }
                    while (rotaryDelta <= -stepPx) {
                        stepDish(-1)
                        rotaryDelta += stepPx
                        stepped = true
                    }
                    if (stepped) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                } else {
                    // Ohne Gerichteauswahl blaettert die Krone durch die Tage.
                    scope.launch { pagerState.scrollBy(event.verticalScrollPixels) }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        // Hoch-/runterwischen wechselt den Tag.
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val day = days[page]
            DayPage(
                day = day,
                category = category,
                isToday = day.date == today,
                dishIndex = dishIndexByDay[day.date] ?: 0,
                dayMode = dayMode,
                onToggleDayMode = { toggleDayMode() },
                onNextDish = {
                    val count = day.dishes.size
                    if (count > 0) {
                        dishIndexByDay[day.date] = ((dishIndexByDay[day.date] ?: 0) + 1).mod(count)
                    }
                },
            )
        }
        // Punkte senkrecht am rechten Rand: auf runden Displays immer sichtbar.
        DayIndicator(
            dayCount = days.size,
            currentDay = pagerState.currentPage,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 5.dp),
        )
        SettingsButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun DayPage(
    day: MensaDay,
    category: PriceCategory,
    isToday: Boolean,
    dishIndex: Int,
    dayMode: Boolean,
    onToggleDayMode: () -> Unit,
    onNextDish: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (dayMode) Accent else Color.Transparent)
                .border(1.dp, if (dayMode) Accent else CardStroke, RoundedCornerShape(50))
                .clickable(onClick = onToggleDayMode)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = dayLabel(day.date, isToday),
                style = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = if (dayMode) Color.Black else Accent,
                    letterSpacing = 0.4.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))

        if (day.dishes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardColor)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.closed),
                    style = TextStyle(
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = TextDim,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        } else {
            val index = dishIndex.coerceIn(0, day.dishes.lastIndex)
            DishCard(
                dish = day.dishes[index],
                category = category,
                position = index + 1,
                total = day.dishes.size,
                dayMode = dayMode,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                // Tippen blaettert zum naechsten Gericht.
                onNext = onNextDish,
            )
        }
    }
}

@Composable
private fun DishCard(
    dish: Dish,
    category: PriceCategory,
    position: Int,
    total: Int,
    dayMode: Boolean,
    modifier: Modifier = Modifier,
    onNext: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardColor)
            .clickable(onClick = onNext)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (dish.category.isNotEmpty()) {
            Text(
                text = dish.category,
                style = TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = Accent,
                    letterSpacing = 0.4.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
        }

        BasicText(
            text = dish.description,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            style = TextStyle(
                fontSize = 15.sp,
                color = TextBright,
                textAlign = TextAlign.Center,
            ),
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            // Lange Beschreibungen werden kleiner, damit Preis und Hinweis sichtbar bleiben.
            autoSize = TextAutoSize.StepBased(
                minFontSize = 11.sp,
                maxFontSize = 15.sp,
                stepSize = 0.5.sp,
            ),
        )

        Spacer(Modifier.height(5.dp))

        Text(
            text = dish.price(category).ifEmpty { "--" },
            style = TextStyle(
                fontSize = 21.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                color = Accent,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = stringResource(category.labelRes),
            style = TextStyle(
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = TextDim,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )

        if (total > 1) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (dayMode) {
                    stringResource(R.string.dish_counter_tap, position, total)
                } else {
                    stringResource(R.string.dish_counter, position, total) +
                        " · " + stringResource(R.string.tap_next_dish)
                },
                style = TextStyle(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = TextDim,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DayIndicator(
    dayCount: Int,
    currentDay: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(dayCount) { index ->
            val active = index == currentDay
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = if (active) 10.dp else 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) Accent else CardStroke)
            )
        }
    }
}

@Composable
private fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.settings_title)
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(CardColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "⚙", style = TextStyle(fontSize = 18.sp, color = TextBright))
    }
}

@Composable
private fun MessageScreen(
    text: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onRefresh)
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = text,
            style = TextStyle(
                fontSize = 16.sp,
                lineHeight = 21.sp,
                color = TextDim,
                textAlign = TextAlign.Center,
            ),
        )
        if (!loading) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.refresh_now),
                style = TextStyle(fontSize = 14.sp, color = Accent),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "⚙ " + stringResource(R.string.settings_title),
            modifier = Modifier.clickable(onClick = onOpenSettings),
            style = TextStyle(fontSize = 13.sp, color = TextDim),
        )
    }
}

@Composable
private fun SettingsScreen(
    category: PriceCategory,
    onCategoryChange: (PriceCategory) -> Unit,
    loading: Boolean,
    lastUpdate: Long,
    error: String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = TextStyle(
                fontSize = 16.sp,
                lineHeight = 20.sp,
                color = Accent,
                letterSpacing = 0.5.sp,
            ),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.settings_price_for),
            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, color = TextDim),
        )
        Spacer(Modifier.height(6.dp))

        PriceCategory.entries.forEach { option ->
            SelectRow(
                label = stringResource(option.labelRes),
                selected = option == category,
                onClick = { onCategoryChange(option) },
            )
            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.crown_hint),
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = TextDim,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(4.dp))
        SelectRow(
            label = if (loading) stringResource(R.string.loading) else stringResource(R.string.refresh_now),
            selected = false,
            showCheck = false,
            onClick = { if (!loading) onRefresh() },
        )

        Spacer(Modifier.height(6.dp))
        Text(
            text = if (lastUpdate == 0L) {
                stringResource(R.string.never_updated)
            } else {
                stringResource(R.string.last_update, TimeFormat.format(Date(lastUpdate)))
            },
            style = TextStyle(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = TextDim,
                textAlign = TextAlign.Center,
            ),
        )
        if (error != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = error,
                style = TextStyle(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = Color(0xFFFFB4AB),
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(8.dp))
        SelectRow(
            label = stringResource(R.string.settings_back),
            selected = false,
            showCheck = false,
            onClick = onBack,
        )
    }
}

@Composable
private fun SelectRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    showCheck: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF12352C) else CardColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontSize = 15.sp,
                lineHeight = 19.sp,
                color = if (selected) Accent else TextBright,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showCheck && selected) {
            Text(text = "✓", style = TextStyle(fontSize = 15.sp, color = Accent))
        }
    }
}

private fun dayLabel(date: LocalDate, isToday: Boolean): String {
    val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.GERMAN)
    val base = "$weekday, ${date.format(DateShort)}"
    return if (isToday) "$base · heute" else base
}

/** Bittet das System, die Kachel neu zu zeichnen (z. B. nach Preisaenderung). */
private fun updateTile(context: android.content.Context) {
    runCatching {
        androidx.wear.tiles.TileService.getUpdater(context)
            .requestUpdate(com.watchyourself.mensaplan.tile.MainTileService::class.java)
    }
}
