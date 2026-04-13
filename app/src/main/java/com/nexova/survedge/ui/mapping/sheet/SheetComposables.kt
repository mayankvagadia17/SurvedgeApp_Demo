package com.nexova.survedge.ui.mapping.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DragHandle() {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(4.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(2.dp)
            )
    )
}

@Composable
fun SheetDivider() {
    Box(
        modifier = Modifier
            .height(1.dp)
            .background(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
    )
}
