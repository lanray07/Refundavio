# Refundavio Premium receipt verification

The Android app sends an Amazon user ID and receipt ID to `POST /verify`. The server calls Amazon RVS over HTTPS and returns an active entitlement only for the expected one-time SKU, matching receipt, correct environment, and no cancellation date. The server keeps no receipt or user identifier and returns only the verification outcome. The production secret belongs in the hosting environment.

## Production configuration

1. Run the verifier with Node.js 22 or newer. Set the variables shown in [.env.example](.env.example) through the host’s secret configuration. Use the [Amazon RVS production shared secret](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-setup-prod.html).
2. Run `node server/premium-verifier.mjs`. It binds to loopback. Put an HTTPS reverse proxy in front of `POST /verify` and apply per-client rate limiting at the proxy. The process also limits concurrent checks and connections from a single peer. Omit request bodies and upstream RVS URLs from proxy and application logs.
3. Build with `gradlew assembleRelease -PrefundavioPremiumVerifier=https://billing.example.org/verify`. Use the actual endpoint. Release builds without an endpoint show that purchases are unavailable. The Amazon app’s public SDK authentication key is packaged in the app assets. Sign the APK through the established release process.
4. Confirm that the Appstore listing and privacy questionnaire describe use of Amazon billing identifiers before submitting the updated build. Use the confirmed release SKU and pricing from the Developer Console.

## QA on the Fire tablet

Run `node --test server/premium-verifier.test.mjs` for verifier tests. Run the server with `AMAZON_RVS_MODE=sandbox` and use `adb reverse tcp:8765 tcp:8765`. Build with `gradlew assembleDebug -PrefundavioQa=billing` to use the same app UI and billing code in an isolated test package. Set up Amazon App Tester with the Premium SKU as `ENTITLED`, then enable App Tester routing using `adb shell setprop debug.amazon.sandboxmode debug`. Open **More → Refundavio Premium**. After purchase and restore, verify that the option to export a PDF and CSV bundle appears in **More → Reports** when real or test data is available. Restore the tablet’s original catalogue, reverse mapping and sandbox flag when finished.

Amazon App Tester and RVS Cloud Sandbox exercise the debug QA APK. Amazon’s release-mode DevTest requires the in-app item to be live. [Amazon IAP testing modes](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html) · [DevTest prerequisites](https://developer.amazon.com/docs/app-testing/get-started-appstore-devtest-iap.html)
