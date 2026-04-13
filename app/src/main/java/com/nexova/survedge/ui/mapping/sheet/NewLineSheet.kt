package com.nexova.survedge.ui.mapping.sheet

import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
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
fun NewLineSheet(
    state: MappingSheetState.NewLine,
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
                DragHandle()
                Spacer(modifier = Modifier.weight(1f))
                Text("New Line", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { state.onClose(); onDismiss() }) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            SheetDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text("Code: ${state.lineCode}", style = MaterialTheme.typography.bodyMedium)

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

            Text("Selected Points (${state.points.size})", style = MaterialTheme.typography.labelMedium)

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.points) { point ->
                    Text(
                        "${point.id} - P1",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { state.onAddPointFromList() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, "Add")
                Spacer(modifier = Modifier.padding(4.dp))
                Text("Add Point")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { state.onSave(state.lineCode, isClosedLine.value, state.points) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.points.size >= 2,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text("Save Line (${state.points.size} points)")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
