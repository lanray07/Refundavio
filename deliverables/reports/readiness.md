# Refundavio milestone and release scope

## Product decision

Deadline queues and refund tracking are already offered by [ReturnTracker](https://www.returntracker.app/features) and [RefundTrail](https://apps.apple.com/gb/app/refundtrail-return-tracker/id6818855545). Refundavio's proposed position is a tablet-friendly, account-free recovery dashboard that immediately separates money still owed, verified-by-user payments and time-sensitive work across currencies. This is a positioning hypothesis; the name still needs formal trademark and marketplace clearance.

The brief proposes an optional one-time Amazon Premium unlock. Keep deadlines, existing records, basic balances and purchase history available without payment. Test price and limits with users before introducing a paywall. No Amazon purchasing or real-money entitlements are active in this milestone.

Amazon Appstore distribution should target Fire tablets. [Amazon ended its Appstore support for non-Fire Android mobile devices](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program). The native APK can be tested on compatible Android devices, but a separate distribution decision is required for those devices.

## Privacy and security

The APK has no internet permission or embedded service keys. Records use the app-private Room database, with Android backup disabled. The Android document picker grants read access only to the selected attachment. Removing an attachment from a record does not delete the provider’s file. Speech is optional and delegated to the installed Android service. Drafts are generated locally and copied only by explicit user action. Database deletion of all purchase records requires confirmation. No account, analytics, merchant integration or automatic messaging is configured.

## Master prompt review, 16 November 2026

At the initial review, the Amazon draft contained signed **0.2.6**, targeted 12 Fire tablet models, and was set to all available countries and regions. Chinese, Hindi and Korean were selected under Language Support. Amazon's Language Support selector has no entries for Indonesian, Swedish or Turkish. The in-app picker includes those languages. Storefront localization and distribution are configured separately. [Amazon international distribution](https://developer.amazon.com/docs/app-submission/distribute-your-app-internationally.html)

The **0.2.7** local candidate adds reports and purchase archiving. It needs final review before replacing the draft binary. Reports collect ledger information into a single file with sections for purchases, refund recovery, confirmed refunds, pending balances, return deadlines, follow-ups, contact history and warranties. The app requires confirmation and a destination selected through the system file picker. Reports omit attachment URIs and files. Archive from purchase details and restore under **Purchases → Show → Archived purchases**. Archived records and confirmed refunds remain available in history and reports. Archiving pauses alerts and leaves purchases with outstanding balances active.

The store asset package contains two original icon sizes and [66 validated Fire tablet screenshots](../store-assets/screenshots/manifest.csv), three each for 22 language and region variants. Turkish and Indonesian now have their own README galleries.

### Candidate verification

Run `gradlew.bat --offline --max-workers 2 -PrefundavioQa testDebugUnitTest assembleDebug assembleRelease assembleDebugAndroidTest lintDebug` using JDK 17 and Android SDK 35. The build produced the [QA debug APK](../../app/build/outputs/apk/debug/app-debug.apk) and the unsigned release. The release was signed with the established Refundavio certificate, verified for APK signature schemes v2 and v3, and saved locally as `D:\Refundavio Amazon Review 0.2.7\Refundavio-0.2.7-Amazon.apk`. Its SHA-256 is `CAEDCD05E15816E2AF9117BBBAFE1C40E98B46ACB70D4C69C23810C2B0A97664`. The candidate manifest contains notification and reboot permissions and has no internet or microphone permission.

| Master prompt area | Status | Evidence or remaining work |
| --- | --- | --- |
| Recovery dashboard and local ledger | PASS | Existing decimal-safe totals, partial payments, currency-separated balances, return deadlines, follow-up queues and warranty view. [Verification](verification.md) |
| Background reminders | PASS | Return and merchant follow-up alerts were exercised with the app closed, screen off and after reboot on the physical Fire tablet. [Reminder report](reminders.md) |
| Candidate report generation and database migration | PASS | All 13 unit tests passed. On Fire tablet `KFTRWI` (Android API 28, 1920 × 1200 landscape), two instrumentation tests verified CSV and text report amounts, opening a multipage PDF, upgrade from the version-one schema, and archive and restore effects on reminder eligibility. Lint reported zero errors, 16 warnings and two hints. |
| Full export journey on the Fire tablet | PASS | The retry saved the Korean CSV through the Fire tablet's Android Downloads picker. The exported file contained 29 parsed data rows with consistent columns and the fictional QA purchase. The test copy was removed after verification. |
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

## 0.2.8 candidate follow-up

Amazon saved the signed **0.2.8** APK as version code 10. The draft still targets 12 Fire tablets, includes the reviewer instructions, and remains unsubmitted with export certification unchecked.

| Area | Status | Evidence or remaining work |
| --- | --- | --- |
| Build and signing | PASS | The offline debug, release and instrumentation builds passed with all 17 unit tests. Android lint reported zero errors, 28 warnings and three hints. The signed APK uses the established Refundavio certificate and signing schemes v2 and v3. SHA-256 `3426E384AF209E84AAF8B281AF08DDFBF6F861BCC86C6BA3AC97B7FE0E57257F`. The local output is `D:\Refundavio Amazon Review 0.2.8\Refundavio-0.2.8-Amazon.apk`. |
| Setup and currency | PASS | The optional setup presents language, currency, reminders, privacy and a first-purchase action. The Fire tablet test changed the preferred currency and verified that the next purchase used it. |
| System file export | PASS | The 0.2.8 candidate saved a Korean CSV to the Fire tablet's Downloads folder. The test parsed 34 data rows and removed the fictional export after verification. |
| Regional resources | PASS | English has device-region and explicit UK, US and Australian choices. Canadian French and Latin American Spanish use validated regional overrides. The Fire tablet verified locale resolution for `fr-CA`, `es-419` and `es-MX`. All 17 full translations passed validation with 186 strings and three plural groups each. |
| Warranty and quiet hours | PASS | The Fire tablet posted an opt-in warranty notification with generic text and private lock-screen visibility. It also posted the return, follow-up and warranty reminders at 02:12:46 GMT for a 02:12:41 GMT schedule, after the QA process had exited. Unit tests verify expiry reminders, archived-record exclusion, overnight deferral and same-day quiet hours. |
| Orders and merchant data | PASS | The Fire tablet created a second item from an order, carried over the merchant, currency and reference, and preserved independent refund balances. The migration test upgraded the original database to schema 4 with its purchase intact and verified saving a merchant profile. The physical Fire tablet merchant directory opened a linked fictional purchase. |
| Touch shortcut fallback | PASS | A Fire tablet test verified that the Speak control is disabled when no recognition activity is present and that the touch shortcut opens the return deadline queue. Actual speech recognition and per-language spoken output remain unverified. |
| Amazon entitlement sandbox | PASS | The one-time Premium SKU is saved as a draft. The Fire tablet purchase and restore passed, and Amazon RVS Cloud Sandbox verified both receipts and rejected an invalid one. [Premium sandbox verification](amazon-premium-sandbox.md) |
| Amazon production billing | BLOCKED | The release APK still requires a customer purchase flow, entitlement delivery, secure production receipt verification and live testing. [Amazon IAP testing](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html) |
| Remaining feature and asset scope | FAIL | Typed multi-document evidence, detailed parcel and warranty histories, parcel and merchant-response reminder types, app lock, encrypted backup and restore, and the full local assistant remain incomplete. The eight-shot store galleries, accessibility review and broader device validation are still open. |

**Submission status: FAIL against the full master prompt.**

The signed 0.2.9 candidate in the Amazon draft adds the customer purchase and restore flow, the Premium report bundle, and server-side receipt verification. The integrated flow passed on the Fire tablet with Amazon App Tester. The deployed HTTPS verifier rejected an invalid receipt through production RVS. A successful production-mode checkout remains open. [0.2.9 billing verification](amazon-premium-release.md)
