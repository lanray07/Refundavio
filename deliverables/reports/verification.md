# Dashboard milestone verification

Source revision: `de7015d`. Android 0.1.0, package `app.refundavio`.

| Check | Status | Evidence |
| --- | --- | --- |
| Debug, unsigned release, and instrumentation APK builds | PASS | Gradle completed all four requested tasks (including the unit tests) successfully. Android 26 minimum, target 35; ARM and x86 APK libraries present. |
| Debug APK signing | PASS | Android apksigner verified the debug v2 signature. The release APK is intentionally unsigned. |
| Financial and urgency tests | PASS | 5 passed; 0 failed or skipped. Covers split payments, separate currency totals, closed and cancelled cases, corrections, deadline queue eligibility, locale number parsing and very large balances. |
| Android lint | PASS | 0 errors, 15 warnings, 2 hints. Warnings include dependency updates, Android API targeting and Kotlin idioms. |
| String, placeholder and plural parity | PASS | 126 strings and 2 plural groups in each bundled locale (English, Spanish, French, Arabic). |
| Android 15 tablet emulator | PASS | 2 instrumented tests on an 800 × 1280 emulator: saved a purchase, recorded a partial refund and reopened its persisted balance; verified the dashboard’s balance, deadline and follow-up sections with fictional data. |
| Physical Fire tablet compatibility | NOT TESTED | No Fire tablet was attached. Amazon’s [model/API reference](https://www.developer.amazon.com/docs/device-specs/ft-identify-tablet-devices.html) is a targeting guide, not evidence of runtime compatibility. |
| Physical-device speech recognition and text to speech | NOT TESTED | Speech depends on the installed engine and the requested locale. |
| Amazon purchasing, signing for distribution and submission | BLOCKED | Amazon product integration, sandbox test access, release signing, final store assets and approval remain required. |

Local build command: `gradlew.bat --offline --max-workers 2 testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest`.

The Windows emulator exited before reaching Android’s home screen. Both on-device tests passed in the [hosted Android run](https://github.com/lanray07/Refundavio/actions/runs/43712053397). The [English tablet preview](screenshots/dashboard-tablet-en-US.png) is a captured app screen with fictional records, visually checked against the tested dashboard. It is a QA capture, not a finished store asset.

APK checksums (SHA-256):

- Debug: `72793792f092cac6ad350bcda89b07823e096e119204310bea867f899c1b5a73`
- Unsigned release: `7a930fb98d3974b7bd7165d9c1154d53042298869253fe3fefe3931a17175c15`
