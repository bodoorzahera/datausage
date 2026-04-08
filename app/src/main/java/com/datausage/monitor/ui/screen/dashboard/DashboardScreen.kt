package com.datausage.monitor.ui.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datausage.monitor.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSession: (Long) -> Unit,
    onNavigateToAppConfig: (Long) -> Unit,
    onNavigateToReports: (Long) -> Unit,
    onNavigateToLimits: (Long) -> Unit,
    onNavigateToCost: (Long) -> Unit,
    onNavigateToImportExport: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profileId = viewModel.profileId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.profile?.name ?: "Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Session control
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isMonitoring)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (state.isMonitoring) "Monitoring Active" else "Monitoring Stopped",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (state.activeSession != null) {
                        Text(
                            "Since: ${FormatUtils.formatDateTime(state.activeSession!!.startTime)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(onClick = viewModel::toggleMonitoring) {
                        Icon(
                            if (state.isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Text(if (state.isMonitoring) "  Stop" else "  Start")
                    }
                }
            }

            // Usage summary cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UsageSummaryCard(
                    label = "Today",
                    value = FormatUtils.formatBytes(state.todayUsage),
                    modifier = Modifier.weight(1f)
                )
                UsageSummaryCard(
                    label = "This Week",
                    value = FormatUtils.formatBytes(state.weekUsage),
                    modifier = Modifier.weight(1f)
                )
                UsageSummaryCard(
                    label = "This Month",
                    value = FormatUtils.formatBytes(state.monthUsage),
                    modifier = Modifier.weight(1f)
                )
            }

            // Cost summary
            if (state.monthCost != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UsageSummaryCard(
                        label = "Today Cost",
                        value = state.todayCost?.let {
                            FormatUtils.formatCost(it.cost, it.currency)
                        } ?: "-",
                        modifier = Modifier.weight(1f)
                    )
                    UsageSummaryCard(
                        label = "Month Cost",
                        value = FormatUtils.formatCost(
                            state.monthCost!!.cost,
                            state.monthCost!!.currency
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Navigation buttons
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionCard("Sessions", Icons.Default.DataUsage, Modifier.weight(1f)) {
                    onNavigateToSession(profileId)
                }
                ActionCard("Apps", Icons.Default.Apps, Modifier.weight(1f)) {
                    onNavigateToAppConfig(profileId)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionCard("Reports", Icons.Default.Assessment, Modifier.weight(1f)) {
                    onNavigateToReports(profileId)
                }
                ActionCard("Limits", Icons.Default.Speed, Modifier.weight(1f)) {
                    onNavigateToLimits(profileId)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionCard("Cost", Icons.Default.AttachMoney, Modifier.weight(1f)) {
                    onNavigateToCost(profileId)
                }
                ActionCard("Import/Export", Icons.Default.ImportExport, Modifier.weight(1f)) {
                    onNavigateToImportExport(profileId)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UsageSummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionCard(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
