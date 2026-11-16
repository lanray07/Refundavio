# Amazon Premium billing verification

16 November 2026 · physical Fire tablet KFTRWI (Fire OS, Android API 28)

Refundavio 0.2.9 adds the Amazon Appstore SDK to the release build. The app loads the localized price for `app.refundavio.premium`, verifies receipts through the backend, and acknowledges fulfillment after verification. Restore queries Amazon purchase history and verifies any active receipt again. An active entitlement unlocks one ZIP containing both the PDF and CSV reports. The individual exports remain available in the free app.

| Check | Result | Evidence |
| --- | --- | --- |
| Localized offer and fresh App Tester checkout | Pass | The isolated QA app initially showed no active purchase and displayed the Amazon App Tester price. App Tester opened checkout and completed a synthetic transaction. |
| Checkout cancellation | Pass | The app reported the incomplete purchase and kept Premium locked. |
| Purchase and receipt verification | Pass | The app showed Premium as active after the sandbox receipt went through the local verifier to Amazon RVS Cloud Sandbox. |
| Restore and verification failure | Pass | After the 0.2.9 QA install and a cold start, Amazon purchase history restored Premium. Removing the connection to the verifier produced a retryable error, hid the purchase button and removed the Premium ZIP option. The individual report formats remained available. Reconnecting and restoring returned Premium to active. |
| Invalid receipt through the sandbox verifier | Pass | Amazon RVS Cloud Sandbox rejected a deliberately invalid receipt. The verifier returned an inactive entitlement. |
| Paid feature delivery | Pass | The system file picker saved the Premium ZIP on the Fire tablet. ZIP integrity passed, the PDF header was valid and the CSV contained the local QA record. |
| Production verifier tests | Pass | Automated tests covered production endpoint selection and receipt validation, wrong SKU/type/ID, cancellation, sandbox receipt rejection in production, malformed input and transient Amazon failure. The tests use stubbed Amazon responses. |
| Android build and unit tests | Pass | The 0.2.9 QA and unsigned release APKs built, and all 17 Android unit tests passed. The release APK includes the Amazon SDK and public authentication key. |
| Production checkout on a signed release APK | Pending | Requires a deployed HTTPS verifier and production Amazon RVS secret. The non-debuggable release binary cannot use App Tester. Amazon DevTest requires the IAP item to be live. [Amazon testing modes](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html) · [DevTest prerequisites](https://developer.amazon.com/docs/app-testing/get-started-appstore-devtest-iap.html) |

The Fire purchase and restore used debuggable QA APKs with the same app UI, billing code and verifier implementation. The fresh purchase was made before the version bump, and the final 0.2.9 QA APK reverified the same transaction on restore. The tablet’s original App Tester catalogue and routing were restored after testing.

The Amazon listing still contains the earlier 0.2.8 APK. The listing and entitlement are both unsubmitted, and export certification is unchecked. See [server configuration](../../server/README.md) for the release deployment and privacy prerequisites.
