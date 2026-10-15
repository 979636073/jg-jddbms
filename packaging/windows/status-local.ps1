#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$manifest = Get-Content -Raw -LiteralPath "$root/.local/run/runtime.json" | ConvertFrom-Json
$processes = @(& "$PSScriptRoot/runtime-processes.ps1" -Manifest $manifest)
if ((Get-FileHash -LiteralPath $manifest.jar -Algorithm SHA256).Hash -ne $manifest.jarSha256) { throw '运行 JAR 校验失败。' }
if ($manifest.releaseDirectory) { $null = & "$PSScriptRoot/verify-release.ps1" -Directory $manifest.releaseDirectory }
$health = $false
try { $health = (Invoke-RestMethod 'http://127.0.0.1:28080/captchaImage' -TimeoutSec 3).code -eq 200 } catch { }
$frontendVersion = ''
try {
    $html = (Invoke-WebRequest 'http://127.0.0.1:8080' -TimeoutSec 3).Content
    $frontendVersion = [regex]::Match($html, '<meta name="?jddbms-build"? content="?([^"\s>]+)').Groups[1].Value
} catch { }
[pscustomobject]@{ Mode = $manifest.mode; Revision = $manifest.revision; RunningProcesses = $processes.Count
    BackendHealthy = $health; FrontendVersion = $frontendVersion; JarSha256 = $manifest.jarSha256; ReleaseDirectory = $manifest.releaseDirectory }
if ($processes.Count -ne 2 -or !$health -or !$frontendVersion) { throw '本地服务未完全就绪。' }
if ($manifest.revision -and $frontendVersion -ne $manifest.revision.Substring(0,12)) { throw '前端版本与发布清单不一致。' }
