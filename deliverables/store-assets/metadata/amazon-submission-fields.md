# Refundavio Amazon Appstore draft for Fire tablets

The following values describe the Refundavio 0.2.4 build. The [marketplace localizations](amazon-marketplace-localizations.json) contain the additional listing copy for the Amazon draft. The [English, Spanish and French listing copy](dashboard-milestone.md) documents the original dashboard milestone.

| Field | Draft value |
| --- | --- |
| App title | Refundavio – Returns & Refund Tracker |
| Package | `app.refundavio` |
| Version | 0.2.4 (version code 6) |
| Target | Fire OS tablets. Choose supported models after the release binary is uploaded and the console reports compatibility. |
| Category | Shopping, if available in the console. |
| Short description | Track refunds, return deadlines and merchant follow-ups without an account. Keep purchase records on your device and set optional reminders for what needs attention. |
| Long description | See the refunds you’re waiting for and the returns you need to send soon. Record each refund as it arrives, track partial payments and view totals separately for each currency. Save return deadlines, merchant follow-up dates, receipts and warranty dates alongside your purchases. Set reminders for return deadlines and merchant follow-ups. Your records are available offline without an account. |
| Feature bullets | Refund balances by currency · Return deadline and merchant follow-up reminders · Partial refunds · Purchase evidence and warranty dates · Offline records |
| Search terms | refund tracker, shopping returns, return deadline, partial refund, merchant follow-up, receipt organiser |
| Website URL | https://github.com/lanray07/Refundavio |
| Support URL | https://github.com/lanray07/Refundavio/issues |
| Privacy policy URL | https://github.com/lanray07/Refundavio/blob/main/PRIVACY.md |
| Data deletion URL, if requested | https://github.com/lanray07/Refundavio/blob/main/PRIVACY.md |
| Reviewer instructions | [Copy the testing instructions below](#reviewer-instructions). |

For the privacy questionnaire, use the policy and release binary to verify disclosures. The app stores its ledger on the device and has no internet permission. Optional voice shortcuts and spoken summaries use installed speech services that can use the network. Review how Amazon’s questionnaire classifies those services before choosing answers. [Amazon Appstore privacy labels](https://developer.amazon.com/docs/app-submission/appstore-privacy-labels.html)

The Gradle release output is unsigned. Sign the Appstore upload using the existing Refundavio release key. The [store screenshots](../screenshots/) show the released Fire tablet interface. Confirm pricing, distribution, ratings and any required support contact in the developer account. [Amazon submission requirements](https://developer.amazon.com/docs/app-submission/submitting-apps-to-amazon-appstore.html) · [Amazon app testing criteria](https://developer.amazon.com/docs/app-testing/test-criteria.html)

## Reviewer instructions

```text
No account or sign-in is required.

Dashboard: Open More > Preview sample dashboard to see outstanding refunds, return deadlines, and merchant follow-ups. For a live test, choose More > Exit sample dashboard.

Reminders: Add a test purchase with an expected refund. Set Return status to Return requested, and set both Return deadline and Next follow-up to today. In More > Background reminders, enable both reminder types, select the due-date option (0 days), and set the reminder time a few minutes ahead. Allow notifications if prompted. Use Home to leave Refundavio. Check for both notices after the scheduled time. Tapping a notice opens the relevant queue. Device power management may delay delivery.

Voice shortcuts are optional. If speech recognition is unavailable, type or select a command.
```
