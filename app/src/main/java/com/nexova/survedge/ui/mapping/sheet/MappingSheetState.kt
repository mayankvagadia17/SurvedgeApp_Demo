package com.nexova.survedge.ui.mapping.sheet

import com.nexova.survedge.ui.mapping.adapter.IndicatorType
import com.nexova.survedge.ui.mapping.overlay.ClickablePolylineOverlay
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint

sealed class MappingSheetState {
    object None : MappingSheetState()

    data class CollectPoint(
        val nextPointId: String = "",
        val pointType: String = "P",
        val note: String = "",
        val isLineMode: Boolean = false,
        val linePointCount: Int = 0,
        val onSavePoint: (String, String, String) -> Unit = { _, _, _ -> },
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class LineSegment(
        val point: LabeledPoint? = null,
        val lineOverlay: ClickablePolylineOverlay? = null,
        val isLineDetails: Boolean = false,
        val onEdit: () -> Unit = {},
        val onDelete: () -> Unit = {},
        val onStakeout: () -> Unit = {},
        val onLineStakeout: () -> Unit = {},
        val onContinueCollect: () -> Unit = {},
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class EditLine(
        val lineOverlay: ClickablePolylineOverlay? = null,
        val lineCode: String = "",
        val isClosedLine: Boolean = false,
        val points: List<LabeledPoint> = emptyList(),
        val onSave: (Boolean, List<LabeledPoint>) -> Unit = { _, _ -> },
        val onSelectCode: () -> Unit = {},
        val onAddPoint: () -> Unit = {},
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class EditPoint(
        val point: LabeledPoint? = null,
        val pointCode: String = "",
        val note: String = "",
        val onSave: (String, String) -> Unit = { _, _ -> },
        val onSelectCode: () -> Unit = {},
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class NewLine(
        val lineCode: String = "",
        val isClosedLine: Boolean = false,
        val points: List<LabeledPoint> = emptyList(),
        val onSave: (String, Boolean, List<LabeledPoint>) -> Unit = { _, _, _ -> },
        val onSelectCode: () -> Unit = {},
        val onAddPointFromList: () -> Unit = {},
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class NewPoint(
        val longitude: String = "",
        val latitude: String = "",
        val elevation: String = "",
        val coordinateSystem: String = "Global",
        val onSave: (String, String, String, String) -> Unit = { _, _, _, _ -> },
        val onSelectCoordinateSystem: () -> Unit = {},
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class SelectCode(
        val onlyPoints: Boolean = false,
        val onlyLines: Boolean = false,
        val onCodeSelected: (String, IndicatorType) -> Unit = { _, _ -> },
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class ObjectList(
        val points: List<LabeledPoint> = emptyList(),
        val lines: List<ClickablePolylineOverlay> = emptyList(),
        val isLineCreationMode: Boolean = false,
        val onItemSelected: (Any) -> Unit = {},
        val onAddPoint: () -> Unit = {},
        val onAddLine: () -> Unit = {},
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class Stakeout(
        val targetPoint: LabeledPoint? = null,
        val northSouth: Double = 0.0,
        val eastWest: Double = 0.0,
        val cutFill: Double = 0.0,
        val distance: Double = 0.0,
        val azimuth: Double = 0.0,
        val inTolerance: Boolean = false,
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class LineStakeout(
        val lineCode: String = "",
        val lineName: String = "",
        val northSouth: Double = 0.0,
        val eastWest: Double = 0.0,
        val cutFill: Double = 0.0,
        val distance: Double = 0.0,
        val azimuth: Double = 0.0,
        val inTolerance: Boolean = false,
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class ConfirmDialog(
        val title: String = "Confirm",
        val message: String = "",
        val onYes: () -> Unit = {},
        val onNo: () -> Unit = {},
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class DeleteLineOptions(
        val lineOverlay: ClickablePolylineOverlay? = null,
        val onKeepPoints: () -> Unit = {},
        val onDeleteAll: () -> Unit = {},
        val onCancel: () -> Unit = {},
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class NewProject(
        val onProjectCreated: (String, String?) -> Unit = { _, _ -> },
        val onClose: () -> Unit = {}
    ) : MappingSheetState()

    data class ProjectOptions(
        val projectId: Long = 0L,
        val onImport: () -> Unit = {},
        val onExport: () -> Unit = {},
        val onClose: () -> Unit = {}
    ) : MappingSheetState()
}
