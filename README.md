<div align="center">
  <!-- Replace with actual logo path when available -->
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" alt="MorphDrop Logo" width="120"/>

  <h1>MorphDrop</h1>

  <p><strong>Drop. Transform. Done.</strong></p>
  <p>A modern, 100% offline Android file converter built with privacy and simplicity at its core.</p>

  [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
  [![Platform](https://img.shields.io/badge/Platform-Android-green.svg)]()
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0.x-purple.svg)]()
  [![Compose](https://img.shields.io/badge/Compose-BOM_2024.02.00-blue.svg)]()
</div>

---

## Overview

**MorphDrop** is an offline-first utility app that allows you to convert documents and images directly on your device. No internet required, no data collection, and no file size limits. It leverages powerful libraries like Apache PDFBox and Apache POI to handle complex file transformations locally and securely.

---

## Core Features

- 🛡️ **100% Offline** — Zero internet permissions required. Files never leave your device.
- ⚡ **Core Conversions** — PDF to Images, Images to PDF, Word to PDF, and more.
- 📄 **Advanced PDF Tools** — Merge, Split, Compress, and Password protect PDF files (Coming Soon).
- ✨ **Modern UI** — Built with Jetpack Compose and Material 3 with Dynamic Color support.
- 🔄 **Background Processing** — Conversions run in the background using WorkManager.
- 📱 **Material You** — Dynamic theming based on your device's wallpaper.

---

## Supported Conversions

### Document Conversions
| From | To |
| :--- | :--- |
| PDF | Images (PNG, JPG) |
| Images (PNG, JPG, WebP, BMP) | PDF |
| Word (DOCX) | PDF |
| PDF | Word (DOCX) |
| PowerPoint (PPTX) | PDF |
| Excel (XLSX) | PDF |

### Image Conversions
| From | To |
| :--- | :--- |
| PNG | JPG, WebP, BMP |
| JPG | PNG, WebP, BMP |
| WebP | PNG, JPG, BMP |
| BMP | PNG, JPG, WebP |

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **UI** | Jetpack Compose + Material 3 (Material You) |
| **Language** | Kotlin 2.0.x |
| **Architecture** | MVVM + Clean Architecture |
| **DI** | Hilt (Dagger Hilt) |
| **Database** | Room (Local History & Favorites - Coming Soon) |
| **Background** | WorkManager |
| **PDF Engine** | Apache PDFBox Android |
| **Office Docs** | Apache POI |
| **Image Proc** | Coil + Android Bitmap API |

---

## Project Structure

```
app/src/main/java/com/morphdrop/app/
├── di/                    # Hilt Dependency Injection modules
├── domain/
│   ├── model/             # Data models (ConversionType, FileType, etc.)
│   └── usecase/           # Business logic use cases
├── ui/
│   ├── components/        # Reusable UI components
│   ├── navigation/        # Navigation setup (NavGraph, Screen)
│   ├── screens/           # Screen composables & ViewModels
│   └── theme/             # Material You theming
└── util/                  # Utilities (FileHelper)
```

---

## Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2) or newer
- JDK 17+
- Android Device/Emulator (API 26+)

### Building from Source
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/MorphDrop_Android.git
   ```
2. Open the project in Android Studio.
3. Build the project using the Gradle wrapper:
   ```bash
   ./gradlew assembleDebug
   ```
4. Install the debug APK on your device/emulator.

---

## License

This project is licensed under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for details.

---

<div align="center">
  Built with ❤️ for privacy and efficiency.
</div>