[Index](../../index.md) > [UI](../main/readme.md)

# Splash Activity

## Overview
`SplashActivity` serves as the application's entry point, displaying a splash screen during initial launch before transitioning to the main application interface.

## Launch Sequence
1. **Edge-to-Edge:** Configures the layout to fit system windows (transparent bars).
2. **Layout:** Displays `R.layout.activity_splash`.
3. **Timer:** Maintains a 3-second (3000ms) delay to allow for branding visibility.
4. **Transition:** After the delay, it starts `MainActivity` and finishes itself, ensuring the user cannot navigate back to the splash screen.

## Source Reference
- `com.nexova.survedge.ui.splash.activity.SplashActivity`
- `layout/activity_splash.xml`
