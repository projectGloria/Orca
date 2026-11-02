$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$runner = 'io.github.nihil.orca.test/androidx.test.runner.AndroidJUnitRunner'
$test = 'io.github.nihil.orca.ProcessRecoveryTest'
$seed = & $adb shell am instrument -w -e class $test -e phase seed $runner
$seed | Write-Output
if (($seed -join "`n") -notmatch 'OK \(1 test\)') { throw 'Seed instrumentation failed' }
& $adb shell am force-stop io.github.nihil.orca
& $adb shell am start -n io.github.nihil.orca/.MainActivity
$verify = & $adb shell am instrument -w -e class $test -e phase verify $runner
$verify | Write-Output
if (($verify -join "`n") -notmatch 'OK \(1 test\)') { throw 'Verification instrumentation failed' }
