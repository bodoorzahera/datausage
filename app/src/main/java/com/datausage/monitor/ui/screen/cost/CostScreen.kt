package com.datausage.monitor.ui.screen.cost

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.datausage.monitor.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CostScreen(
    onNavigateBack: () -> Unit,
    viewModel: CostViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val costPerUnit by viewModel.costPerUnit.collectAsStateWithLifecycle()
    val unitType by viewModel.unitType.collectAsStateWithLifecycle()
    val currency by viewModel.currency.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cost Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Configure Cost", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = costPerUnit,
                onValueChange = viewModel::onCostPerUnitChange,
                label = { Text("Cost per unit") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = unitType == "GB",
                    onClick = { viewModel.onUnitTypeChange("GB") },
                    label = { Text("Per GB") }
                )
                FilterChip(
                    selected = unitType == "MB",
                    onClick = { viewModel.onUnitTypeChange("MB") },
                    label = { Text("Per MB") }
                )
            }

            OutlinedTextField(
                value = currency,
                onValueChange = viewModel::onCurrencyChange,
                label = { Text("Currency") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Cost Summary", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CostCard(
                    label = "Today",
                    cost = state.todayCost?.let {
                        FormatUtils.formatCost(it.cost, it.currency)
                    } ?: "-",
                    usage = state.todayCost?.let {
                        FormatUtils.formatBytes(it.totalBytes)
                    } ?: "-",
                    modifier = Modifier.weight(1f)
                )
                CostCard(
                    label = "This Week",
                    cost = state.weekCost?.let {
                        FormatUtils.formatCost(it.cost, it.currency)
                    } ?: "-",
                    usage = state.weekCost?.let {
                        FormatUtils.formatBytes(it.totalBytes)
                    } ?: "-",
                    modifier = Modifier.weight(1f)
                )
            }

            CostCard(
                label = "This Month",
                cost = state.monthCost?.let {
                    FormatUtils.formatCost(it.cost, it.currency)
                } ?: "-",
                usage = state.monthCost?.let {
                    FormatUtils.formatBytes(it.totalBytes)
                } ?: "-",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CostCard(
    label: String,
    cost: String,
    usage: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(cost, style = MaterialTheme.typography.titleMedium)
            Text(usage, style = MaterialTheme.typography.bodySmall)
        }
    }
}
