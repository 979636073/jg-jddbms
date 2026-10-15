#requires -Version 7.0
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$ConfigFile = "$env:USERPROFILE/.codex/jddbms-local.env",
    [string]$ReleaseDirectory,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$ui = Join-Path $root 'jd-dbms-ui'
$java = Join-Path $JavaHome 'bin/java.exe'
if (!(Test-Path $java)) { throw '请通过 -JavaHome 指定 JDK 17 目录。' }
if ((& $java -version 2>&1 | Out-String) -notmatch 'version "17\.') { throw '本地运行基线为 JDK 17。' }
if ((& node --version) -notmatch '^v24\.') { throw '请使用 jd-dbms-ui/.nvmrc 指定的 Node 版本。' }
$jar = Join-Path $root 'jd-dbms-admin/target/jd-dbms-admin.jar'
if ($ReleaseDirectory) {
    $ReleaseDirectory = (Resolve-Path -LiteralPath $ReleaseDirectory).Path
    $release = & "$PSScriptRoot/verify-release.ps1" -Directory $ReleaseDirectory
    $jar = Join-Path $ReleaseDirectory 'backend.jar'
}
if (!(Test-Path $jar)) { throw '缺少后端 JAR，请先执行 mvn --batch-mode verify。' }
if (!$ReleaseDirectory -and !(Test-Path "$ui/node_modules/@vue/cli-service/bin/vue-cli-service.js")) { throw '请先在 jd-dbms-ui 执行 npm ci。' }

$settings = @{}
foreach ($file in @("$root/.env.docker", $ConfigFile)) {
    if (!(Test-Path -LiteralPath $file)) { continue }
    foreach ($line in Get-Content -LiteralPath $file) {
        if ($line -match '^([A-Z][A-Z0-9_]+)=(.*)$' -and $matches[2].Trim()) {
            $settings[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
        }
    }
}
Get-ChildItem Env:JDDBMS* | ForEach-Object { $settings[$_.Name] = $_.Value }
foreach ($name in @('JDDBMS_META_DB_PASSWORD', 'JDDBMS_TOKEN_SECRET', 'JDDBMS_DATASOURCE_ENCRYPTION_KEY')) {
    if (!$settings[$name]) { throw "缺少配置：$name，请在本地配置文件或环境变量中提供。" }
}
if ($settings['JDDBMS_META_DB_URL']) {
    $settings['JDDBMS_META_DB_URL'] = $settings['JDDBMS_META_DB_URL'].Replace('host.docker.internal', '127.0.0.1')
}
if ($CheckOnly) { Write-Output 'JDK、Node、依赖、JAR 与必要配置检查通过。'; return }
if (Get-NetTCPConnection -State Listen -LocalPort 8080,28080 -ErrorAction SilentlyContinue) {
    throw '8080 或 28080 已有服务。请先停止对应进程，再运行此脚本。'
}

$runtime = Join-Path $root '.local/run'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
$runtimeJar = Join-Path $runtime "jd-dbms-admin-$(Get-Date -Format yyyyMMdd-HHmmss).jar"
Copy-Item -LiteralPath $jar -Destination $runtimeJar
$backend = Start-Process -FilePath $java -ArgumentList @('-Dfile.encoding=UTF-8', '-jar', "`"$runtimeJar`"") `
    -WorkingDirectory $root -WindowStyle Hidden -Environment $settings -PassThru `
    -RedirectStandardOutput "$runtime/backend.out.log" -RedirectStandardError "$runtime/backend.err.log"
try {
    $frontendArguments = @('node_modules/@vue/cli-service/bin/vue-cli-service.js', 'serve', '--port', '8080', '--host', '127.0.0.1')
    $frontendMarker = 'node_modules/@vue/cli-service/bin/vue-cli-service.js'
    if ($ReleaseDirectory) {
        $frontendMarker = Join-Path $ReleaseDirectory 'local-web-server.mjs'
        $frontendArguments = @("`"$frontendMarker`"", "`"$ReleaseDirectory/ui`"")
    }
    $frontend = Start-Process -FilePath (Get-Command node).Source `
        -ArgumentList $frontendArguments `
        -WorkingDirectory $ui -WindowStyle Hidden -Environment @{ NODE_OPTIONS = '--openssl-legacy-provider' } -PassThru `
        -RedirectStandardOutput "$runtime/frontend.out.log" -RedirectStandardError "$runtime/frontend.err.log"
    $manifestPath = Join-Path $runtime 'runtime.json'
    if (Test-Path -LiteralPath $manifestPath) { Copy-Item -LiteralPath $manifestPath -Destination "$runtime/previous-run.json" -Force }
    @{
        mode = $(if ($ReleaseDirectory) { 'release' } else { 'development' })
        revision = $(if ($ReleaseDirectory) { $release.revision } else { $null })
        releaseDirectory = $ReleaseDirectory; jar = $runtimeJar
        jarSha256 = (Get-FileHash -LiteralPath $runtimeJar -Algorithm SHA256).Hash
        processes = @(
            @{ pid = $backend.Id; startedAtTicks = $backend.StartTime.ToUniversalTime().Ticks; marker = $runtimeJar }
            @{ pid = $frontend.Id; startedAtTicks = $frontend.StartTime.ToUniversalTime().Ticks; marker = $frontendMarker }
        )
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding utf8NoBOM
    $deadline = (Get-Date).AddSeconds(90)
    do {
        if ($backend.HasExited -or $frontend.HasExited) { throw '服务提前退出，请检查 .local/run 日志。' }
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:28080/captchaImage' -TimeoutSec 2
            $page = Invoke-WebRequest 'http://127.0.0.1:8080' -TimeoutSec 2
            if ($health.code -eq 200 -and $page.StatusCode -eq 200) { break }
        } catch { }
        if ((Get-Date) -gt $deadline) { throw '本地服务健康检查超时。' }
        Start-Sleep -Milliseconds 500
    } while ($true)
} catch {
    Stop-Process -Id $backend.Id -ErrorAction SilentlyContinue
    if ($frontend) { Stop-Process -Id $frontend.Id -ErrorAction SilentlyContinue }
    throw
}
Write-Output "后端 PID=$($backend.Id)，前端 PID=$($frontend.Id)，日志目录=$runtime"
Write-Output '前端 http://localhost:8080；后端健康检查 http://localhost:28080/captchaImage。'
