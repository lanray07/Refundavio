# Background reminder verification · Refundavio 0.2.0

Verified source revision: `4578005`. [Passing hosted Android run](https://github.com/lanray07/Refundavio/actions/runs/43716828547).

| Check | Result |
| --- | --- |
| Closed-app delivery | PASS — Android 15 tablet emulator. The harness scheduled the real inexact Android alarm, sent Refundavio to Home, and confirmed the process had exited before the alert was due. Both notification IDs became active after the configured time without reopening Refundavio. |
| Observed timing | Preferred time 13:07:18 UTC; both notifications observed at 13:08:07 UTC on 15 November 2026. The emulator was awake. No test command forced the alarm to fire. |
| Schedule calculations | PASS — 5 reminder tests for lead days and time, repeat suppression, rescheduled follow-ups, closed or expired cases, and daylight saving. The existing 5 refund tests also passed. |
| Device regressions | PASS — Dashboard journey and Room migration from the exported version-one schema. The migration retained existing records and verified reminder history cascades on deletion. |
| Builds and lint | PASS — Debug, unsigned release and instrumentation APKs built. Android lint reported zero errors. |
| Localisation resources | PASS — English, Spanish, French and Arabic each have 140 strings and three plural groups, with matching placeholders. |
| Physical K70_PRO tablet | NOT TESTED — Windows detected a media connection, but no ADB device appeared. No APK was installed on the tablet. |
| Doze, reboot and Android Force stop | NOT TESTED in this run. Android can delay inexact reminders under battery restrictions. The app re-arms on boot and clock changes. Android Force stop suspends delivery until the app is opened. |

The first [hosted attempt](https://github.com/lanray07/Refundavio/actions/runs/43715985441) timed out without observing notifications. The passing run kept the emulator awake and checked active notification keys directly. It verifies ordinary closed-app delivery, not an exact delivery guarantee or behavior in sleep.

Use **More → Background reminders** to configure the two channels, lead days and preferred time. Notification text never includes item names, merchant names or amounts. Tapping an alert opens the corresponding queue. No exact-alarm or internet permission is required.
