# MorphDrop v1.5.0 - Ember Design Overhaul, Markdown Engine and Performance

## New Features
- **Ember Design System:** Refreshed modern visual identity with warm neutral tones, theme-aware contrast, dynamic wallpaper colors (Android 12+), and responsive layouts for foldables and tablets.
- **Advanced Markdown to PDF Engine:** Complete rendering overhaul supporting GitHub Flavored Markdown tables, inline and block math formulas, syntax-highlighted code blocks, blockquotes, and interactive task checklists.
- **Quick Conversion Presets & Continue Reading:** Instant conversions with saved tool presets via long-press, paired with a home screen continue-reading card to jump straight back into your last opened document.

## Improvements
- **70% Smaller App Size:** Streamlined 64-bit architecture filtering and R8 bytecode optimization slashed release APK download size from 73MB down to ~21MB.
- **History Multi-Select & Pinning:** Organize your conversion history with bulk deletion, instant undo restoration, and pin-to-top prioritization for essential documents.
- **Adaptive Screen Padding:** Responsive, comfortable bottom spacing and edge-to-edge layout across Home, History, and Settings screens.

## Bug Fixes
- **UI & Undo Glitches:** Resolved duplicate pin indicators and fixed redundant snackbar alerts when undoing history deletions.
- **Settings Alignment:** Fixed wallpaper color toggle positioning and dynamic theme state restoration.
- **Conversion Robustness:** Closed background stream and memory leaks across PDF converters, with enhanced cooperative task cancellation.
- **Reliable Persistence:** Hardened Room database migration handling and secure package update verification.