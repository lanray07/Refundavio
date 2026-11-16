# Refundavio Premium sandbox verification

Amazon saved **Refundavio Premium** as a one-time entitlement draft with SKU `app.refundavio.premium`. The base test price is **£4.99**, with marketplace prices calculated by Amazon. The English display title, one-time purchase description, and 512 px and 114 px icons are saved. The entitlement and the 0.2.8 Amazon listing are unsubmitted. Export certification is unchecked. [Amazon entitlement setup](https://developer.amazon.com/docs/in-app-purchasing/iap-create-and-submit-iap-items.html)

| Check | Result | Evidence |
| --- | --- | --- |
| Catalogue lookup | PASS | On Fire tablet `KFTRWI` (Android API 28), Amazon App Tester returned the configured SKU, a price, and product type `ENTITLED`. |
| One-time purchase | PASS | The debug QA app received a successful purchase callback and an active receipt under the App Tester test account. |
| Purchase receipt verification | PASS | Amazon RVS Cloud Sandbox returned the matching receipt and SKU, product type `ENTITLED`, a null cancellation date, and `testTransaction: true`. The test then sent the SDK fulfillment acknowledgment. |
| Restore | PASS | A separate instrumentation process queried Amazon's paginated purchase history. It restored the same active receipt for the same test account. RVS Cloud Sandbox verified it again. |
| Invalid receipt | PASS | RVS Cloud Sandbox rejected an invalid receipt with HTTP 400. |

## Run the sandbox checks

The debug build uses Amazon Appstore SDK 3.0.9 and the isolated `app.refundavio.qa` package. The tests enforce App Tester routing before making a purchase request. The Amazon App Tester catalogue must include the entitlement SKU. A fresh sandbox transaction for this SKU is needed to rerun the purchase test. [App Tester setup](https://developer.amazon.com/docs/in-app-purchasing/iap-install-and-configure-app-tester.html)

1. Build with `gradlew.bat --offline --max-workers 2 -PrefundavioQa assembleDebug assembleDebugAndroidTest` and install both APKs on the test tablet.
2. With Amazon App Tester installed, configure `app.refundavio.premium` as `ENTITLED` in `amazon.sdktester.json`, enable sandbox routing, and run `PremiumSandboxTest#purchaseOneTimePremium` with `-e premiumSandbox true`.
3. Run `python scripts/verify-amazon-sandbox.py --serial DEVICE purchase` to verify the receipt against Amazon and record the verified response in the QA package.
4. Run `PremiumSandboxTest#fulfillVerifiedPremiumReceipt`, then `PremiumSandboxTest#restoreOneTimePremiumFromAmazonHistory`, each with `-e premiumSandbox true`.
5. Run `python scripts/verify-amazon-sandbox.py --serial DEVICE restore` and restore the previous tablet test configuration.

The RVS script uses a sandbox placeholder and prints the outcome without receipt or user identifiers. Production still requires an end-user purchase flow, entitlement delivery, secure server-side RVS configuration, and live testing. The 0.2.8 draft APK has no Premium purchase flow. [RVS Cloud Sandbox](https://developer.amazon.com/docs/in-app-purchasing/rvs-cloud-sandbox.html) · [Production RVS setup](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-setup-prod.html)
