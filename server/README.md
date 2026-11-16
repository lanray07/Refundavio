# Refundavio Premium receipt verification

The Android app sends an Amazon user ID and receipt ID to `POST /verify`. The server calls Amazon RVS over HTTPS and returns an active entitlement only for the expected one-time SKU, matching receipt, correct environment, and no cancellation date. The server keeps no receipt or user identifier and returns only the verification outcome. The production secret belongs in the hosting environment.

## Production configuration

Refundavio uses [Cloudflare Workers](https://developers.cloudflare.com/workers/) for the [production endpoint](https://refundavio-premium-verifier.lanraybanks.workers.dev/verify). The Worker bounds request bodies and rate-limits Amazon calls per Cloudflare location without using a client IP address or identifiers as rate-limit keys. Worker observability is disabled. The Amazon secret is stored as the encrypted Worker secret `AMAZON_RVS_SHARED_SECRET`.

To deploy code updates, run `npx wrangler deploy --config server/wrangler.toml --keep-vars --no-upload-source-maps` from the repository root. Use `npx wrangler secret put AMAZON_RVS_SHARED_SECRET --config server/wrangler.toml` when provisioning or rotating the secret. Build the APK with `gradlew assembleRelease -PrefundavioPremiumVerifier=https://refundavio-premium-verifier.lanraybanks.workers.dev/verify` and sign it with the established Refundavio publisher certificate.

### Standalone Node.js hosting

1. Run the verifier with Node.js 22 or newer. Set the variables shown in [.env.example](.env.example) through the host’s secret configuration. Use the [Amazon RVS production shared secret](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-setup-prod.html).
2. Run `node server/premium-verifier.mjs`. It binds to loopback. Put an HTTPS reverse proxy in front of `POST /verify` and apply per-client rate limiting at the proxy. The process also limits concurrent checks and connections from a single peer. Omit request bodies and upstream RVS URLs from proxy and application logs.
3. Build with `gradlew assembleRelease -PrefundavioPremiumVerifier=https://billing.example.org/verify`. Use the actual endpoint. Release builds without an endpoint show that purchases are unavailable. The Amazon app’s public SDK authentication key is packaged in the app assets. Sign the APK through the established release process.
4. Confirm that the Appstore listing and privacy questionnaire describe use of Amazon billing identifiers before submitting the updated build. Use the confirmed release SKU and pricing from the Developer Console.

## QA on the Fire tablet

Run `node --test server/premium-verifier.test.mjs` for verifier tests. Run the server with `AMAZON_RVS_MODE=sandbox` and use `adb reverse tcp:8765 tcp:8765`. Build with `gradlew assembleDebug -PrefundavioQa=billing` to use the same app UI and billing code in an isolated test package. Set up Amazon App Tester with the Premium SKU as `ENTITLED`, then enable App Tester routing using `adb shell setprop debug.amazon.sandboxmode debug`. Open **More → Refundavio Premium**. After purchase and restore, verify that the option to export a PDF and CSV bundle appears in **More → Reports** when real or test data is available. Restore the tablet’s original catalogue, reverse mapping and sandbox flag when finished.

Amazon App Tester and RVS Cloud Sandbox exercise the debug QA APK. Amazon’s release-mode DevTest requires the in-app item to be live. [Amazon IAP testing modes](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html) · [DevTest prerequisites](https://developer.amazon.com/docs/app-testing/get-started-appstore-devtest-iap.html)

## Private Amazon Live App Testing

Build the signed private APK with `gradlew assembleRelease -PrefundavioLat=true -PrefundavioPremiumVerifier=https://refundavio-premium-verifier.lanraybanks.workers.dev/verify-lat`. This build buys `app.refundavio.premium.lat`, a separate Amazon entitlement for Live App Testing. The route calls Amazon production RVS and requires the matching beta SKU and `betaProduct: true`. The Fire tablet's LAT receipt had `testTransaction: false`. The public APK uses `/verify` and the public SKU.

Install the private release from Amazon’s tester invitation, using the Amazon account selected for that test. Confirm the purchase activates Premium, close and reopen the app, then use **More → Refundavio Premium → Restore purchases**. Report the transaction outcome without recording Amazon user or receipt IDs. Amazon requires a separate export certification for the private test and does not charge testers for LAT purchases. [Live App Testing for IAP](https://developer.amazon.com/docs/in-app-purchasing/iap-lat.html) · [Live App Testing setup](https://developer.amazon.com/docs/app-testing/live-app-testing-getting-started.html)
