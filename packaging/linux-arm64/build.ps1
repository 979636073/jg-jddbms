param(
    [string]$Version = '20261014-arm-r1',
    [string]$OutputDirectory = 'D:\code\build\dbms'
)
$ErrorActionPreference = 'Stop'
$sourceRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
$packageName = "jg-jddbms-linux-arm64-$Version"
$stage = Join-Path $outputRoot $packageName
$archive = Join-Path $outputRoot "$packageName.tar.gz"
$jar = Join-Path $sourceRoot 'jd-dbms-admin\target\jd-dbms-admin.jar'
$web = Join-Path $sourceRoot 'jd-dbms-ui\dist'
$mime = Join-Path $PSScriptRoot 'nginx\mime.types'
$nginxSource = Join-Path $PSScriptRoot 'nginx'
$redisSource = Join-Path $PSScriptRoot 'redis'

if (!(Test-Path -LiteralPath $jar) -or !(Test-Path -LiteralPath (Join-Path $web 'index.html'))) {
    throw '请先构建后端 Jar 和前端 dist。'
}
if (!(Test-Path -LiteralPath $mime)) { throw '缺少 Nginx mime.types。' }
foreach ($nginxFile in @('nginx','NGINX-LICENSE','PCRE2-LICENSE','ZLIB-LICENSE')) {
    if (!(Test-Path -LiteralPath (Join-Path $nginxSource $nginxFile))) { throw "缺少 ARM64 Nginx 文件：$nginxFile" }
}
foreach ($redisFile in @('redis-server','redis-cli','REDIS-LICENSE')) {
    if (!(Test-Path -LiteralPath (Join-Path $redisSource $redisFile))) { throw "缺少 ARM64 Redis 文件：$redisFile" }
}
if (Test-Path -LiteralPath $stage) { throw "目标目录已存在：$stage" }
if (Test-Path -LiteralPath $archive) { throw "目标压缩包已存在：$archive" }

New-Item -ItemType Directory -Force -Path $stage | Out-Null
foreach ($directory in @('app','assets\jar','web','config','nginx','redis','docker','run','logs','temp','data','data\h2')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $stage $directory) | Out-Null
}
Copy-Item -LiteralPath $jar -Destination (Join-Path $stage 'app\jddbms.jar')
Copy-Item -Path (Join-Path $sourceRoot 'assets\jar\*.jar') -Destination (Join-Path $stage 'assets\jar')
foreach ($name in @('entrypoint.sh','nginx.conf')) {
    Copy-Item -LiteralPath (Join-Path $sourceRoot "docker\$name") -Destination (Join-Path $stage 'docker')
}
foreach ($name in @('Dockerfile.backend','Dockerfile.frontend')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $stage 'docker')
}
robocopy $web (Join-Path $stage 'web') /E /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -gt 7) { throw "复制前端失败：$LASTEXITCODE" }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'config\local.example.env') -Destination (Join-Path $stage 'config')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'nginx\nginx.conf') -Destination (Join-Path $stage 'nginx\nginx.conf.template')
Copy-Item -LiteralPath $mime -Destination (Join-Path $stage 'nginx\mime.types')
foreach ($nginxFile in @('nginx','NGINX-LICENSE','PCRE2-LICENSE','ZLIB-LICENSE')) {
    Copy-Item -LiteralPath (Join-Path $nginxSource $nginxFile) -Destination (Join-Path $stage 'nginx')
}
foreach ($redisFile in @('redis-server','redis-cli','REDIS-LICENSE')) {
    Copy-Item -LiteralPath (Join-Path $redisSource $redisFile) -Destination (Join-Path $stage 'redis')
}
foreach ($name in @('start.sh','stop.sh','status.sh','http-check.sh','metadata-db.sh','README.md')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $stage
}
foreach ($name in @('start.sh','stop.sh','status.sh','http-check.sh','metadata-db.sh','config/local.example.env')) {
    $scriptPath = Join-Path $stage $name
    $content = [IO.File]::ReadAllText($scriptPath)
    [IO.File]::WriteAllText($scriptPath, ($content -replace "`r`n", "`n"), [Text.UTF8Encoding]::new($false))
}
$artifacts = [ordered]@{}
foreach ($relative in @('app/jddbms.jar','web/index.html','nginx/nginx','redis/redis-server','redis/redis-cli')) {
    $artifacts[$relative] = (Get-FileHash -LiteralPath (Join-Path $stage $relative) -Algorithm SHA256).Hash.ToLowerInvariant()
}
$gitHead = (& git -C $sourceRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw '无法读取源码 Git 版本。' }
$gitChanges = @(& git -C $sourceRoot status --porcelain)
if ($LASTEXITCODE -ne 0) { throw '无法读取工作区状态。' }
$versionInfo = [ordered]@{
    version = $Version
    builtAt = (Get-Date).ToUniversalTime().ToString('o')
    gitHead = $gitHead
    includesUncommittedChanges = ($gitChanges.Count -gt 0)
    sha256 = $artifacts
}
[IO.File]::WriteAllText((Join-Path $stage 'VERSION.json'), ($versionInfo | ConvertTo-Json -Depth 4), [Text.UTF8Encoding]::new($false))

& tar.exe -czf $archive -C $outputRoot $packageName
if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $archive)) { throw '创建 tar.gz 失败。' }
$hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$archive.sha256", "$hash  $packageName.tar.gz`n", [Text.Encoding]::ASCII)
Write-Output "已创建：$archive"
Write-Output "SHA-256：$hash"
