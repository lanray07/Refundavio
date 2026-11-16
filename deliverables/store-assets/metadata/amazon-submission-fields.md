# Refundavio Amazon Appstore draft for Fire tablets

The following values describe the Refundavio 0.2.10 public draft. The [marketplace localizations](amazon-marketplace-localizations.json) contain the additional listing copy for the Amazon draft. The [English, Spanish and French listing copy](dashboard-milestone.md) documents the original dashboard milestone.

| Field | Draft value |
| --- | --- |
| App title | Refundavio – Returns & Refund Tracker |
| Package | `app.refundavio` |
| Version | 0.2.10 (version code 12) |
| Target | 12 Fire tablet models selected in the Amazon draft |
| Category | Shopping |
| Short description | Track refunds, return deadlines and merchant follow-ups without an account. Keep purchase records on your device and set optional reminders for what needs attention. |
| Long description | See the refunds you’re waiting for and the returns you need to send soon. Record each refund as it arrives, track partial payments and view totals separately for each currency. Save return deadlines, merchant follow-up dates, receipts and warranty dates alongside your purchases. Set reminders for return deadlines and merchant follow-ups. Your records are available offline without an account. |
| Feature bullets | Refund balances by currency · Return deadline and merchant follow-up reminders · Partial refunds · Purchase evidence and warranty dates · Offline records |
| Search terms | refund tracker, shopping returns, return deadline, partial refund, merchant follow-up, receipt organiser |
| Website URL | https://github.com/lanray07/Refundavio |
| Support URL | https://github.com/lanray07/Refundavio/issues |
| Privacy policy URL | https://github.com/lanray07/Refundavio/blob/main/PRIVACY.md |
| Data deletion URL, if requested | https://github.com/lanray07/Refundavio/blob/main/PRIVACY.md |
| Reviewer instructions | [Copy the testing instructions below](#reviewer-instructions). |

The app stores its ledger on the device. Premium purchases and restores send the Amazon user ID and receipt ID to the verification server for an Amazon receipt check. Optional voice shortcuts and spoken summaries use installed speech services that can use the network. The draft privacy questionnaire includes billing and voice disclosures. [Refundavio privacy policy](../../../PRIVACY.md) · [Amazon Appstore privacy labels](https://developer.amazon.com/docs/app-submission/appstore-privacy-labels.html)

The Gradle release output is unsigned. Sign the Appstore upload using the existing Refundavio release key. The [store screenshots](../screenshots/) show the released Fire tablet interface. Confirm pricing, distribution, ratings and any required support contact in the developer account. [Amazon submission requirements](https://developer.amazon.com/docs/app-submission/submitting-apps-to-amazon-appstore.html) · [Amazon app testing criteria](https://developer.amazon.com/docs/app-testing/test-criteria.html)

## Reviewer instructions

```text
No account or sign-in is required.

Dashboard: Open More > Preview sample dashboard to see outstanding refunds, return deadlines, and merchant follow-ups. For a live test, choose More > Exit sample dashboard.

Reminders: Add a test purchase with an expected refund. Set Return status to Return requested, and set both Return deadline and Next follow-up to today. In More > Background reminders, enable both reminder types, select the due-date option (0 days), and set the reminder time a few minutes ahead. Allow notifications if prompted. Use Home to leave Refundavio. Check for both notices after the scheduled time. Tapping a notice opens the relevant queue. Device power management may delay delivery.

Voice shortcuts are optional. If speech recognition is unavailable, type or select a command.
```

## Premium entitlement descriptions

The public entitlement uses SKU `app.refundavio.premium` and the display title **Refundavio Premium** in all 12 locales offered by the entitlement editor. The descriptions below match the saved Amazon draft. The public app and entitlement remain unsubmitted, and public export certification is unchecked.

| Amazon locale | Description |
| --- | --- |
| English (U.S.) | Unlock the option to export your Refundavio report as one ZIP file containing both a PDF for easy reading and a CSV for spreadsheets. Buy once, with no subscription. Individual PDF and CSV exports remain free. |
| English (Australia) | Unlock the option to export your Refundavio report as one ZIP file containing both a PDF for easy reading and a CSV for spreadsheets. Buy once, with no subscription. Individual PDF and CSV exports remain free. |
| English (U.K.) | Unlock the option to export your Refundavio report as one ZIP file containing both a PDF for easy reading and a CSV for spreadsheets. Buy once, with no subscription. Individual PDF and CSV exports remain free. |
| English (India) | Unlock the option to export your Refundavio report as one ZIP file containing both a PDF for easy reading and a CSV for spreadsheets. Buy once, with no subscription. Individual PDF and CSV exports remain free. |
| German | Exportiere deinen Refundavio-Bericht als einzelne ZIP-Datei mit einem gut lesbaren PDF und einer CSV-Datei für Tabellenprogramme. Einmaliger Kauf ohne Abo. Der separate Export als PDF oder CSV bleibt kostenlos. |
| Spanish | Exporta tu informe de Refundavio en un único archivo ZIP que incluye un PDF para leer y un CSV para hojas de cálculo. Compra única, sin suscripción. Las exportaciones por separado en PDF y CSV siguen siendo gratuitas. |
| Spanish (Mexico) | Exporta tu reporte de Refundavio en un solo archivo ZIP que incluye un PDF fácil de leer y un CSV para hojas de cálculo. Paga una sola vez, sin suscripción. Puedes seguir exportando el PDF y el CSV por separado sin costo. |
| French | Exportez votre rapport Refundavio dans un seul fichier ZIP contenant un PDF facile à consulter et un CSV pour les tableurs. Achat unique sans abonnement. Les exports séparés en PDF et en CSV restent gratuits. |
| Italian | Esporta il tuo riepilogo di Refundavio in un unico file ZIP con un PDF da consultare e un CSV per i fogli di calcolo. Acquisto una tantum, senza abbonamento. Le esportazioni separate in PDF e CSV restano gratuite. |
| Japanese | Refundavioのレポートを、閲覧用のPDFと表計算ソフト用のCSVをまとめた1つのZIPファイルとして書き出せます。買い切りで、定期購入は不要です。PDFとCSVを個別に書き出す機能は引き続き無料です。 |
| Portuguese (Brazil) | Exporte seu relatório do Refundavio em um único arquivo ZIP com um PDF para leitura e um CSV para planilhas. Compra única, sem assinatura. As exportações separadas em PDF e CSV continuam gratuitas. |
| Chinese (Simplified) | 将 Refundavio 报告导出为一个 ZIP 文件，其中包含便于阅读的 PDF 和可用电子表格软件打开的 CSV。一次性购买，无需订阅。单独导出 PDF 或 CSV 仍然免费。 |

## Screenshot fallback locales

These four listing descriptions are saved in Amazon. The draft’s multimedia editor does not offer these locales, so Amazon uses the English (U.S.) screenshots. Each linked Fire tablet set contains the localized refund dashboard, return deadline, and warranty screenshots.

| Listing locale | Amazon screenshot fallback | Localized screenshot set |
| --- | --- | --- |
| Dutch (Netherlands) | English (U.S.) | [Dashboard](../screenshots/refundavio-nl-nl-fire-tablet-01-terugbetalingen-volgen.png) · [Return deadlines](../screenshots/refundavio-nl-nl-fire-tablet-02-retourtermijnen.png) · [Warranty dates](../screenshots/refundavio-nl-nl-fire-tablet-03-garantiedata.png) |
| Polish (Poland) | English (U.S.) | [Dashboard](../screenshots/refundavio-pl-pl-fire-tablet-01-sledzenie-zwrotow-pieniedzy.png) · [Return deadlines](../screenshots/refundavio-pl-pl-fire-tablet-02-terminy-zwrotow.png) · [Warranty dates](../screenshots/refundavio-pl-pl-fire-tablet-03-terminy-gwarancji.png) |
| Portuguese (Portugal) | English (U.S.) | [Dashboard](../screenshots/refundavio-pt-pt-fire-tablet-01-reembolsos-pendentes.png) · [Return deadlines](../screenshots/refundavio-pt-pt-fire-tablet-02-prazos-de-devolucao.png) · [Warranty dates](../screenshots/refundavio-pt-pt-fire-tablet-03-datas-de-garantia.png) |
| Swedish (Sweden) | English (U.S.) | [Dashboard](../screenshots/refundavio-sv-se-fire-tablet-01-vantande-aterbetalningar.png) · [Return deadlines](../screenshots/refundavio-sv-se-fire-tablet-02-returfrister.png) · [Warranty dates](../screenshots/refundavio-sv-se-fire-tablet-03-garantidatum.png) |
