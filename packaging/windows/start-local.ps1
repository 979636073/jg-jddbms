#requires -Version 7.0
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$ConfigFile = "$env:USERPROFILE/.codex/jddbms-local.env",
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
if (!(Test-Path $jar)) { throw '缺少后端 JAR，请先执行 mvn --batch-mode verify。' }
if (!(Test-Path "$ui/node_modules/@vue/cli-service/bin/vue-cli-service.js")) { throw '请先在 jd-dbms-ui 执行 npm ci。' }

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
    $frontend = Start-Process -FilePath (Get-Command node).Source `
        -ArgumentList @('node_modules/@vue/cli-service/bin/vue-cli-service.js', 'serve', '--port', '8080', '--host', '127.0.0.1') `
        -WorkingDirectory $ui -WindowStyle Hidden -Environment @{ NODE_OPTIONS = '--openssl-legacy-provider' } -PassThru `
        -RedirectStandardOutput "$runtime/frontend.out.log" -RedirectStandardError "$runtime/frontend.err.log"
} catch {
    Stop-Process -Id $backend.Id -ErrorAction SilentlyContinue
    throw
}
Write-Output "后端 PID=$($backend.Id)，前端 PID=$($frontend.Id)，日志目录=$runtime"
Write-Output '前端 http://localhost:8080；后端健康检查 http://localhost:28080/captchaImage。'
