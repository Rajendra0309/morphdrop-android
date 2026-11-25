# MorphDrop v1.7.0 - Audio Tools, Archives and a Smoother Home

## New Features
- **8 New Tools (42 in Total):** Audio Converter, Audio Trimmer, Video to Audio, Create ZIP, Extract ZIP, Create TAR.GZ, Extract TAR.GZ, and Extract 7z.
- **Audio Tools (WAV & M4A):** Convert audio files, extract audio from video, or trim tracks with start and end markers. Outputs are produced in WAV or M4A (AAC) formats, fully offline.
- **Archive Creation & Extraction:** Create ZIP and TAR.GZ archives from single or multiple files, and safely extract ZIP, TAR.GZ, and 7z archives into Downloads with zip-slip protection.

## Improvements
- **Share Picker:** Shared files now open the Home conversion picker with quick viewer actions (View PDF / View Markdown) on top; sharing multiple files automatically displays multi-file tools.
- **Multi-File Selection:** The Add More button on the configuration screen allows combining multiple files for tools like Create ZIP.
- **Archive Preview:** Inspect an archive's contents directly on the configuration screen before extracting.
- **Truncated Archive Warning:** The Result screen warns when an incomplete or truncated archive was only partially recovered.
- **Smoother Home:** Collapsing header driven by finger scroll deltas for smooth, jiggle-free navigation.

## Bug Fixes
- **TAR.GZ Output Name:** Fixed doubled `.tar.tar.gz` output filenames on Create TAR.GZ.
- **Archive Preview Sizes:** Fixed archive preview listing 0 B entry sizes for app-created ZIP archives.
- **Result Archive Chips:** Fixed Result screen displaying a GZ chip for `.tar.gz` files and fixed config-screen badges showing ZIP → ZIP.
- **File Format Labels:** Fixed `.tar.gz` compound extension files being labelled as GZ.
