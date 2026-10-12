param(
    [string]$Version = '20261007-r1',
    [string]$OutputDirectory = 'E:\code\build\dbms'
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
$redisSource = Join-Path $PSScriptRoot 'redis'

if (!(Test-Path -LiteralPath $jar) -or !(Test-Path -LiteralPath (Join-Path $web 'index.html'))) {
    throw '请先构建后端 Jar 和前端 dist。'
}
if (!(Test-Path -LiteralPath $mime)) { throw '缺少 Nginx mime.types。' }
foreach ($redisFile in @('redis-server','redis-cli','REDIS-LICENSE')) {
    if (!(Test-Path -LiteralPath (Join-Path $redisSource $redisFile))) { throw "缺少 ARM64 Redis 文件：$redisFile" }
}
if (Test-Path -LiteralPath $stage) { throw "目标目录已存在：$stage" }
if (Test-Path -LiteralPath $archive) { throw "目标压缩包已存在：$archive" }

New-Item -ItemType Directory -Force -Path $stage | Out-Null
foreach ($directory in @('app','web','config','nginx','redis','sql','sql\parts','run','logs','temp','data')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $stage $directory) | Out-Null
}
Copy-Item -LiteralPath $jar -Destination (Join-Path $stage 'app\jddbms.jar')
robocopy $web (Join-Path $stage 'web') /E /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -gt 7) { throw "复制前端失败：$LASTEXITCODE" }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'config\local.example.env') -Destination (Join-Path $stage 'config')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'nginx\nginx.conf') -Destination (Join-Path $stage 'nginx\nginx.conf.template')
Copy-Item -LiteralPath $mime -Destination (Join-Path $stage 'nginx\mime.types')
foreach ($redisFile in @('redis-server','redis-cli','REDIS-LICENSE')) {
    Copy-Item -LiteralPath (Join-Path $redisSource $redisFile) -Destination (Join-Path $stage 'redis')
}
foreach ($name in @('start.sh','stop.sh','status.sh','README.md')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $stage
}
$parts = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'sql\parts') -File -Filter '*.sql' | Sort-Object Name)
if ($parts.Count -ne 12) { throw "预期 12 份元数据 SQL，实际 $($parts.Count) 份。" }
$combined = Join-Path $stage 'sql\JDDBMS_metadata_dm.sql'
$writer = [IO.StreamWriter]::new($combined, $false, [Text.UTF8Encoding]::new($false))
try {
    $writer.WriteLine('-- JDDBMS 达梦元数据库全新初始化脚本；仅用于空的 JDDBMS 模式。')
    $writer.WriteLine('-- 生成日期：2026-10-07；按下列顺序执行。')
    foreach ($part in $parts) {
        Copy-Item -LiteralPath $part.FullName -Destination (Join-Path $stage 'sql\parts')
        $writer.WriteLine()
        $writer.WriteLine("-- ===== BEGIN $($part.Name) =====")
        $writer.WriteLine([IO.File]::ReadAllText($part.FullName, [Text.Encoding]::UTF8))
        $writer.WriteLine("-- ===== END $($part.Name) =====")
    }
} finally { $writer.Dispose() }

& tar.exe -czf $archive -C $outputRoot $packageName
if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $archive)) { throw '创建 tar.gz 失败。' }
$hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$archive.sha256", "$hash  $packageName.tar.gz`n", [Text.Encoding]::ASCII)
Write-Output "已创建：$archive"
Write-Output "SHA-256：$hash"
