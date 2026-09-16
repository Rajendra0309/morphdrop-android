# MorphDrop v1.4.3 - System Routing, PDF Enhancements and Core Stability

## New Features
- **Unified Share Sheet and Open-With Routing:** Direct intent routing via single task activity for seamless document viewing and conversion from external apps across all supported file types (PDF, Markdown, Excel, CSV, Images, and Text).
- **Excel Spreadsheet Preview Card:** Added dedicated preview card for Excel and CSV files prior to conversion, displaying sheet names, column dimensions, and table stats.

## Improvements
- **Wide Excel to PDF Layout Engine:** Dynamic page sizing and auto-orientation with zebra striping, proportional column width clamping, and clean multi-line row pagination across page breaks.
- **Enhanced PDF Annotation Suite:** Added undo and redo capabilities, swipe eraser deduplication, persistent bottom toolbar alignment, and atomic Room database transactions for stroke and highlight persistence.
- **Image Metadata Inspector:** Enhanced EXIF extraction with direct ContentResolver input stream fallback for scoped storage, preserving 0-meter altitude values and precise GPS coordinates.

## Bug Fixes
- **PDF Viewer Zoom Rendering and Canvas Safety:** Resolved zoom rendering clarity by rendering base preview at 2.0x display density across all zoom levels without pixelated overlay degradation, while protecting against Android Hardware Canvas 100MB limits on oversized documents.
- **Text Selection Bounds Crash:** Fixed IllegalArgumentException in PDF viewer text action popup menu by safely enforcing non-negative coordinate boundaries during deep zoom.
- **MIME Type and Intent Decoding:** Corrected redundant URI decoding in conversion configuration and added wildcard MIME filters in AndroidManifest for reliable system-wide file associations.