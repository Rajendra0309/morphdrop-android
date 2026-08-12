# MorphDrop v1.1.0 - Advanced Image Tools Update

## 🚀 Enhancements
- **Workstation UI Overhaul:** Complete redesign of the Image and Document conversion tools featuring native pill-shaped input fields and a premium "Workstation" aesthetic.
- **Interactive Cropping:** Introduced a fully interactive image cropping dialog that launches automatically upon image selection.
- **Custom Color Picker:** Added a precision HSV Color Wheel for custom padding color selection when compressing and resizing images.
- **Live PDF Previews:** Native integration with `PdfRenderer` now provides real-time, high-quality thumbnails of the first page of PDF documents in the conversion configuration screen.
- **Dynamic Bounding Previews:** Live previews now dynamically mask cropped regions and respect precise EXIF and manual rotations.

## 🐛 Bug Fixes
- Fixed an `InvalidImageException` crash occurring in `ImageConverterUseCase` caused by memory-safe bounds decoding.
- Fixed a bug where auto-cropping would endlessly loop when canceling the crop dialog.
- Fixed the HSV rendering layout logic in the custom color picker where the visual gradient was rendered inverted.

## ⚙️ Maintenance
- Improved background worker error handling to gracefully record failures in the conversion history database instead of crashing silently.