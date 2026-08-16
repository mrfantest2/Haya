# Poker Vision — Google Play Data Safety Preflight

This file is a release-preparation checklist. Final Play Console answers must match the exact production build and Google Play's current form wording.

## App-owned data handling
Poker Vision does not provide user accounts, cloud sync, advertising, purchases, deposits, withdrawals, cash prizes, or real-money wagering.

The app requests CAMERA permission only for the card scanner. Camera frames used for card recognition are processed on-device. Poker Vision does not intentionally save those frames to app storage and does not upload card images to a Poker Vision/Fantest server.

Equity calculations, selected cards, hand evaluation, and Monte Carlo simulation run locally on the device.

## Third-party SDK disclosure — Google ML Kit
The production build uses `com.google.mlkit:text-recognition:16.0.1` for on-device text recognition.

Google's ML Kit disclosure documentation states that ML Kit Android SDKs may collect technical information for diagnostics and usage analytics, including device information, application information, per-installation/device identifiers, performance metrics, API configuration, feature input/output size, feature version, event types, and error codes. Google states that these collected diagnostics are encrypted in transit and are not transferred to third parties by ML Kit.

Google also states that the actual ML Kit feature inputs and outputs, such as the images/video/text supplied for recognition and the recognition results, are processed on-device and are not sent to Google servers as feature content.

## Conservative Play Console disclosure plan
Use the current Play Console wording and disclose the ML Kit diagnostics rather than claiming that the app collects absolutely no data.

Recommended categories to review/declare where Play's form maps them:
- App info and performance — diagnostics / other app performance data
- Device or other IDs — per-installation or diagnostic identifiers
- App activity / app interactions — only if the current Play form categorizes ML Kit feature-event telemetry this way

Purposes:
- Analytics
- Diagnostics / app functionality where the current form requires it

Sharing:
- No user data intentionally shared by Poker Vision with third parties
- ML Kit documentation states its listed diagnostic collection is not transferred to third parties

Security:
- Data in transit: encrypted by ML Kit using HTTPS
- Account creation: none
- User deletion: no Poker Vision account or server-side user profile exists to delete

## Camera permission declaration
Purpose text:
"Poker Vision uses the camera only when the user opens Scan Cards. Camera frames are analyzed on-device to recognize playing-card rank and suit. The camera is stopped when the scanner is closed or the app is backgrounded."

## Other Play Console declarations
- Contains ads: No
- In-app purchases: No
- Real-money gambling: No
- Financial transactions: No
- App access restrictions/login: No
- Target audience recommendation: 18+
- Category recommendation: Education
- Content: poker/card-game educational references and probability analysis; no wagering functionality

## Policy positioning
Store metadata must consistently describe Poker Vision as an educational/offline analysis tool. Do not add gambling-operator links, betting deposits, cash-out functionality, real-money contests, or claims about guaranteed winnings.
