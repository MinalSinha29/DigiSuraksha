# 🛡️ DigiSuraksha (डीजी सुरक्षा)
> **On-Device Digital Safety & Privacy Shield for Android**

[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-green.svg)](https://developer.android.com)
[![ML Kit](https://img.shields.io/badge/ML%20Kit-Vision%20%26%20Barcode-blue.svg)](https://developers.google.com/ml-kit)
[![Compliance](https://img.shields.io/badge/Compliance-DPDP%20Act%202023-orange.svg)](https://www.meity.gov.in)

---

## 📌 Problem Statement
Millions of users routinely take and share screenshots containing highly sensitive information (Aadhaar cards, PAN cards, bank account details, UPI payment QR codes, OTPs, and personal addresses) over messaging apps without realizing the financial and identity theft risks. Concurrently, citizens are inundated with sophisticated phishing SMS scams.

**DigiSuraksha** solves this by providing a 100% offline, on-device privacy guardian that automatically detects, warns, and redacts sensitive data before it gets shared.

---

## 🚀 Key Features

### 1. 🔍 Real-Time Screenshot Auto-Detection
* Background `ContentObserver` watches for new screenshot captures with **350ms scoped-storage debouncing**.
* Posts an immediate high-priority alert notification: *"🛡️ New Screenshot Detected — Tap to scan"*.
* Deep-links directly into the scanner with the image pre-loaded.

### 2. 🎯 Granular Per-Field Redaction & UPI QR Blurring
* **Google ML Kit Text Recognition** extracts on-screen text with regex & contextual keyword classification.
* **Google ML Kit Barcode Scanning** detects embedded UPI QR codes (`upi://pay?...`) and blacks out the QR bounding box.
* Interactive checkboxes allow users to choose exactly which detected entities to redact or retain.

### 3. 📩 SMS Phishing & Fraud Analyzer
* Real-time heuristic scanning of incoming SMS messages for high-confidence fraud phrases (fake lotteries, KYC suspension threats, electricity bill scams).
* Categorizes risks into `HIGH`, `MEDIUM`, and `LOW` with actionable safety tips.

### 4. 📜 Modern Security Audit Logs
* Real-time audit trail of all scans, threats detected, and sharing actions stored locally in `SharedPreferences`.
* Formatted UI with colored risk badges and timestamp tracking.

### 5. 🛡️ Permission Transparency & DPDP Act 2023 Compliance
* Plain-language pre-consent screen shown **before** Android system permission dialogs.
* **100% On-Device Processing**: No images, SMS messages, or personal data are ever uploaded to any cloud server.

---

## 🏗️ Architecture & Tech Stack

* **Language**: Kotlin
* **UI**: AndroidX Material Components, ViewPager2, CardView, RecyclerView
* **ML / Vision**: Google ML Kit (Text Recognition Latin & Barcode Scanning)
* **Storage**: Scoped Storage `MediaStore` APIs, Android `ContentObserver`, `SharedPreferences`
* **Architecture**: Clean Single-Responsibility Activities with dedicated observers & heuristic analyzers

---


## ⚙️ Installation & Running

1. Clone this repository:
   ```bash
   git clone https://github.com/<MinalSinha29>/DigiSuraksha.git