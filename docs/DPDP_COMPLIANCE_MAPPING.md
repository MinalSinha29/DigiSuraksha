# DigiSuraksha — DPDP Act 2023 Compliance Mapping & Privacy Architecture

## 1. Purpose & Overview

DigiSuraksha is an on-device Android security application designed to protect personal data and sensitive information contained within screenshots, images, and text messages before users share them.

The Digital Personal Data Protection (DPDP) Act, 2023 establishes guidelines for handling digital personal data in India, emphasizing principles such as data minimisation, explicit consent, user control, and privacy-by-design. DigiSuraksha aligns with these core principles by empowering users to identify, redact, and control sensitive personal information locally on their device before sharing.

---

## 2. Detected Data Types & Privacy Relevance Mapping

The following table maps the actual detection tags and entity classifications implemented in DigiSuraksha to personal data definitions and privacy principles referenced under the DPDP Act 2023 framework:

| DigiSuraksha Data Type / Tag | What It Represents | Why It Is Sensitive | DPDP Act 2023 Relevance |
| :--- | :--- | :--- | :--- |
| **Aadhaar Number** | 12-digit Unique Identification Number issued by UIDAI (`aadhaarRegex`) | Direct government identifier that uniquely identifies an individual. | Personal data relating to individual identity; risk of identity theft and unauthorized profiling. |
| **PAN Card Number** | 10-character Permanent Account Number issued by Income Tax Dept (`panRegex`) | Financial identity credential linked to tax records and bank accounts. | Financial personal data; requiring strict protection against financial fraud. |
| **Payment Card Details** | 16-digit Debit / Credit Card Numbers (`cardRegex`, `cardKeywords`) | Access credential for financial accounts and payment transactions. | Financial personal data; critical risk of unauthorized monetary transactions if exposed. |
| **UPI Handles & Payment QRs** | VPA addresses (e.g. `user@upi`) and pre-set Payment QRs (`upiRegex`, `detectUpiQrCode`) | Direct payment routing addresses and financial identifiers. | Personal financial data; risk of unauthorized payment requests or impersonation. |
| **Passwords & Passcodes** | Text labels indicating passwords or security codes (`passwordKeywordRegex`) | Authentication secrets for accessing personal accounts and services. | Confidential security credential; compromise leads to account takeover. |
| **One-Time Passwords (OTPs)** | Short-lived authentication codes (`otpKeywords`) | Real-time secondary authentication factor for banking and logins. | High-risk authentication credential; protection prevents unauthorized account access. |
| **Phone Numbers** | 10-digit Indian Mobile Numbers (`phoneRegex`) | Primary communication identifier and contact detail. | Personal data that directly identifies or contacts an individual. |
| **Email Addresses** | Standard electronic mail addresses (`emailRegex`) | Digital contact address and account username identifier. | Personal data; risk of phishing, spamming, and account correlation. |
| **Physical Address & Pincodes** | Street, locality keywords, and 6-digit Pincodes (`addressKeywordRegex`, `pincodeRegex`) | Geographical location and physical residence details. | Location-related personal data; risk of physical stalking or privacy violation. |
| **Vehicle Registration** | Indian motor vehicle license plate numbers (`vehicleRegex`) | Vehicle ownership credential linked to official transport registries. | Personal data that identifies vehicle ownership and movement history. |
| **IP Addresses** | IPv4 network protocol addresses (`ipRegex`) | Network location identifier assigned to user device connections. | Technical personal identifier under digital privacy guidelines. |
| **Fraud & Phishing Signals** | Message patterns indicating scams, prize claims, or fake KYC (`fraudHighPhrases`) | Suspicious or deceptive content designed to defraud the user. | User safety alert feature helping prevent cyber fraud and financial exploitation. |

---

## 3. Privacy-by-Design Relevance

DigiSuraksha implements a **Privacy-by-Design** architecture:

- **100% On-Device Processing**: Text recognition (OCR), QR scanning, regex entity detection, and image redaction/blurring are executed strictly on the user's local device.
- **Zero Remote Data Transmission**: No user screenshots, extracted text, or detected personal data are transmitted to external servers or cloud services.
- **Data Minimisation**: Redaction options (blurring sensitive bounding boxes or replacing sensitive text with masked characters) allow users to share only necessary information.

---

## 4. User Control & Consent Architecture

DigiSuraksha ensures the user retains complete decision-making authority over their data:

1. **User-Driven Sharing Choice**:
   - Share Masked Text (replaces sensitive entities with `[REDACTED]`).
   - Share Blurred Image (blurs bounding boxes of sensitive fields).
   - Share Original Image (user explicitly overrides redaction).
2. **Selective Per-Field Exclusions**:
   - Users can selectively exclude specific detected fields from redaction if they explicitly choose to share them.
3. **Optional Auto-Detect Feature**:
   - The Auto-Detect Screenshots feature defaults strictly to **OFF**.
   - It is user-controlled via a toggle in Settings.
   - It requires explicit user interaction (tapping the notification) before any image is opened for scanning.

---

## 5. Permission Transparency

Before requesting Android runtime permissions (`READ_MEDIA_IMAGES` / `READ_EXTERNAL_STORAGE`), DigiSuraksha displays a **Custom Consent Screen** that explains in plain language:

- **Why permission is needed**: To access screenshots for sensitive data checking before sharing.
- **Privacy Assurance**: Clarifies that processing takes place 100% on-device.
- **User Choice**: Reassures the user that permission grant is voluntary.

The native Android system permission prompt is only requested **after** the user taps "Continue" on the DigiSuraksha consent screen.

---

## 6. Auto-Detect Privacy Model

The Auto-Detect feature follows a strict non-intrusive workflow:

```
   User takes screenshot
             ↓
   Auto-Detect enabled in Settings?
             ├── NO  ──► No action taken
             └── YES ──► ContentObserver detects file event in MediaStore
                             ↓
                         Post Notification: "New screenshot detected - Tap to scan"
                             ↓
                         USER TAPS NOTIFICATION
                             ↓
                         ScreenshotScannerActivity opens with image
                             ↓
                         Image scanned & analyzed locally on device
```

**Privacy Guarantee**: The app does **NOT** perform background OCR or automatic scanning before the user taps the notification.

---

## 7. Limitations & Compliance Disclaimer

- This document serves as a technical compliance mapping of DigiSuraksha's architecture against privacy principles outlined in the Digital Personal Data Protection (DPDP) Act, 2023.
- This mapping is created for project assessment and technical demonstration purposes and does **not** constitute formal legal certification, legal advice, or a guarantee of compliance with the DPDP Act 2023.
