param(
    [Parameter(Mandatory=$true)][string]$AdbPath,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$Serial = 'emulator-5554',
    [string[]]$Profiles = @('small-phone', 'medium-phone', 'large-text', 'tablet-portrait', 'tablet-landscape')
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
if ($Serial -notmatch '^emulator-\d+$') { throw 'Layout overrides are permitted only on an emulator.' }
$matrix = @(
    @{ Name='pixel-reference'; Size='1080x2424'; Density=420; Font='1.0' },
    @{ Name='small-phone'; Size='720x1280'; Density=360; Font='1.0' },
    @{ Name='medium-phone'; Size='1080x2160'; Density=480; Font='1.0' },
    @{ Name='large-text'; Size='1080x2160'; Density=480; Font='1.4' },
    @{ Name='tablet-portrait'; Size='1200x1920'; Density=240; Font='1.0' },
    @{ Name='tablet-landscape'; Size='1920x1200'; Density=240; Font='1.0' },
    @{ Name='small-phone-dark'; Size='720x1280'; Density=360; Font='1.0'; Dark=$true },
    @{ Name='tablet-landscape-dark'; Size='1920x1200'; Density=240; Font='1.0'; Dark=$true }
)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
foreach ($profile in $matrix | Where-Object { $_.Name -in $Profiles }) {
    Write-Output "Checking $($profile.Name): $($profile.Size), $($profile.Density) dpi, font $($profile.Font)"
    & $AdbPath -s $Serial wait-for-device
    & $AdbPath -s $Serial shell am force-stop com.futo.platformplayer.compose.graytest
    & $AdbPath -s $Serial shell wm size $profile.Size
    & $AdbPath -s $Serial shell wm density $profile.Density
    & $AdbPath -s $Serial shell settings put system font_scale $profile.Font
    & $AdbPath -s $Serial shell settings put secure show_ime_with_hard_keyboard 1
    $navigation = if ($profile.Name.StartsWith('tablet')) { 'threebutton' } else { 'gestural' }
    & $AdbPath -s $Serial shell cmd overlay enable-exclusive --category "com.android.internal.systemui.navbar.$navigation"
    Start-Sleep -Milliseconds 1500
    & $AdbPath -s $Serial wait-for-device
    $actualSize = & $AdbPath -s $Serial shell wm size
    $actualDensity = & $AdbPath -s $Serial shell wm density
    if (($actualSize -join ' ') -notmatch [regex]::Escape($profile.Size)) { throw 'Display size override did not apply.' }
    if (($actualDensity -join ' ') -notmatch "density: $($profile.Density)") { throw 'Display density override did not apply.' }
    @($actualSize; $actualDensity; "font_scale=$($profile.Font)") | Out-File -LiteralPath (Join-Path $OutputDirectory "$($profile.Name)-display.txt") -Encoding utf8
    $dark = if ($profile.Dark) { 'true' } else { 'false' }
    $result = & $AdbPath -s $Serial shell am instrument -w -e timeout_msec 120000 -e class 'com.futo.platformplayer.compose.GrayjayAppTest#captureResponsiveScreens' -e layoutAuditName $profile.Name -e layoutAuditDark $dark com.futo.platformplayer.compose.graytest.test/androidx.test.runner.AndroidJUnitRunner
    $result | Out-File -LiteralPath (Join-Path $OutputDirectory "$($profile.Name)-result.txt") -Encoding utf8
    & $AdbPath -s $Serial pull "/sdcard/Android/data/com.futo.platformplayer.compose.graytest/files/layout-audit/$($profile.Name)" $OutputDirectory
    if ($result -match 'OK \(1 test\)') { Write-Output "$($profile.Name): capture complete" }
    else { Write-Output "$($profile.Name): inspect test failure and available captures" }
}
