package com.datausage.monitor.ui.screen.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datausage.monitor.data.local.db.entity.SessionEntity
import com.datausage.monitor.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onNavigateBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val appUsages by viewModel.appUsages.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sessions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Start/Stop button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRunning)
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
                        if (isRunning && activeSession != null) {
                            Text(
                                "Session Active",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Started: ${FormatUtils.formatDateTime(activeSession!!.startTime)}")
                            Text(
                                "Usage: ${FormatUtils.formatBytes(activeSession!!.totalBytesRx + activeSession!!.totalBytesTx)}"
                            )
                            Text(
                                "Down: ${FormatUtils.formatBytes(activeSession!!.totalBytesRx)} / Up: ${FormatUtils.formatBytes(activeSession!!.totalBytesTx)}"
                            )
                        } else {
                            Text(
                                "No Active Session",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        FilledTonalButton(
                            onClick = {
                                if (isRunning) viewModel.stopSession()
                                else viewModel.startSession()
                            }
                        ) {
                            Icon(
                                if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null
                            )
                            Text(if (isRunning) "  Stop Session" else "  Start Session")
                        }
                    }
                }
            }

            // Per-app usage for active session
            if (isRunning && appUsages.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "App Usage (Current Session)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(appUsages) { usage ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                usage.packageName.substringAfterLast("."),
                                modifier = Modifier.weight(1f)
                            )
                            Text(FormatUtils.formatBytes(usage.totalRx + usage.totalTx))
                        }
                    }
                }
            }

            // Previous sessions
            if (sessions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Previous Sessions",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(sessions.filter { it.endTime != null }.take(20)) { session ->
                    SessionHistoryCard(session)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: SessionEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    FormatUtils.formatDate(session.startTime),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    FormatUtils.formatBytes(session.totalBytesRx + session.totalBytesTx),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                "${FormatUtils.formatTime(session.startTime)} - ${FormatUtils.formatTime(session.endTime ?: 0)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val duration = (session.endTime ?: System.currentTimeMillis()) - session.startTime
            Text(
                "Duration: ${FormatUtils.formatDuration(duration)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
