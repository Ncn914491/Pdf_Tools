# Changelog

## Version 1.2.6 (2026-01-05)

### 🐛 Critical Bug Fixes
- **Fixed external PDF opening crash** - App no longer crashes when opening PDFs from Gmail, Drive, or other apps
  - Root cause: Navigation was trying to use non-existent parameterized route
  - Solution: Navigate to `pdf_viewer_direct` route which properly handles external intents

- **Fixed file visibility in file manager** - Created PDFs now appear immediately in file managers
  - Changed MediaStore notification to use MediaScannerConnection for better reliability
  - PDF Toolkit folder is now visible in Documents directory

- **Fixed Gmail restriction** - Bug report and feature request now only use Gmail app
  - Added `setPackage("com.google.android.gm")` to restrict to Gmail only
  - Shows helpful toast message if Gmail is not installed (no crash)

### ✨ New Features
- **In-app Open Source Licenses** - View all third-party licenses directly in the app
  - No need to open external web browser
  - Beautiful scrollable dialog with all library information
  - Includes Apache License 2.0 full text

### 🔧 Improvements
- Added debug logging to MainActivity for better external PDF opening diagnostics
- Updated copyright year to 2026
- Improved MediaStore file scanning for Android 10+ devices

### 📝 Code Quality
- Removed 18 unnecessary documentation .md files
- Cleaned up codebase for better maintainability
- Updated version strings in email templates

### 🔄 Version Updates
- Version Code: 10 → 11
- Version Name: 1.2.5 → 1.2.6

---

## Version 1.2.5 (Previous Release)

### Bug Fixes
- Removed 50-page limit in PDF viewer
- Fixed password-protected PDF handling
- Changed image tools default from COMPRESS to RESIZE
- Added clear history button to Files screen
- Updated organize pages labels for clarity

---

For full release history, see previous versions in git history.
