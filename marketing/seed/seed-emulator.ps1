# Seeds test data into FullTXT on a running emulator for Play Store screenshots.
# No cloud login, no SAF: installs the debug build, lets it create fulltxt.db,
# then injects test rows (seed.sql) and three "connected" cloud accounts
# (fulltxt_sync.xml) via run-as. Emulator-only (debug build, run-as).
#
# Usage:  powershell -ExecutionPolicy Bypass -File marketing\seed\seed-emulator.ps1
# Requires: a running emulator, the debug APK built (gradlew assembleDebug),
#           python3 on PATH (used to apply the SQL via the sqlite3 module).

$ErrorActionPreference = "Stop"
$sdk = "C:\Users\flori\AppData\Local\Android\Sdk"
$adb = "$sdk\platform-tools\adb.exe"
$pkg = "me.fulltxt.app"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$repo = Resolve-Path "$here\..\.."
$apk  = "D:\build\fulltxt\app\outputs\apk\debug\app-debug.apk"

# --- pick the emulator device ---
$serial = (& $adb devices) | Select-String "^emulator-\S+\s+device" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1
if (-not $serial) { throw "Kein laufender Emulator gefunden (adb devices)." }
Write-Host "Emulator: $serial"
function adbx { & $adb -s $serial @args }

# --- install debug build ---
Write-Host "Installiere Debug-APK ..."
adbx install -r -d "$apk" | Out-Null

# --- launch once so Room creates databases/fulltxt.db, then stop ---
Write-Host "Starte App einmal, damit die DB angelegt wird ..."
adbx shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Seconds 6
adbx shell am force-stop $pkg
Start-Sleep -Seconds 2

# --- pull the freshly created db (+wal/shm) ---
$tmp = Join-Path $env:TEMP "fulltxt_seed"
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$dbLocal = Join-Path $tmp "fulltxt.db"
Write-Host "Hole leere DB vom Geraet ..."
& $adb -s $serial exec-out run-as $pkg cat databases/fulltxt.db | Set-Content -Path $dbLocal -Encoding Byte
foreach ($ext in @("-wal","-shm")) {
  $ok = $true
  try { & $adb -s $serial exec-out run-as $pkg cat "databases/fulltxt.db$ext" | Set-Content -Path "$dbLocal$ext" -Encoding Byte -ErrorAction Stop } catch { $ok = $false }
  if (-not $ok -or (Test-Path "$dbLocal$ext") -eq $false) { Remove-Item "$dbLocal$ext" -ErrorAction SilentlyContinue }
}

# --- apply seed.sql with python's sqlite3, then checkpoint into the main file ---
Write-Host "Spiele Testdaten ein (python sqlite3) ..."
$seedSql = (Join-Path $here "seed.sql") -replace '\\','/'
$dbPy = $dbLocal -replace '\\','/'
$py = @"
import sqlite3
db = sqlite3.connect(r'$dbPy')
db.executescript(open(r'$seedSql', encoding='utf-8').read())
db.commit()
db.execute('PRAGMA wal_checkpoint(TRUNCATE)')
db.commit()
n = db.execute('SELECT count(*) FROM file_metadata').fetchone()[0]
print('Zeilen in file_metadata:', n)
db.close()
"@
$py | python3 -

# --- push db back and remove stale wal/shm on device ---
Write-Host "Schreibe DB zurueck ..."
adbx push "$dbLocal" /data/local/tmp/seed.db | Out-Null
adbx shell chmod 666 /data/local/tmp/seed.db
adbx shell run-as $pkg cp /data/local/tmp/seed.db databases/fulltxt.db
adbx shell run-as $pkg rm -f databases/fulltxt.db-wal databases/fulltxt.db-shm

# --- push the connected-accounts prefs ---
Write-Host "Schreibe verbundene Konten (SharedPreferences) ..."
$xml = (Join-Path $here "fulltxt_sync.xml")
adbx push "$xml" /data/local/tmp/fulltxt_sync.xml | Out-Null
adbx shell chmod 666 /data/local/tmp/fulltxt_sync.xml
adbx shell run-as $pkg mkdir -p shared_prefs
adbx shell run-as $pkg cp /data/local/tmp/fulltxt_sync.xml shared_prefs/fulltxt_sync.xml

# --- cleanup device temp ---
adbx shell rm -f /data/local/tmp/seed.db /data/local/tmp/fulltxt_sync.xml

Write-Host "Fertig. App neu starten und Screenshots aufnehmen."
adbx shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
