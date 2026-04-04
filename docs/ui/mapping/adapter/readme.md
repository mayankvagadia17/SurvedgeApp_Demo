# Mapping Adapters

| Adapter | List Host (RecyclerView) | Item Layout | Data Model | Click/Action Callbacks |
| :--- | :--- | :--- | :--- | :--- |
| **CodeAdapter** | `rv_codes` (in `bottom_sheet_select_code.xml`) | `item_code.xml` | `CodeItem` | `onItemClick(CodeItem)` |
| **EditPointAdapter** | `rv_points` (in `bottom_sheet_edit_line.xml`) | `item_edit_point.xml` | `LabeledPoint` | `onRemoveClick(Int)`, `onDragStart(ViewHolder)` |
| **ObjectListAdapter** | `rv_objects` (in `bottom_sheet_object_list.xml`) | `item_object_list.xml` / `item_edit_point.xml` | `ObjectListItem` | `onDelete(Item)`, `onItemClick(Item)`, `onArrowClick(Item)`, `onDragStart(ViewHolder)` |

## CodeAdapter
Used in the **Code Selection Bottom Sheet** to allow the user to select the active code for point/line collection.
- **Highlights**: The selected code is highlighted based on `selectedCodeId`.
- **Indicators**: Displays a dot icon for `POINT` type and a line icon for `LINE` type using `IndicatorType`.
- **Logic Location**: Managed via `showSelectCodeSheet` in `MappingFragmentLogic.kt`.

## EditPointAdapter
Used in the **Edit Line Bottom Sheet** to manage the order of points within a polyline.
- **Support**: Supports point removal and reordering via `ItemTouchHelper` using the drag handle.
- **Model**: Uses `LabeledPoint` which contains the coordinate and ID.
- **Logic Location**: Managed via `showEditLineSheet` in `MappingFragmentLogic.kt`.

## ObjectListAdapter
A versatile adapter used in the **Object List Bottom Sheet** to display all collected points and lines.
- **Modes**:
    - **Standard**: Displays objects with an icon, name, and date/distance info (using `item_object_list.xml`).
    - **Orderable**: Displays a simplified view with a drag handle and delete button (reusing `item_edit_point.xml`).
- **Hierarchy**: Supports nested point lists for `LINE` objects when expanded, effectively creating a tree view for line constituents.
- **States**: Manages expansion (`isExpanded`) state for lines to show their constituent points.
- **Logic Location**: Managed via `showObjectListSheet` in `MappingFragmentLogic.kt`.
