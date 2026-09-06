param(
    [string]$Package = 'com.futo.platformplayer.compose.graytest',
    [int]$X = 500,
    [int]$FromY = 1780,
    [int]$ToY = 800,
    [int]$SwipeMs = 320,
    [int]$SwipesPerDirection = 6,
    [string]$OutputFile = ''
)

$ErrorActionPreference = 'Stop'
# Open the intended list and return it to its top before running this script.
# Coordinates deliberately remain explicit so a different screen size cannot silently
# turn the benchmark into gestures on player controls or navigation.
& adb shell dumpsys gfxinfo $Package reset | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not reset frame statistics.' }
for ($index = 0; $index -lt $SwipesPerDirection; $index++) {
    & adb shell input swipe $X $FromY $X $ToY $SwipeMs
}
for ($index = 0; $index -lt $SwipesPerDirection; $index++) {
    & adb shell input swipe $X $ToY $X $FromY $SwipeMs
}
$statistics = & adb shell dumpsys gfxinfo $Package
if ($LASTEXITCODE -ne 0) { throw 'Could not read frame statistics.' }
if ($OutputFile) { $statistics | Out-File -LiteralPath $OutputFile -Encoding utf8 }
$statistics | Select-String 'Total frames|Janky frames:|percentile:|Slow UI thread|Frame deadline missed:'
