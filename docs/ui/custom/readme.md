[Index](../../index.md) > [UI](../main/readme.md)

# Custom UI Components

## SnappingHorizontalScrollView
A specialized `HorizontalScrollView` that implements paged snapping behavior.

### Purpose
Designed to provide a "ViewPager-like" interaction for horizontal content, where the scroll position automatically "snaps" to the start of a specific page when the user stops flinging or dragging.

### Logic
- **Interaction Detection:** Uses `VelocityTracker` to monitor user swipe speed.
- **Snap Selection:**
  - **Fling:** If the user performs a strong swipe (velocity > 500), it snaps in the direction of the swipe.
  - **Position:** If the user drags slowly, it snaps to the nearest page based on which child occupies more than 50% of the viewport.
- **Smooth Transition:** Utilizes `smoothScrollTo` for a clean, animated transition to the target page.

### Usage
- Used in `component_stakeout_bottom_sheet.xml` to allow users to swipe between different stakeout data views or controls.

## Source Reference
- `com.nexova.survedge.ui.custom.SnappingHorizontalScrollView`
