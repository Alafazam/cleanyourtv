# Windows version of restore.sh: re-enable every package listed in a debloat file, restore the
# stock launcher as home and reset animation scales. Pass the same list you used with disable.ps1.
#
# Usage: tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt [-Stock stock.launcher.package]
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$List,
    [string]$Stock = 'com.google.android.tvlauncher',
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
# Pipe plain UTF-8 to native commands; the default here prepends a BOM that corrupts the first command.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$repo = Split-Path -Parent $PSScriptRoot
$List = (Resolve-Path $List).Path
$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

$pkgs = Get-Content $List | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ } | ForEach-Object { ($_ -split '\s+')[0] }
# Same check as disable.ps1: the list is contributor-supplied (#165).
$bad = @($pkgs | Where-Object { $_ -notmatch '^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' })
if ($bad.Count -gt 0) {
    throw "refusing $List, not a package name: $($bad -join ', ')"
}
$cmds = @($pkgs | ForEach-Object { "pm enable $_" }) + @(
    "pm enable $Stock",
    # resolve the stock HOME component on the TV (Android 9 needs pkg/activity, not just pkg)
    ('cmd package set-home-activity --user 0 $(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME ' + $Stock + ' | tail -n 1)'),
    'settings put global window_animation_scale 1.0',
    'settings put global transition_animation_scale 1.0',
    'settings put global animator_duration_scale 1.0',
    'pm list packages -d'
)
$cmds | & $py -I (Join-Path $repo 'tools\tv.py') --batch
