# Refundavio milestone and release scope

## Product decision

Deadline queues and refund tracking are already offered by [ReturnTracker](https://www.returntracker.app/features) and [RefundTrail](https://apps.apple.com/gb/app/refundtrail-return-tracker/id6818855545). Refundavio's proposed position is a tablet-friendly, account-free recovery dashboard that immediately separates money still owed, verified-by-user payments and time-sensitive work across currencies. This is a positioning hypothesis; the name still needs formal trademark and marketplace clearance.

The brief proposes an optional one-time Amazon Premium unlock. Keep deadlines, existing records, basic balances and purchase history available without payment. Test price and limits with users before introducing a paywall. No Amazon purchasing or real-money entitlements are active in this milestone.

Amazon Appstore distribution should target Fire tablets. [Amazon ended its Appstore support for non-Fire Android mobile devices](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program). The native APK can be tested on compatible Android devices, but a separate distribution decision is required for those devices.

## Privacy and security

The APK has no internet permission or embedded service keys. Records use the app-private Room database, with Android backup disabled. The Android document picker grants read access only to the selected attachment. Removing an attachment from a record does not delete the provider’s file. Speech is optional and delegated to the installed Android service. Drafts are generated locally and copied only by explicit user action. Database deletion of all purchase records requires confirmation. No account, analytics, merchant integration or automatic messaging is configured.

## Master prompt review, 16 November 2026

The Amazon draft contains signed **0.2.6**, targets 12 Fire tablet models, and is set to all available countries and regions. The review page displays the reviewer instructions and an unchecked export certification. Chinese, Hindi and Korean are now selected under Language Support. Amazon's Language Support selector has no entries for Indonesian, Swedish or Turkish. The in-app picker includes those languages. Storefront localization and distribution are configured separately. [Amazon international distribution](https://developer.amazon.com/docs/app-submission/distribute-your-app-internationally.html)

The **0.2.7** local candidate adds reports and purchase archiving. It needs final review before replacing the draft binary. Reports collect ledger information into a single file with sections for purchases, refund recovery, confirmed refunds, pending balances, return deadlines, follow-ups, contact history and warranties. The app requires confirmation and a destination selected through the system file picker. Reports omit attachment URIs and files. Archive from purchase details and restore under **Purchases → Show → Archived purchases**. Archived records and confirmed refunds remain available in history and reports. Archiving pauses alerts and leaves purchases with outstanding balances active.

The store asset package contains two original icon sizes and [66 validated Fire tablet screenshots](../store-assets/screenshots/manifest.csv), three each for 22 language and region variants. Turkish and Indonesian now have their own README galleries.

### Candidate verification

Run `gradlew.bat --offline --max-workers 2 -PrefundavioQa testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug` using JDK 17 and Android SDK 35. The build produced the [QA debug APK](../../app/build/outputs/apk/debug/app-debug.apk) and the unsigned release. The release was signed with the established Refundavio certificate, verified for APK signature schemes v2 and v3, and saved locally as `D:\Refundavio Amazon Review 0.2.7\Refundavio-0.2.7-Amazon.apk`. Its SHA-256 is `CAEDCD05E15816E2AF9117BBBAFE1C40E98B46ACB70D4C69C23810C2B0A97664`. The candidate manifest contains notification and reboot permissions and has no internet or microphone permission.

| Master prompt area | Status | Evidence or remaining work |
| --- | --- | --- |
| Recovery dashboard and local ledger | PASS | Existing decimal-safe totals, partial payments, currency-separated balances, return deadlines, follow-up queues and warranty view. [Verification](verification.md) |
| Background reminders | PASS | Return and merchant follow-up alerts were exercised with the app closed, screen off and after reboot on the physical Fire tablet. [Reminder report](reminders.md) |
| Candidate report generation and database migration | PASS | All 13 unit tests passed. On Fire tablet `KFTRWI` (Android API 28, 1920 × 1200 landscape), two instrumentation tests verified CSV and text report amounts, opening a multipage PDF, upgrade from the version-one schema, and archive and restore effects on reminder eligibility. Lint reported zero errors, 16 warnings and two hints. |
| Full export journey on the Fire tablet | BLOCKED | The Korean report confirmation opened the device's Downloads picker. Another app took the foreground before the exported file could be verified in Downloads. |
| Core workflows beyond the ledger | FAIL | The acceptance review found no multi-item order model, independent merchant directory, multiple typed evidence attachments or full parcel, return-request and warranty histories. |
| Onboarding and optional protection | FAIL | The acceptance review found no dedicated onboarding, app lock or encrypted backup/restore. |
| Reminder breadth | FAIL | Dedicated warranty, parcel dispatch and merchant response alert types, quiet hours and recurrence are missing. |
| Voice | BLOCKED | Fire tablet `KFTRWI` at Android API 28 exposed no speech-recognition activity. It reported IVONA and Pico TTS services. Per-language synthesis and actual speech recognition require compatible engines and physical tests. The app supplies text and touch shortcuts for balances, deadlines and follow-ups. |
| AI and templates | FAIL | One editable local follow-up template exists. The full template catalogue, structured intake, duplicate detection, correspondence summaries and evidence checklist are missing. Optional remote AI would require a configured service and consent flow. |
| Localization | PASS | All 17 translated Android resource bundles match English for resource keys and placeholders. Arabic RTL and both Chinese scripts have physical Fire tablet evidence in prior verification. |
| Regional localization and automation | FAIL | The brief calls for dedicated Canadian French and Latin American Spanish in-app variants, broader human review, per-locale speech verification, and a configured translation and screenshot pipeline. |
| Store art and screenshots | FAIL | The package has original tablet PNGs, headers and localized descriptions. Eight scenarios for every approved listing locale, phone variants, a full creative set and complete final draft coverage are missing. Amazon's minimum is three screenshots. [Amazon test criteria](https://developer.amazon.com/docs/app-testing/test-criteria.html) |
| Premium and Amazon purchasing | BLOCKED | Billing integration and sandbox validation remain. Production verification requires a configured entitlement SKU, Amazon test access and secure server-side receipt verification. No paid entitlement is currently advertised or activated. [Amazon IAP implementation](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html) · [Amazon IAP testing](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html) |
| Privacy and release validation | FAIL | The privacy policy covers optional speech and export destinations. Finish accessibility testing, broader Fire compatibility tests and release verification after the remaining features ship. Formal brand clearance and export certification require the owner's review. |
| Architecture requirements | FAIL | The app uses Room and explicit migrations, along with AlarmManager and shared preferences. DataStore, WorkManager and the independent order, merchant and return modules specified in the brief remain unevaluated for this candidate. |

**Release gate through the full master prompt: FAIL.** The draft remains available for review.
