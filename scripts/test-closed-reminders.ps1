param(
    [string]$Adb = 'adb',
    [string]$Serial,
    [string]$Package = 'app.refundavio.qa',
    [switch]$DisposableEmulator,
    [switch]$IncludeWarranty,
    [int]$TimeoutSeconds = 210
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
if ($Package -ne 'app.refundavio.qa' -and !$DisposableEmulator) {
    throw 'Use the isolated .qa package on a personal device.'
}
$device = @()
if ($Serial) { $device = @('-s', $Serial) }
$expectedIds = if ($IncludeWarranty) { @(1001, 1002, 1003) } else { @(1001, 1002) }
$idPattern = '(' + ($expectedIds -join '|') + ')'

function Android {
    $output = & $Adb @device @args
    if ($LASTEXITCODE -ne 0) { throw "adb command failed: $($args[0])" }
    return $output
}

function Active-ReminderIds {
    $keys = & $Adb @device shell cmd notification list
    if ($LASTEXITCODE -eq 0 -and !($keys -match 'Unknown command|^usage:')) {
        foreach ($key in $keys) {
            if ($key -match ('^\d+\|' + [regex]::Escape($Package) + '\|' + $idPattern + '\|')) { [int]$Matches[1] }
        }
        return
    }
    $active = $false
    foreach ($line in (Android shell dumpsys notification)) {
        if ($line -match '^\s*Notification List:\s*$') { $active = $true; continue }
        if (!$active) { continue }
        if ($line -match '^\s*(?:Enqueued Notification List|Snoozed notifications|Ranking Config|Notification listener)\b') { break }
        if ($line -match ('NotificationRecord\(.*\bpkg=' + [regex]::Escape($Package) + '\s+.*\bid=' + $idPattern + '\b')) {
            [int]$Matches[1]
        }
    }
}

if ($DisposableEmulator -and (Android shell getprop ro.kernel.qemu | Out-String).Trim() -ne '1') {
    throw 'Disposable mode requires an Android emulator.'
}
if ($DisposableEmulator) {
    # Keep the test environment interactive so battery policies do not obscure closed-app delivery.
    Android shell svc power stayon true
    Android shell input keyevent KEYCODE_WAKEUP
}
$sdk = [int](Android shell getprop ro.build.version.sdk)
if ($sdk -ge 33) {
    Android shell pm grant $Package android.permission.POST_NOTIFICATIONS
}
$argsForFixture = @('shell', 'am', 'instrument', '-w', '-e', 'class',
    'app.refundavio.ReminderDeviceTest#seedAlarmForClosedAppTest',
    '-e', 'seedReminders', 'true', '-e', 'delaySeconds', '65')
if ($DisposableEmulator) { $argsForFixture += @('-e', 'disposableEmulator', 'true') }
if ($IncludeWarranty) { $argsForFixture += @('-e', 'includeWarranty', 'true') }
$argsForFixture += "$Package.test/androidx.test.runner.AndroidJUnitRunner"
$seed = Android @argsForFixture
$seed | Write-Output
if (!($seed -match '^OK \(1 test\)\s*$')) { throw 'The scheduled alarm fixture failed.' }
$trigger = [DateTimeOffset]::Parse((Android shell run-as $Package cat files/reminder-test-trigger.txt | Out-String).Trim())

Android shell am start -W -n "$Package/app.refundavio.MainActivity"
$running = & $Adb @device shell pidof $Package
if (!$running) { throw 'The app process did not start before the closed-app test.' }
Android shell input keyevent KEYCODE_HOME
$exited = $false
for ($try = 0; $try -lt 15; $try++) {
    Android shell am kill $Package
    $running = & $Adb @device shell pidof $Package
    if (!$running) { $exited = $true; break }
    Start-Sleep -Seconds 1
}
if (!$exited) { throw 'The app process did not exit. No closed-app result can be reported.' }
$deviceNow = [DateTimeOffset]::FromUnixTimeSeconds([long](Android shell date +%s))
if ($deviceNow -ge $trigger) { throw 'The app did not close before the alarm was due.' }
if (@(Active-ReminderIds).Count -ne 0) { throw 'A notification posted before its scheduled time.' }
Write-Output "App sent to Home and process confirmed gone before alarm time $($trigger.ToString('o'))."
if ($DisposableEmulator) {
    Android shell dumpsys alarm | Select-String 'app\.refundavio\.REMINDER_ALARM' -Context 0,2 | Out-String | Write-Output
}

$watch = [Diagnostics.Stopwatch]::StartNew()
do {
    $ids = @(Active-ReminderIds)
    if (!($expectedIds | Where-Object { $_ -notin $ids })) {
        $received = [DateTimeOffset]::FromUnixTimeSeconds([long](Android shell date +%s))
        if ($received -lt $trigger) { throw 'The notifications appeared early.' }
        Write-Output "PASS: Android posted reminder IDs $($expectedIds -join ', ') without reopening the app at $($received.ToString('o'))."
        exit 0
    }
    Start-Sleep -Seconds 3
} while ($watch.Elapsed.TotalSeconds -lt $TimeoutSeconds)
if ($DisposableEmulator) {
    Android shell dumpsys alarm | Select-String 'app\.refundavio\.REMINDER_ALARM' -Context 0,3 | Out-String | Write-Output
    Android shell cmd notification list | Where-Object { $_ -match [regex]::Escape($Package) } | Write-Output
    Android shell logcat -d -s RefundavioReminders:D | Write-Output
}
throw "Expected reminder IDs $($expectedIds -join ', ') within $TimeoutSeconds seconds (last IDs: $ids)."
