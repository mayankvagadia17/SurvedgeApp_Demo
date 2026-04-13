package com.nexova.survedge.ui.mapping.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditLineSheet(
    state: MappingSheetState.EditLine,
    onDismiss: () -> Unit
) {
    val isClosedLine = remember { mutableStateOf(state.isClosedLine) }

    ModalBottomSheet(
        onDismissRequest = { state.onClose(); onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text("Edit Line", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { state.onClose(); onDismiss() }) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            SheetDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text("Code: L1", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Closed Line", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                Checkbox(
                    checked = isClosedLine.value,
                    onCheckedChange = { isClosedLine.value = it }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("Points (${state.points.size})", style = MaterialTheme.typography.labelMedium)

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(state.points) { _, point ->
                    PointDragItem(point)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { state.onSave(isClosedLine.value, state.points) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Line")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PointDragItem(point: LabeledPoint) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("≡", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(point.id, style = MaterialTheme.typography.bodyMedium)
            Text("Line1", style = MaterialTheme.typography.bodySmall)
        }
    }
}
