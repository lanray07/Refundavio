# Refundavio milestone and release scope

## Product decision

Deadline queues and refund tracking are already offered by [ReturnTracker](https://www.returntracker.app/features) and [RefundTrail](https://apps.apple.com/gb/app/refundtrail-return-tracker/id6818855545). Refundavio's proposed position is a tablet-friendly, account-free recovery dashboard that immediately separates money still owed, verified-by-user payments and time-sensitive work across currencies. This is a positioning hypothesis; the name still needs formal trademark and marketplace clearance.

The brief proposes an optional one-time Amazon Premium unlock. Keep deadlines, existing records, basic balances and purchase history available without payment. Test price and limits with users before introducing a paywall. No Amazon purchasing or real-money entitlements are active in this milestone.

## Privacy and security

The APK has no internet permission or embedded service keys. Records use the app-private Room database, with Android backup disabled. The Android document picker grants read access only to the selected attachment. Removing an attachment from a record does not delete the provider’s file. Speech is optional and delegated to the installed Android service. Drafts are generated locally and copied only by explicit user action. Database deletion of all purchase records requires confirmation. No account, analytics, merchant integration or automatic messaging is configured.

## Still required before submission

- Expand the purchase model to independent order, item, return and merchant entities, with multiple products per order, archiving and richer parcel, merchant, warranty and evidence histories.
- Add and verify Android notifications, configurable lead times, CSV/PDF exports and encrypted backups. Dashboard due dates and rescheduling exist; no background reminders are delivered.
- Expand localisation to the complete brief, review translations, verify all relevant device speech services, and generate eight real app screenshots for every approved Amazon listing locale.
- Integrate and sandbox-test Amazon billing with a configured non-consumable product, restore and receipt verification. Configure signing, verify Fire hardware, confirm current submission criteria, prepare store assets and obtain release approval.
- The local follow-up draft is an editable template. Remote AI is not connected.

The app is not ready for Amazon Appstore submission.
