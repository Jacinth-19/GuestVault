package com.guestvault.ui.screens.selectapps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.guestvault.domain.model.AppCategory
import com.guestvault.domain.model.GuestApp
import com.guestvault.domain.repository.SettingsRepository
import com.guestvault.domain.usecase.ApplyRecommendedDefaultsUseCase
import com.guestvault.domain.usecase.GetInstalledAppsUseCase
import com.guestvault.domain.usecase.UpdateAllowedAppsUseCase
import com.guestvault.ui.components.AppIcon
import com.guestvault.ui.components.EmptyState
import com.guestvault.ui.components.GuestVaultDivider
import com.guestvault.ui.components.GuestVaultTopBar
import com.guestvault.ui.components.PrimaryButton
import com.guestvault.ui.components.StatusPill
import com.guestvault.ui.theme.Warning
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SelectAppsUiState(
    val apps: List<GuestApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val category: AppCategory = AppCategory.ALL,
    val loading: Boolean = true
) {
    val canContinue: Boolean get() = selected.isNotEmpty()
}

@HiltViewModel
class SelectAppsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val updateAllowedApps: UpdateAllowedAppsUseCase,
    private val applyRecommendedDefaults: ApplyRecommendedDefaultsUseCase
) : ViewModel() {

    private val apps = MutableStateFlow<List<GuestApp>>(emptyList())
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow(AppCategory.ALL)
    private val loading = MutableStateFlow(true)

    val state: StateFlow<SelectAppsUiState> = combine(
        apps, selected, query, category, loading
    ) { allApps, selection, search, selectedCategory, isLoading ->
        val filtered = allApps.filter { app ->
            val matchesQuery = search.isBlank() ||
                app.label.contains(search, ignoreCase = true) ||
                app.packageName.contains(search, ignoreCase = true)
            val matchesCategory = when (selectedCategory) {
                AppCategory.ALL -> true
                AppCategory.RECOMMENDED -> app.isRecommended
                else -> app.category == selectedCategory
            }
            matchesQuery && matchesCategory
        }
        SelectAppsUiState(
            apps = filtered,
            selected = selection,
            query = search,
            category = selectedCategory,
            loading = isLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SelectAppsUiState()
    )

    init {
        viewModelScope.launch {
            selected.value = settingsRepository.settings.first().allowedPackages
            apps.value = getInstalledApps()
            loading.value = false
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onCategoryChange(value: AppCategory) {
        category.value = value
    }

    fun toggle(packageName: String) {
        val next = selected.value.toMutableSet().apply {
            if (!add(packageName)) remove(packageName)
        }.toSet()
        selected.value = next
        viewModelScope.launch { updateAllowedApps(next) }
    }

    fun clearSelection() {
        selected.value = emptySet()
        viewModelScope.launch { updateAllowedApps(emptySet()) }
    }

    fun useRecommended() {
        viewModelScope.launch {
            selected.value = applyRecommendedDefaults(force = true)
            category.value = AppCategory.ALL
        }
    }
}

@Composable
fun SelectAppsRoute(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    continueLabel: String = "Continue",
    viewModel: SelectAppsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SelectAppsScreen(
        state = state,
        onBack = onBack,
        continueLabel = continueLabel,
        onQueryChange = viewModel::onQueryChange,
        onCategoryChange = viewModel::onCategoryChange,
        onToggle = viewModel::toggle,
        onClear = viewModel::clearSelection,
        onUseRecommended = viewModel::useRecommended,
        onContinue = onContinue
    )
}

@Composable
fun SelectAppsScreen(
    state: SelectAppsUiState,
    onBack: () -> Unit,
    continueLabel: String = "Continue",
    onQueryChange: (String) -> Unit,
    onCategoryChange: (AppCategory) -> Unit,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onUseRecommended: () -> Unit,
    onContinue: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GuestVaultTopBar(
                title = "Allowed apps",
                subtitle = "Guests only see what you pick",
                onBack = onBack
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${state.selected.size} apps selected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onUseRecommended) {
                            Text("Use recommended", style = MaterialTheme.typography.labelMedium)
                        }
                        if (state.selected.isNotEmpty()) {
                            TextButton(onClick = onClear) {
                                Text("Clear", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    PrimaryButton(
                        text = continueLabel,
                        onClick = onContinue,
                        enabled = state.canContinue
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AppCategory.entries.toList()) { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { onCategoryChange(category) },
                        label = { Text(category.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 4.dp)
            ) {
                if (state.loading) {
                    Text(
                        text = "Reading installed apps…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                } else if (state.apps.isEmpty()) {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "No apps match",
                        message = "Try a different search or category."
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 12.dp)) {
                        items(state.apps, key = { it.packageName }) { app ->
                            AppRow(
                                app = app,
                                checked = app.packageName in state.selected,
                                onToggle = { onToggle(app.packageName) }
                            )
                            GuestVaultDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: GuestApp,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = "App row ${app.label}"
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = app.packageName, label = app.label, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (app.isRecommended) {
                        "Recommended · ${app.category.displayName}"
                    } else {
                        app.category.displayName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (app.isSensitive) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        text = "Sensitive",
                        icon = Icons.Rounded.WarningAmber,
                        containerColor = Warning.copy(alpha = 0.16f),
                        contentColor = if (dark) Color(0xFFFCD34D) else Color(0xFF92400E)
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.size(24.dp)
        )
    }
}
