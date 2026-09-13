# MorphDrop v1.4.2 - Hotfix: Startup Crash Fix & Performance Optimization

## 🚀 What's New
- **Critical Hotfix for App Startup Crash:** Resolved `java.lang.VerifyError` caused by legacy Apache POI / XMLBeans classes failing Android ART runtime bytecode verification during application launch.
- **Native Android Excel Processing:** Replaced heavy Apache POI desktop library with a 100% lightweight, native Android OOXML parser (`XmlPullParser` + `ZipInputStream` + `PdfDocument`), with zero third-party dependencies and no Apache POI or XMLBeans runtime dependency.
- **Further APK Size Reduction (29 MB → 25.8 MB):** Completely stripped Apache POI and XMLBeans bytecode, shedding another 3.2 MB from the standalone release APK.
- **ChromeOS & Large Screen Compatibility:** Full compatibility with Chromebooks, Chromeboxes, and non-touchscreen convertibles.
- **Dynamic "What's New" & Polished Update Dialogs:** Material 3 release notes fetched directly from GitHub Releases, streamlined dialog triggers, and responsive layout polish across dark and light themes.