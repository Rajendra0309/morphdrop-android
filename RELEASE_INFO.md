# MorphDrop v1.4.1 - APK Size Optimization, ChromeOS Support & Dialog Polish

## 🚀 What's New
- **50% APK Size Reduction (57 MB → 29 MB):** Retained essential high-accuracy Latin (English/European) and Devanagari (Hindi/Marathi) on-device OCR models, excluded unneeded post-quantum crypto lookup tables, and filtered unneeded architectures while preserving full 64-bit and 32-bit ARM support for Android 8.0+.
- **ChromeOS & Large Screen Compatibility:** Added explicit non-touchscreen and camera-optional declarations in manifest, enabling seamless native installation across Chromebooks, Chromeboxes, and convertibles.
- **"What's New in MorphDrop" Modal:** Introduced an in-app Material 3 modal that dynamically loads release notes directly from GitHub Releases after onboarding or when a new update is launched.
- **Polished In-App Update Dialog:** Redesigned the "Update Available" modal with symmetrical action pills ("Later" and "Update Now"), non-wrapping version transition tags, and rich Markdown release note rendering matching "What's New".
- **Settings & Update Trigger Fix:** Tapping "Check for Updates" in Settings now triggers the update dialog directly without requiring navigation back to the Home screen.
- **Smart Update Priority:** When an older version is installed, the app now prioritizes the "Update Available" dialog over "What's New", ensuring users always update first.
- **Dark Mode Settings UI Polish:** Overhauled Settings screen cards and icons with luminous tints, theme-aware translucent glass containers, and high-contrast borders for a sleek dark mode appearance.
- **Silent Startup Update Checks:** Eliminated duplicate "App is up to date" toasts during app startup and suppressed update prompts during onboarding.