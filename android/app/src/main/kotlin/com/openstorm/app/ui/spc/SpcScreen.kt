package com.openstorm.app.ui.spc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Tornado
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openstorm.core.domain.model.SpcOutlook
import com.openstorm.core.domain.model.SpcRiskArea
import com.openstorm.core.domain.model.SpcRiskLevel
import com.openstorm.core.domain.model.SpcWatch
import com.openstorm.core.domain.model.WatchType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpcScreen(
    viewModel: SpcViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadSpcProducts()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("SPC Outlooks") },
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            },
        )

        // Day selector chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (day in 1..3) {
                FilterChip(
                    selected = uiState.selectedDay == day,
                    onClick = { viewModel.selectDay(day) },
                    label = { Text("Day $day") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    ),
                )
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Active watches section
                if (uiState.watches.isNotEmpty()) {
                    item {
                        SectionHeader("Active Watches")
                    }
                    items(uiState.watches, key = { it.id }) { watch ->
                        WatchCard(watch)
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // Outlook risk areas
                val outlook = uiState.currentOutlook
                if (outlook != null && outlook.riskAreas.isNotEmpty()) {
                    item {
                        SectionHeader("Day ${uiState.selectedDay} Convective Outlook")
                    }
                    item {
                        OutlookSummaryCard(outlook)
                    }
                    items(outlook.riskAreas.reversed()) { area ->
                        RiskAreaCard(area)
                    }
                } else if (outlook != null) {
                    item {
                        SectionHeader("Day ${uiState.selectedDay} Convective Outlook")
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No risk areas in Day ${uiState.selectedDay} outlook",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun OutlookSummaryCard(outlook: SpcOutlook) {
    val highestRisk = outlook.riskAreas.maxByOrNull { it.riskLevel.ordinal }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Highest Risk:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (highestRisk != null) {
                    RiskLevelChip(highestRisk.riskLevel)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Issued: ${formatTimestamp(outlook.issuedAt.toString())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = "Valid through: ${formatTimestamp(outlook.expiresAt.toString())}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Risk level legend
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                SpcRiskLevel.entries.forEach { level ->
                    val hasThisLevel = outlook.riskAreas.any { it.riskLevel == level }
                    if (hasThisLevel) {
                        RiskLevelDot(level)
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskAreaCard(area: SpcRiskArea) {
    val color = riskLevelColor(area.riskLevel)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = area.riskLevel.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = color,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = area.riskLevel.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = riskDescription(area.riskLevel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun WatchCard(watch: SpcWatch) {
    val color = when (watch.type) {
        WatchType.TORNADO -> Color(0xFFFF0000)
        WatchType.SEVERE_THUNDERSTORM -> Color(0xFFFFAA00)
    }
    val icon = when (watch.type) {
        WatchType.TORNADO -> Icons.Default.Tornado
        WatchType.SEVERE_THUNDERSTORM -> Icons.Default.Thunderstorm
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = color,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (watch.type) {
                        WatchType.TORNADO -> "Tornado Watch #${watch.number}"
                        WatchType.SEVERE_THUNDERSTORM -> "Severe T-Storm Watch #${watch.number}"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Expires: ${formatTimestamp(watch.expiresAt.toString())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun RiskLevelChip(level: SpcRiskLevel) {
    val color = riskLevelColor(level)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = level.displayName,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
    }
}

@Composable
private fun RiskLevelDot(level: SpcRiskLevel) {
    val color = riskLevelColor(level)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = level.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

private fun riskLevelColor(level: SpcRiskLevel): Color {
    return when (level) {
        SpcRiskLevel.TSTM -> Color(0xFF66A366)
        SpcRiskLevel.MRGL -> Color(0xFF2E8B2E)
        SpcRiskLevel.SLGT -> Color(0xFFDAA520)
        SpcRiskLevel.ENH -> Color(0xFFFF8C00)
        SpcRiskLevel.MDT -> Color(0xFFDC2626)
        SpcRiskLevel.HIGH -> Color(0xFFE040E0)
    }
}

private fun riskDescription(level: SpcRiskLevel): String {
    return when (level) {
        SpcRiskLevel.TSTM -> "Isolated to scattered thunderstorms expected"
        SpcRiskLevel.MRGL -> "Isolated severe storms possible"
        SpcRiskLevel.SLGT -> "Scattered severe storms expected"
        SpcRiskLevel.ENH -> "Numerous severe storms likely"
        SpcRiskLevel.MDT -> "Widespread severe weather including potential for significant events"
        SpcRiskLevel.HIGH -> "Major severe weather outbreak expected — rare, significant event"
    }
}

private fun formatTimestamp(iso: String): String {
    return iso
        .substringAfter("T")
        .substringBefore(".")
        .let { "${it}Z" }
}
