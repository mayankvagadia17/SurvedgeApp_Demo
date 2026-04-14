package com.nexova.survedge.ui.mapping.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LineStakeoutSheet(
    state: MappingSheetState.LineStakeout,
    onDismiss: () -> Unit
) {
    val currentPage = remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = { state.onClose(); onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DragHandle()
                Spacer(modifier = Modifier.weight(1f))
                Text("Line Stakeout", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { state.onClose(); onDismiss() }) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            SheetDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text("Target Line: ${state.lineCode}", style = MaterialTheme.typography.bodyMedium)
            if (state.lineName.isNotEmpty()) {
                Text(state.lineName, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentPage.intValue == 0) {
                CartesianView(
                    northSouth = state.northSouth,
                    eastWest = state.eastWest,
                    cutFill = state.cutFill
                )
            } else {
                PolarView(
                    distance = state.distance,
                    azimuth = state.azimuth
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            PaginationDots(
                current = currentPage.intValue,
                total = 2,
                onPageChange = { currentPage.intValue = it }
            )

            if (state.inTolerance) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "IN TOLERANCE",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CartesianView(northSouth: Double, eastWest: Double, cutFill: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StakeoutValue("N/S", northSouth.toString())
        Spacer(modifier = Modifier.height(8.dp))
        StakeoutValue("E/W", eastWest.toString())
        Spacer(modifier = Modifier.height(8.dp))
        StakeoutValue("Cut/Fill", cutFill.toString())
    }
}

@Composable
private fun PolarView(distance: Double, azimuth: Double) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StakeoutValue("Distance", distance.toString())
        Spacer(modifier = Modifier.height(8.dp))
        StakeoutValue("Azimuth", azimuth.toString())
    }
}

@Composable
private fun StakeoutValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun PaginationDots(current: Int, total: Int, onPageChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        repeat(total) { index ->
            androidx.compose.material3.Button(
                onClick = { onPageChange(index) },
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = if (index == current)
                    androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                else
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            ) {
                Text((index + 1).toString())
            }
            if (index < total - 1) Spacer(modifier = Modifier.width(8.dp))
        }
    }
}
