# Background reminder verification · Refundavio 0.2.0

Verified source revision: `4578005`. [Passing hosted Android run](https://github.com/lanray07/Refundavio/actions/runs/43716828547).

| Check | Result |
| --- | --- |
| Closed-app delivery on a physical Fire tablet | PASS — Amazon Fire tablet KFTRWI (Android 9, API 28). The isolated QA build scheduled a real inexact alarm. The harness sent the app to Home and confirmed its process had exited before the alarm was due. Android then posted both the return deadline (ID 1001) and merchant follow-up (ID 1002) notifications without reopening the app. |
| Physical Fire tablet timing | On 15 November 2026, the alarm was scheduled for 14:02:56 GMT. Both notifications were observed at 14:02:59 GMT, three seconds later. The tablet was awake and connected by USB. No test command forced the alarm to fire. The regular Refundavio installation and records were untouched. |
| Closed-app delivery on an emulator | PASS — Android 15 tablet emulator. The harness confirmed the process had exited before the alarm was due and observed both notifications without reopening the app. Preferred time: 13:07:18 GMT; both observed at 13:08:07 GMT on 15 November 2026. |
| Schedule calculations | PASS — 5 reminder tests for lead days and time, repeat suppression, rescheduled follow-ups, closed or expired cases, and daylight saving. The existing 5 refund tests also passed. |
| Device regressions | PASS — Dashboard journey and Room migration from the exported version-one schema. The migration retained existing records and verified reminder history cascades on deletion. |
| Builds and lint | PASS — Debug, unsigned release and instrumentation APKs built. Android lint reported zero errors. |
| Localisation resources | PASS — English, Spanish, French and Arabic each have 140 strings and three plural groups, with matching placeholders. |
| Physical K70_PRO tablet | NOT TESTED — Windows detected a media connection, but no ADB device appeared. No APK was installed on the tablet. |
| Doze, reboot and Android Force stop | NOT TESTED in this run. Android can delay inexact reminders under battery restrictions. The app re-arms on boot and clock changes. Android Force stop suspends delivery until the app is opened. |

The first [hosted attempt](https://github.com/lanray07/Refundavio/actions/runs/43715985441) timed out without observing notifications. The passing hosted run kept the emulator awake and checked active notification keys directly. Both successful tests establish closed-app delivery on awake devices. Delivery during sleep and exact timing remain unverified.

Use **More → Background reminders** to configure the two channels, lead days and preferred time. Notification text never includes item names, merchant names or amounts. Tapping an alert opens the corresponding queue. No exact-alarm or internet permission is required.
