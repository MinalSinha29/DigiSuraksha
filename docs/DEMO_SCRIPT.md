# DigiSuraksha — Examiner Demonstration Script

This document provides a step-by-step walkthrough for demonstrating DigiSuraksha to an examiner or evaluator.

---

## 📋 Demonstration Overview

| Step # | Demo Feature | Target Screen | Key Takeaway |
| :--- | :--- | :--- | :--- |
| **1** | App Launch & Onboarding | `MainActivity` → `OnboardingActivity` | 4-screen accessible onboarding explaining privacy features. |
| **2** | Permission Transparency | `MediaPermissionHelper` Consent Screen | Custom explanation dialog shown *before* native permission prompt. |
| **3** | Main Dashboard | `HomeActivity` | Clean, card-based navigation hub & Settings toggle. |
| **4** | Screenshot Scanning & OCR | `ScreenshotScannerActivity` | On-device OCR (ML Kit), UPI QR detection, and Risk Assessment. |
| **5** | Per-Field Redaction Toggles | `ScreenshotScannerActivity` | Selective exclusion of detected sensitive fields. |
| **6** | Secure Sharing Modes | `ScreenshotScannerActivity` / Intent | Masked text, Blurred image, and Share Original override. |
| **7** | Security Event Logging | `LogsActivity` | Local audit trail of security actions. |
| **8** | Auto-Detect Screenshots | Settings Card → Notification Bar | Optional, default-OFF ContentObserver detection & tap-to-scan flow. |
| **9** | SMS Risk Analyzer | `SmsAnalyzerActivity` | Fraud detection, OTP analysis, and dialer block shortcut. |
| **10** | DPDP Compliance Mapping | `docs/DPDP_COMPLIANCE_MAPPING.md` | Architectural alignment with India's DPDP Act 2023. |

---

## 🎬 Step-by-Step Demonstration Walkthrough

### Step 1: App Launch & 4-Screen Onboarding
- **Action**: Open the DigiSuraksha app on the device/emulator. Tap **"Get Started"** on `MainActivity`.
- **Expected Visual Output**: `OnboardingActivity` opens displaying Screen 1 (*"Welcome to DigiSuraksha"*). Swipe or tap **"Next"** through Screens 2, 3, and 4.
- **Talking Points**:
  > *"DigiSuraksha begins with a clear 4-screen onboarding guide designed for non-technical users. It explains what personal data is detected, how local protection works, and highlights that Auto-Detect is optional and OFF by default."*

---

### Step 2: Permission Transparency Flow
- **Action**: On Screen 4, tap **"Get Started"** to enter `HomeActivity`. Tap **"📸 Screenshot Scanner"** card.
- **Expected Visual Output**: Custom Card-based **Media Access Required** consent dialog pops up *before* any Android system permission dialog.
- **Action**: Tap **"Continue"** on the custom consent dialog. Native Android system permission prompt appears. Tap **"Allow"**.
- **Talking Points**:
  > *"Notice how DigiSuraksha presents a custom permission consent screen explaining WHY media access is needed and guaranteeing 100% on-device processing before invoking Android's system permission prompt."*

---

### Step 3: Screenshot Scanning, OCR & Risk Assessment
- **Action**: In `ScreenshotScannerActivity`, tap **"Select Screenshot"**. Choose a test screenshot containing an Aadhaar number, UPI ID, or credit card details.
- **Expected Visual Output**:
  - Image is displayed in the preview box.
  - OCR text is extracted instantly via Google ML Kit.
  - Risk Level indicator updates (e.g. **🔴 HIGH RISK** or **🟠 MEDIUM RISK**).
  - Detected findings checkboxes appear below (e.g. `[x] Aadhaar Number`, `[x] UPI ID`).
- **Talking Points**:
  > *"Text recognition and QR scanning are performed completely on-device using ML Kit. The app automatically classifies risk into HIGH, MEDIUM, or LOW levels and identifies specific sensitive fields."*

---

### Step 4: Per-Field Redaction Toggles & Exclusions
- **Action**: Uncheck one of the detected findings checkboxes (e.g. uncheck `[ ] UPI ID`).
- **Expected Visual Output**: The unselected field is added to `excludedTypes`.
- **Action**: Tap **"Share Securely"** -> Choose **"Share Masked Text"**.
- **Expected Visual Output**: System Share Chooser opens. The preview text shows Aadhaar masked (`[REDACTED]`), while the unchecked UPI ID remains unredacted.
- **Talking Points**:
  > *"Users maintain complete control over what gets redacted. If a user unchecks a field, it is excluded from redaction while remaining sensitive fields remain protected."*

---

### Step 5: Secure Sharing Modes (Blurred Image & Share Original)
- **Action**: Tap **"Share Securely"** -> Choose **"Share Blurred Image"**.
- **Expected Visual Output**: Android Canvas graphics overlay solid/blurred bounding boxes over sensitive text areas. System Share sheet presents the sanitized image.
- **Action**: Tap **"Share Securely"** -> Choose **"⚠ Share Original Image"**.
- **Expected Visual Output**: Alert dialog confirms intent before sharing unredacted image.
- **Talking Points**:
  > *"DigiSuraksha provides three sharing options: Masked Text, Blurred Image, or Share Original. All image blurring is rendered locally on the device."*

---

### Step 6: Security Event Audit Logging
- **Action**: Navigate back to `HomeActivity` -> Tap **"📜 View Logs"**.
- **Expected Visual Output**: `LogsActivity` displays timestamps and logged events (e.g., `Screenshot → HIGH → Shared (Masked Text)`).
- **Talking Points**:
  > *"All sensitive security actions are logged locally in SharedPreferences for user transparency and auditing."*

---

### Step 7: Auto-Detect Screenshots Feature
- **Action**: On `HomeActivity`, locate the **"⚙️ Auto-Detect Screenshots"** card.
- **Expected Visual Output**: Switch is **OFF by default**.
- **Action**: Flip the switch to **ON**.
- **Action**: Press Home button on device -> Take a new screenshot (`Volume Down + Power`).
- **Expected Visual Output**: System notification appears:
  - **Title**: `"New screenshot detected"`
  - **Text**: `"Tap to scan with DigiSuraksha"`
- **Action**: Tap the notification.
- **Expected Visual Output**: `ScreenshotScannerActivity` opens directly with the newly taken screenshot pre-loaded and scanned!
- **Talking Points**:
  > *"Auto-Detect is optional and OFF by default. When enabled, a ContentObserver detects file creation and posts a notification. DigiSuraksha NEVER performs silent background OCR—the user MUST tap the notification to open and scan."*

---

### Step 8: SMS Risk Analyzer & Real-time Alerts
- **Action**: Return to `HomeActivity` -> Tap **"📩 SMS Analyzer"**.
- **Action**: Paste a test scam message (e.g., *"Your SBI account is blocked! Update KYC immediately at bit.ly/fake-link"*). Tap **"Analyze SMS"**.
- **Expected Visual Output**: Threat level updates to **CRITICAL**, highlighting urgency, phishing link, and bank impersonation flags.
- **Action**: Tap **"Block Sender"** shortcut.
- **Expected Visual Output**: Android Dialer opens pre-filled with the sender's phone number.
- **Talking Points**:
  > *"The SMS Analyzer flags phishing and scam patterns. Since Android requires user action for call blocking, DigiSuraksha opens the system Dialer so the user can block the number natively in one extra tap."*

---

### Step 9: DPDP Act 2023 Compliance Architecture
- **Action**: Refer to [docs/DPDP_COMPLIANCE_MAPPING.md](file:///c:/Users/Admin/OneDrive/Desktop/digii/DigiSuraksha/docs/DPDP_COMPLIANCE_MAPPING.md).
- **Talking Points**:
  > *"DigiSuraksha aligns with the DPDP Act 2023 through 100% local processing, explicit consent screens, default-OFF auto-detect, data minimisation, and user-driven sharing choices."*

---

## 🚫 Key Claims We Do NOT Make During Presentation

To maintain accuracy during the examination, **DO NOT** claim the following:

1. ❌ **Do NOT claim automatic background OCR**: Auto-Detect ONLY posts a notification. OCR occurs ONLY after the user taps the notification.
2. ❌ **Do NOT claim silent SMS blocking/deletion**: Android OS restricts non-default SMS apps from deleting or blocking messages silently. The app uses `detect -> notify -> user action -> dialer shortcut`.
3. ❌ **Do NOT claim cloud syncing**: DigiSuraksha has zero cloud backends; processing is 100% on-device.
4. ❌ **Do NOT claim legal certification**: The DPDP document represents technical architectural alignment, not a formal government legal certification.
