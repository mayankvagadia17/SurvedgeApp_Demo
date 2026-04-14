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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
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
fun LineSegmentSheet(
    state: MappingSheetState.LineSegment,
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DragHandle()
                Spacer(modifier = Modifier.weight(1f))
                Text("Segment Details", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { state.onClose(); onDismiss() }) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            SheetDivider()
            Spacer(modifier = Modifier.height(12.dp))

            if (state.isLineDetails && state.lineOverlay != null) {
                Text("Line Code", style = MaterialTheme.typography.labelMedium)
                Text("L1", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { state.onEdit() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, "Edit")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit Line")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { state.onLineStakeout() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Stakeout Line")
                }
            } else if (state.point != null) {
                Text("Point ID", style = MaterialTheme.typography.labelMedium)
                Text(state.point.id, style = MaterialTheme.typography.bodyMedium)

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { state.onEdit() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { state.onDelete() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = { state.onStakeout() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Stakeout")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
