package com.guestvault.ui.screens.limits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.guestvault.core.Premium
import com.guestvault.core.PremiumFeature
import com.guestvault.domain.model.AppCategory
import com.guestvault.domain.model.GuestApp
import com.guestvault.domain.model.GuestSchedule
import com.guestvault.core.Formatters
import com.guestvault.domain.policy.AppLimitPolicy
import com.guestvault.domain.policy.DailyBudgetPolicy
import com.guestvault.domain.policy.SchedulePolicy
import com.guestvault.domain.repository.SettingsRepository
import com.guestvault.domain.usecase.GetInstalledAppsUseCase
import com.guestvault.domain.usecase.ObserveSettingsUseCase
import com.guestvault.ui.components.AppIcon
import com.guestvault.ui.components.GuestVaultTopBar
import com.guestvault.ui.components.IconBadge
import com.guestvault.ui.components.InfoBanner
import com.guestvault.ui.components.SecondaryButton
import com.guestvault.ui.components.SettingSwitchRow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val LIMIT_CHOICES = listOf(0, 5, 15, 30, 60)
private val DAILY_CHOICES = listOf(0, 30, 60, 120, 180)

data class TimeRulesUiState(
    val apps: List<GuestApp> = emptyList(),
    val limits: Map<String, Int> = emptyMap(),
    val schedule: GuestSchedule = GuestSchedule(),
    /** 0 means no daily budget. */
    val dailyBudgetMinutes: Int = 0,
    /** Guest time already spent today; only meaningful with a daily budget. */
    val usedTodayMillis: Long = 0L,
    /** "Open now" / "Paused until 07:00 tomorrow" — computed once per screen entry. */
    val scheduleStatus: String = "",
    val loaded: Boolean = false
)

@HiltViewModel
class TimeRulesViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val settingsRepository: SettingsRepository,
    private val getInstalledApps: GetInstalledAppsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(TimeRulesUiState())
    val state: StateFlow<TimeRulesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val installed = runCatching { getInstalledApps() }
                .getOrDefault(emptyList())
                .associateBy { it.packageName }

            // One collector: every write echoes back through the same flow, so
            // local state and stored state can never disagree for more than a
            // recomposition.
            observeSettings().collect { settings ->
                val apps = settings.allowedPackages
                    .map { pkg -> installed[pkg] ?: GuestApp(pkg, pkg, AppCategory.OTHER) }
                    .sortedBy { it.label.lowercase() }
                val used = if (DailyBudgetPolicy.hasBudget(settings.dailyBudgetMinutes)) {
                    runCatching {
                        DailyBudgetPolicy.usedToday(
                            base = settingsRepository.readDailyUsage(),
                            sessionStartedAt = settings.activeSession?.startedAt,
                            now = System.currentTimeMillis()
                        )
                    }.getOrDefault(0L)
                } else {
                    0L
                }
                _state.value = TimeRulesUiState(
                    apps = apps,
                    limits = settings.appLimits,
                    schedule = settings.schedule,
                    scheduleStatus = scheduleStatus(settings.schedule),
                    dailyBudgetMinutes = settings.dailyBudgetMinutes,
                    usedTodayMillis = used,
                    loaded = true
                )
            }
        }
    }

    fun setDailyBudget(minutes: Int) = viewModelScope.launch {
        settingsRepository.setDailyBudgetMinutes(minutes)
    }

    fun setLimit(packageName: String, minutes: Int) = viewModelScope.launch {
        val updated = _state.value.limits.toMutableMap().apply {
            if (minutes <= 0) remove(packageName) else put(packageName, minutes)
        }
        settingsRepository.setAppLimits(updated)
    }

    fun setScheduleEnabled(enabled: Boolean) = updateSchedule { it.copy(enabled = enabled) }

    fun setScheduleStart(minute: Int) = updateSchedule {
        it.copy(startMinuteOfDay = minute)
    }

    fun setScheduleEnd(minute: Int) = updateSchedule {
        it.copy(endMinuteOfDay = minute)
    }

    fun toggleDay(day: Int) = updateSchedule { current ->
        val days = if (day in current.days) current.days - day else current.days + day
        // Never save a schedule that can never open.
        current.copy(days = days.ifEmpty { current.days })
    }

    private fun updateSchedule(change: (GuestSchedule) -> GuestSchedule) =
        viewModelScope.launch {
            settingsRepository.setSchedule(change(_state.value.schedule))
        }

    private fun scheduleStatus(schedule: GuestSchedule): String =
        if (!schedule.enabled) {
            "No schedule — guest mode can start at any time"
        } else {
            val closed = SchedulePolicy.closedMessage(schedule, ZonedDateTime.now())
            closed ?: "Open now · ${SchedulePolicy.describe(schedule)}"
        }
}

@Composable
fun TimeRulesRoute(
    onBack: () -> Unit,
    viewModel: TimeRulesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TimeRulesScreen(
        state = state,
        onBack = onBack,
        onLimitChange = viewModel::setLimit,
        onDailyBudgetChange = viewModel::setDailyBudget,
        onScheduleEnabled = viewModel::setScheduleEnabled,
        onStartChange = viewModel::setScheduleStart,
        onEndChange = viewModel::setScheduleEnd,
        onToggleDay = viewModel::toggleDay
    )
}

@Composable
fun TimeRulesScreen(
    state: TimeRulesUiState,
    onBack: () -> Unit,
    onLimitChange: (String, Int) -> Unit,
    onDailyBudgetChange: (Int) -> Unit,
    onScheduleEnabled: (Boolean) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    onToggleDay: (Int) -> Unit
) {
    var editing by remember { mutableStateOf<GuestApp?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GuestVaultTopBar(
                title = "Time limits",
                subtitle = if (Premium.isPro) "Budgets per app, and when guests may run" else "A GuestVault Pro feature",
                onBack = onBack
            )
        }
    ) { padding ->
        if (Premium.isLocked(PremiumFeature.PER_APP_LIMITS)) {
            LockedTimeRules(padding = padding, onBack = onBack)
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                InfoBanner(
                    icon = Icons.Rounded.Timer,
                    title = "How a budget is counted",
                    text = "The clock starts when the guest opens the app and stops when " +
                        "they come back to GuestVault. Android does not let an ordinary " +
                        "app watch other apps, so this is the honest rule — not a guess."
                )
            }

            item {
                DailyBudgetCard(
                    minutes = state.dailyBudgetMinutes,
                    usedTodayMillis = state.usedTodayMillis,
                    onChange = onDailyBudgetChange
                )
            }

            item {
                Text(
                    text = "PER APP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.apps.isEmpty()) {
                item {
                    Text(
                        text = "No apps are allowed yet. Choose an allow-list first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(state.apps, key = { it.packageName }) { app ->
                    AppLimitRow(
                        app = app,
                        minutes = state.limits[app.packageName] ?: 0,
                        onClick = { editing = app }
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SCHEDULE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingSwitchRow(
                            icon = Icons.Rounded.Schedule,
                            title = "Only allow guest mode at set times",
                            subtitle = state.scheduleStatus,
                            checked = state.schedule.enabled,
                            onCheckedChange = onScheduleEnabled,
                            switchDescription = "Schedule toggle"
                        )
                    }
                }
            }

            if (state.schedule.enabled) {
                item {
                    TimeWindowCard(
                        schedule = state.schedule,
                        onStartChange = onStartChange,
                        onEndChange = onEndChange
                    )
                }
                item {
                    DayPicker(schedule = state.schedule, onToggleDay = onToggleDay)
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    editing?.let { app ->
        LimitDialog(
            app = app,
            current = state.limits[app.packageName] ?: 0,
            onPick = { minutes ->
                editing = null
                onLimitChange(app.packageName, minutes)
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun AppLimitRow(app: GuestApp, minutes: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = app.packageName, label = app.label, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = if (minutes > 0) "Budgeted" else "No limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = AppLimitPolicy.label(minutes),
                style = MaterialTheme.typography.labelLarge,
                color = if (minutes > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * The whole day's guest time, across every session.
 *
 * Separate from the per-app list on purpose: "an hour of phone a day" and
 * "ten minutes of YouTube a hand-over" are different promises, and an owner who
 * wants one should not have to configure the other.
 */
@Composable
private fun DailyBudgetCard(
    minutes: Int,
    usedTodayMillis: Long,
    onChange: (Int) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    val usedLabel = Formatters.duration(usedTodayMillis.coerceAtLeast(0L))

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = Icons.Rounded.Today)
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = "Daily limit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (minutes > 0) {
                            "$usedLabel used today of ${AppLimitPolicy.label(minutes)}"
                        } else {
                            "No daily limit — guest mode can run as long as you allow"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { editing = true }) {
                    Text(if (minutes > 0) "Change" else "Set")
                }
            }
            if (minutes > 0) {
                Spacer(Modifier.height(8.dp))
                val remaining = AppLimitPolicy.remainingMillis(minutes, usedTodayMillis)
                Text(
                    text = DailyBudgetPolicy.caption(minutes, usedTodayMillis)
                        ?: DailyBudgetPolicy.END_MESSAGE,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (remaining <= 0L) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Daily limit") },
            text = {
                Column {
                    Text(
                        text = "Total time a guest may use this phone in one day. It " +
                            "resets at midnight and is counted across sessions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DAILY_CHOICES.forEach { choice ->
                            FilterChip(
                                selected = choice == minutes,
                                onClick = {
                                    editing = false
                                    onChange(choice)
                                },
                                label = { Text(AppLimitPolicy.label(choice)) }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun LimitDialog(
    app: GuestApp,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label) },
        text = {
            Column {
                Text(
                    text = "How long may a guest use this app in one session?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LIMIT_CHOICES.forEach { minutes ->
                        FilterChip(
                            selected = minutes == current,
                            onClick = { onPick(minutes) },
                            label = { Text(AppLimitPolicy.label(minutes)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun TimeWindowCard(
    schedule: GuestSchedule,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Guest mode opens at",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TimeStepper(
                minutes = schedule.startMinuteOfDay,
                onChange = onStartChange,
                step = 30,
                label = "start time"
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "and closes at",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TimeStepper(
                minutes = schedule.endMinuteOfDay,
                onChange = onEndChange,
                step = 30,
                label = "end time"
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (schedule.startMinuteOfDay > schedule.endMinuteOfDay) {
                    "This window runs past midnight, into the next day."
                } else {
                    "Guest mode stays shut outside this window."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimeStepper(
    minutes: Int,
    onChange: (Int) -> Unit,
    step: Int,
    label: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TextButton(onClick = { onChange(wrap(minutes - step)) }) {
            Text("−")
        }
        Text(
            text = SchedulePolicy.formatMinute(minutes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { onChange(wrap(minutes + step)) }) {
            Text("+")
        }
    }
}

private fun wrap(minute: Int): Int {
    val day = GuestSchedule.MINUTES_PER_DAY
    return ((minute % day) + day) % day
}

@Composable
private fun DayPicker(schedule: GuestSchedule, onToggleDay: (Int) -> Unit) {
    Column {
        Text(
            text = "Days",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..7).forEach { day ->
                FilterChip(
                    selected = day in schedule.days,
                    onClick = { onToggleDay(day) },
                    label = { Text(SchedulePolicy.shortDay(day)) }
                )
            }
        }
    }
}

@Composable
private fun LockedTimeRules(padding: PaddingValues, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        IconBadge(icon = Icons.Rounded.WorkspacePremium)
        Text(
            text = PremiumFeature.PER_APP_LIMITS.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = PremiumFeature.PER_APP_LIMITS.blurb,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        InfoBanner(
            icon = Icons.Rounded.Lock,
            title = "This is the free build",
            text = "GuestVault Pro is a separate paid app. It installs beside this one, " +
                "keeps your settings, and still has no network permission — there is " +
                "nothing to unlock here, and nothing to buy inside this app."
        )
        SecondaryButton(text = "Back", onClick = onBack)
    }
}
