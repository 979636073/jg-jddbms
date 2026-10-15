#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$file = Join-Path $root '.local/run/runtime.json'
if (!(Test-Path -LiteralPath $file)) { throw '没有受管运行清单；请核实端口进程，不会自动结束未知服务。' }
$manifest = Get-Content -Raw -LiteralPath $file | ConvertFrom-Json
$processes = @(& "$PSScriptRoot/runtime-processes.ps1" -Manifest $manifest)
foreach ($process in $processes) { Stop-Process -Id $process.Id }
Write-Output "已停止 $($processes.Count) 个与运行清单匹配的本地进程。"
