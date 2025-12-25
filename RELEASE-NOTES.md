# PDF Toolkit - Play Store Release Build

## Build Information

| Property | Value |
|----------|-------|
| **Package Name** | `com.yourname.pdftoolkit` |
| **Version Name** | 1.0.1 |
| **Version Code** | 2 |
| **Min SDK** | 24 (Android 7.0) |
| **Target SDK** | 35 (Android 15) |
| **Compile SDK** | 35 |

## Release Artifacts

| File | Size | Location |
|------|------|----------|
| **APK** | 7.6 MB | `release-builds/PDFToolkit-v1.0.1-release.apk` |
| **AAB** | 8.7 MB | `release-builds/PDFToolkit-v1.0.1-release.aab` |

## Signing Information

| Property | Value |
|----------|-------|
| **Certificate DN** | CN=PDF Toolkit, OU=Mobile Apps, O=PDF Toolkit Inc, L=Bangalore, ST=Karnataka, C=IN |
| **SHA-256 Digest** | 3e870626be0933319fc301428b79585cd54bc2cc5dc78845d9ce3cf2d3cad1cc |
| **SHA-1 Digest** | 9a3a4f48f28c68f5103f402ea7fd1d305f18f6eb |

## Play Store Publishing Steps

### 1. Upload AAB to Play Console
1. Go to [Google Play Console](https://play.google.com/console)
2. Select your app or create a new one
3. Navigate to **Release > Production** (or Testing track)
4. Click **Create new release**
5. Upload `PDFToolkit-v1.0.1-release.aab`

### 2. Store Listing Requirements
- **App Title**: PDF Toolkit
- **Short Description**: (max 80 characters)
  ```
  Merge, split, compress & edit PDF files offline. Simple, secure, private.
  ```
- **Full Description**: (max 4000 characters)
  ```
  PDF Toolkit is a powerful, completely offline PDF editor for Android.
  
  ✨ FEATURES:
  • Merge PDFs - Combine multiple PDF files into one
  • Split PDF - Extract pages or split into separate files
  • Compress PDF - Reduce file size with adjustable quality
  • Rotate Pages - Rotate all pages or specific ones
  • Extract Pages - Select and extract specific pages
  • Add Security - Password protect your PDFs
  • Edit Metadata - Update title, author, and more
  • Convert Images - Turn photos into PDF documents
  
  🔒 PRIVACY FOCUSED:
  • 100% offline - No internet required
  • No cloud uploads - Your files stay on your device
  • No accounts needed - Just use and go
  
  📱 MODERN DESIGN:
  • Material Design 3 with dynamic colors
  • Dark mode support
  • Clean, intuitive interface
  ```

### 3. Graphics Assets Needed
- **App Icon**: 512x512 PNG (no transparency)
- **Feature Graphic**: 1024x500 JPG/PNG
- **Screenshots**: 
  - Phone: 2-8 screenshots, 16:9 or 9:16 aspect ratio
  - Tablet: 2-8 screenshots (optional but recommended)

### 4. Content Rating
- Complete the content rating questionnaire
- Expected rating: **Everyone** (no objectionable content)

### 5. Privacy Policy
- Required for apps with file access permissions
- Create and host a privacy policy URL

## Keystore Backup (IMPORTANT!)

⚠️ **CRITICAL**: Back up these files securely. You CANNOT update your app without them!

| File | Description |
|------|-------------|
| `pdftoolkit-release.keystore` | Release signing keystore |
| `keystore.properties` | Keystore credentials |

### Keystore Details
- **Alias**: pdftoolkit
- **Validity**: 10,000 days (~27 years)
- **Algorithm**: RSA 2048-bit

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Release AAB (for Play Store)
./gradlew bundleRelease

# Both APK and AAB
./gradlew assembleRelease bundleRelease
```

## ProGuard/R8 Notes

The release build uses R8 with:
- `isMinifyEnabled = true` (code shrinking)
- `isShrinkResources = true` (resource shrinking)

ProGuard rules for PdfBox-Android are included in `proguard-rules.pro`.

---

Generated: 2025-12-25
Version: 1.0.1
