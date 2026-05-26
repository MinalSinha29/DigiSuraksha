# DigiSuraksha

DigiSuraksha is an Android-based privacy and compliance assistant designed to help users securely share screenshots and SMS content without exposing sensitive information. The app uses on-device OCR (Google ML Kit), regex-based detection, and risk classification to identify confidential data such as OTPs, UPI IDs, Aadhaar numbers, PAN numbers, card details, passwords, emails, and phone numbers.

Based on the detected content, DigiSuraksha classifies screenshots into HIGH, MEDIUM, or LOW risk levels and provides secure sharing options including masking, blurring, compliance warnings, and risk explanations. The app also includes an SMS Analyzer module for detecting fraudulent or suspicious messages.

Built using Kotlin and Android Studio, DigiSuraksha focuses on privacy-first local processing, ensuring that sensitive data remains on the device instead of being uploaded to external servers.
