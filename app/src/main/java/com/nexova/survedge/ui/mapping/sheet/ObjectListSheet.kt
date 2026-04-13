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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ObjectListSheet(
    state: MappingSheetState.ObjectList,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { state.onClose(); onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DragHandle()
                Spacer(modifier = Modifier.weight(1f))
                Text("Select Object", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { state.onClose(); onDismiss() }) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            SheetDivider()

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.points) { point ->
                    PointListItem(point) { state.onItemSelected(point) }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { state.onAddPoint() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, "Add")
                    Text("Point")
                }
                Spacer(modifier = Modifier.padding(4.dp))
                Button(
                    onClick = { state.onAddLine() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, "Add")
                    Text("Line")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PointListItem(point: LabeledPoint, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(point.id, style = MaterialTheme.typography.bodyMedium)
        Text("P1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
