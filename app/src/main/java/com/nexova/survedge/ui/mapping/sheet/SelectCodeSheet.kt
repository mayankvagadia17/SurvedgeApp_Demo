package com.nexova.survedge.ui.mapping.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nexova.survedge.ui.mapping.adapter.CodeItem
import com.nexova.survedge.ui.mapping.adapter.IndicatorType
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SelectCodeSheet(
    state: MappingSheetState.SelectCode,
    onDismiss: () -> Unit
) {
    val showAddCode = remember { mutableStateOf(false) }
    val searchQuery = remember { mutableStateOf("") }
    val newCodeName = remember { mutableStateOf("") }
    val newCodeDesc = remember { mutableStateOf("") }

    // Sample codes - in real implementation, fetch from SharedPreferences/database
    val defaultCodes = listOf(
        CodeItem("PV", "Point Vertex", IndicatorType.POINT),
        CodeItem("BL", "Boundary Line", IndicatorType.LINE),
        CodeItem("CL", "Center Line", IndicatorType.LINE),
        CodeItem("ST", "Structure", IndicatorType.POINT),
    )

    ModalBottomSheet(
        onDismissRequest = {
            state.onClose()
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.32f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (!showAddCode.value) {
                // Select Code View
                SelectCodeView(
                    codes = defaultCodes,
                    searchQuery = searchQuery.value,
                    onSearchChange = { searchQuery.value = it },
                    onCodeSelect = { code ->
                        state.onCodeSelected(code.abbreviation, code.indicatorType)
                        state.onClose()
                        onDismiss()
                    },
                    onAddCodeClick = { showAddCode.value = true },
                    onClose = {
                        state.onClose()
                        onDismiss()
                    }
                )
            } else {
                // Add Code View
                AddCodeView(
                    codeName = newCodeName.value,
                    onCodeNameChange = { newCodeName.value = it },
                    codeDesc = newCodeDesc.value,
                    onCodeDescChange = { newCodeDesc.value = it },
                    onBackClick = {
                        showAddCode.value = false
                        newCodeName.value = ""
                        newCodeDesc.value = ""
                    },
                    onAddClick = {
                        showAddCode.value = false
                        newCodeName.value = ""
                        newCodeDesc.value = ""
                    },
                    onClose = {
                        state.onClose()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectCodeView(
    codes: List<CodeItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onCodeSelect: (CodeItem) -> Unit,
    onAddCodeClick: () -> Unit,
    onClose: () -> Unit
) {
    val filteredCodes = codes.filter {
        it.abbreviation.contains(searchQuery, ignoreCase = true) ||
        it.description.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DragHandle()
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Select Code",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAddCodeClick) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        SheetDivider()
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            placeholder = { Text("Search codes") },
            shape = MaterialTheme.shapes.small,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(filteredCodes) { code ->
                CodeItemRow(code = code, onClick = { onCodeSelect(code) })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AddCodeView(
    codeName: String,
    onCodeNameChange: (String) -> Unit,
    codeDesc: String,
    onCodeDescChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.Close, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("Add Code", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        SheetDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Code Name", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = codeName,
            onValueChange = onCodeNameChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g., PV") },
            shape = MaterialTheme.shapes.small
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text("Description", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = codeDesc,
            onValueChange = onCodeDescChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g., Point Vertex") },
            shape = MaterialTheme.shapes.small
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            enabled = codeName.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Add Code", color = MaterialTheme.colorScheme.onPrimary)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CodeItemRow(
    code: CodeItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(6.dp)
            ) {
                Text(code.abbreviation, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(code.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (code.indicatorType == IndicatorType.POINT) "Point" else "Line",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
