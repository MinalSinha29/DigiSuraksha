# DigiSuraksha

DigiSuraksha is a privacy-first, on-device Android security and compliance assistant designed to protect sensitive personal information before users share screenshots, images, or SMS messages.

Built with Kotlin and modern Android APIs, DigiSuraksha combines local Optical Character Recognition (OCR using Google ML Kit), barcode/QR scanning, and multi-pattern regex engines to automatically detect confidential data—such as Aadhaar numbers, PAN numbers, credit/debit card details, UPI handles, passwords, OTPs, phone numbers, and email addresses—without ever uploading data to external servers.

---

## 🌟 Key Features

### 📸 1. Screenshot Security & OCR Scanner
- **Local OCR Engine**: Uses Google ML Kit Text Recognition to process image text 100% on-device.
- **UPI QR Code Scanning**: Uses Google ML Kit Barcode Scanning to detect embedded payment URIs in QR codes.
- **Sensitive Data Detection**: Identifies 12+ sensitive data types including Aadhaar, PAN, Payment Cards, UPI handles, Passwords, OTPs, Phone numbers, Emails, Addresses, Vehicle registration numbers, and IP addresses.
- **Automated Risk Assessment**: Classifies scans into **HIGH**, **MEDIUM**, or **LOW** risk levels with actionable security tips and explanations.
- **Per-Field Redaction Toggles**: Allows users to selectively exclude specific detected fields from redaction if they explicitly choose to share them.
- **Flexible Secure Sharing**:
  - **Share Masked Text**: Replaces sensitive data with `[REDACTED]`.
  - **Share Blurred Image**: Generates a blurred version over sensitive bounding boxes using Android Canvas graphics.
  - **Share Original Image**: Allows explicit user override after confirmation.

### ⚙️ 2. Auto-Detect Screenshots (Optional & OFF by Default)
- **Non-Intrusive Detection**: Uses a background `ContentObserver` on `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` to detect when a new screenshot is saved.
- **User-Controlled Toggle**: Auto-Detect is strictly **OFF by default** and must be manually enabled by the user in Settings.
- **Explicit Notification Flow**:
  ```
  Screenshot Taken → Auto-Detect Enabled → System Notification Alert → USER TAPS NOTIFICATION → ScreenshotScannerActivity Opens & Scans
  ```
- **Privacy Assurance**: No silent background OCR or automatic image scanning occurs. The user must explicitly tap the notification before the image is scanned.

### 🛡️ 3. Permission Transparency & Consent Screen
- **Pre-Permission Explanation**: Displays DigiSuraksha's custom Card-based Consent Screen *before* triggering native Android permission prompts (`READ_MEDIA_IMAGES` on Android 13+ / `READ_EXTERNAL_STORAGE` on legacy Android).
- **Plain Language Explanations**: Clearly explains why access is needed, guarantees on-device local processing, and emphasizes user choice.

### 📩 4. SMS Risk Analyzer & Real-time Alerts
- **On-Demand SMS Analysis**: Analyzes pasted text or received SMS messages for OTP exposure, financial phishing, fake KYC scams, and suspicious links.
- **Real-Time SMS Alert Receiver**: Uses an Android `BroadcastReceiver` (`RECEIVE_SMS`) to analyze incoming messages in real time.
- **Non-Intrusive Action**: Displays a notification if suspicious SMS content is detected. Tapping the notification pre-fills the message in `SmsAnalyzerActivity`.
- **Dialer Shortcut**: Provides a "Block Sender" shortcut that opens the system Dialer so the user can block the contact natively (DigiSuraksha does not silently block or delete SMS messages).

### 📖 5. Interactive 4-Screen Onboarding
- Guides new users through DigiSuraksha's core purpose, detection capabilities, protection mechanisms, and optional Auto-Detect settings using an accessible 4-screen `ViewPager2` flow.

### 📜 6. Security Event Logs
- Records localized activity logs in `SharedPreferences` for user auditability (e.g., screenshots scanned, redaction shares, SMS alerts).

---

## 🔒 Privacy Model & DPDP Act 2023 Alignment

DigiSuraksha is engineered in alignment with India's **Digital Personal Data Protection (DPDP) Act, 2023**:

1. **100% On-Device Processing**: All OCR, QR scanning, text analysis, and image blurring are performed strictly locally. No image or text data ever leaves the user's device.
2. **Data Minimisation**: Blurring and masking features prevent accidental exposure of personal identifiers (Aadhaar, PAN, Card Numbers, OTPs).
3. **Explicit Consent & Control**: Auto-Detect defaults to **OFF**, media permissions require prior explanation, and sharing modes remain completely under user control.

For detailed legal and technical mapping, refer to [docs/DPDP_COMPLIANCE_MAPPING.md](file:///c:/Users/Admin/OneDrive/Desktop/digii/DigiSuraksha/docs/DPDP_COMPLIANCE_MAPPING.md).

---

## 🛠️ Technology Stack

- **Language**: Kotlin 1.9+
- **Minimum SDK**: API Level 24 (Android 7.0)
- **Target SDK**: API Level 36 (Android 16)
- **UI Framework**: Android XML Layouts, Material Design 3, AppCompat, ViewPager2
- **ML & Vision**:
  - `com.google.mlkit:text-recognition:16.0.0`
  - `com.google.mlkit:barcode-scanning:17.2.0`
- **Architecture Components**: `ContentObserver`, `BroadcastReceiver`, `SharedPreferences`, `FileProvider`

---

## 📱 How Auto-Detect Screenshots Works

```
   User takes screenshot
             ↓
   Is Auto-Detect ON in Settings?
             ├── NO  ──► No notification or action
             └── YES ──► ContentObserver detects new image in MediaStore
                             ↓
                         DigiSuraksha posts notification:
                         "New screenshot detected — Tap to scan with DigiSuraksha"
                             ↓
                         USER TAPS NOTIFICATION
                             ↓
                         ScreenshotScannerActivity opens with screenshot pre-loaded
                             ↓
                         On-device OCR & Risk Analysis executes
```

---

## 📶 Real-Time SMS Reliability Note

- **Detection Model**: `Detect SMS → Show Notification → User Acts`.
- **System Limitations**: On Android 8.0+ (API 26+), background execution limits and OEM-specific battery optimization software (such as on Samsung, Xiaomi, or Vivo devices) may delay or restrict background `BroadcastReceiver` execution. For optimal real-time alerts, users may need to grant "Unrestricted Battery Usage" to DigiSuraksha in Android System Settings.

---

## 📜 Documentation & Demo Resources

- **[Demo Presentation Script](file:///c:/Users/Admin/OneDrive/Desktop/digii/DigiSuraksha/docs/DEMO_SCRIPT.md)**: Examiner demonstration walkthrough step-by-step.
- **[DPDP Act 2023 Compliance Document](file:///c:/Users/Admin/OneDrive/Desktop/digii/DigiSuraksha/docs/DPDP_COMPLIANCE_MAPPING.md)**: Comprehensive mapping of application detection tags to privacy principles.
