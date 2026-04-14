package com.nexova.survedge.ui.mapping.sheet

import androidx.compose.runtime.Composable
import com.nexova.survedge.ui.theme.SurvedgeTheme

@Composable
fun MappingSheetsHost(
    state: MappingSheetState,
    onDismiss: () -> Unit
) {
    SurvedgeTheme {
        when (state) {
            is MappingSheetState.None -> Unit
            is MappingSheetState.CollectPoint -> CollectPointSheet(state, onDismiss)
            is MappingSheetState.LineSegment -> LineSegmentSheet(state, onDismiss)
            is MappingSheetState.EditLine -> EditLineSheet(state, onDismiss)
            is MappingSheetState.EditPoint -> EditPointSheet(state, onDismiss)
            is MappingSheetState.NewLine -> NewLineSheet(state, onDismiss)
            is MappingSheetState.NewPoint -> NewPointSheet(state, onDismiss)
            is MappingSheetState.SelectCode -> SelectCodeSheet(state, onDismiss)
            is MappingSheetState.ObjectList -> ObjectListSheet(state, onDismiss)
            is MappingSheetState.Stakeout -> StakeoutSheet(state, onDismiss)
            is MappingSheetState.LineStakeout -> LineStakeoutSheet(state, onDismiss)
            is MappingSheetState.ConfirmDialog -> ConfirmDialogSheet(state, onDismiss)
            is MappingSheetState.DeleteLineOptions -> DeleteLineOptionsSheet(state, onDismiss)
            is MappingSheetState.NewProject -> NewProjectSheet(state, onDismiss)
            is MappingSheetState.ProjectOptions -> ProjectOptionsSheet(state, onDismiss)
        }
    }
}
