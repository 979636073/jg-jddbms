param(
    [string]$TemplateDirectory = 'E:\code\dbms',
    [string]$OutputDirectory = 'E:\code\build\dbms',
    [string]$Version = '20261007-r1'
)
$ErrorActionPreference = 'Stop'
$packageRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$sourceRoot = (Resolve-Path -LiteralPath (Join-Path $packageRoot '..\..')).Path
$templateRoot = (Resolve-Path -LiteralPath $TemplateDirectory).Path
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
$packageName = "jg-jddbms-kylin-template-$Version"
$stage = Join-Path $outputRoot $packageName
$archive = Join-Path $outputRoot "$packageName.tar.gz"
$jar = Join-Path $sourceRoot 'jd-dbms-admin\target\jd-dbms-admin.jar'
$web = Join-Path $sourceRoot 'jd-dbms-ui\dist'
$redis = Join-Path $packageRoot 'redis'

foreach ($required in @($jar, (Join-Path $web 'index.html'), (Join-Path $redis 'redis-server'),
    (Join-Path $redis 'redis-cli'), (Join-Path $redis 'REDIS-LICENSE'),
    (Join-Path $templateRoot 'assets\jar'), (Join-Path $templateRoot 'ssh'))) {
    if (!(Test-Path -LiteralPath $required)) { throw "缺少构建输入：$required" }
}
if (Test-Path -LiteralPath $stage) { throw "目标目录已存在：$stage" }
if (Test-Path -LiteralPath $archive) { throw "目标压缩包已存在：$archive" }

New-Item -ItemType Directory -Force -Path $stage | Out-Null
foreach ($directory in @('dist','assets\jar','ssh','redis\bin','config','nginx','sql\parts','bin','run','logs','temp','data')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $stage $directory) | Out-Null
}
Copy-Item -LiteralPath $jar -Destination (Join-Path $stage 'jd-dbms-admin.jar')
foreach ($copy in @(
    @{ Source = $web; Destination = (Join-Path $stage 'dist') },
    @{ Source = (Join-Path $templateRoot 'assets\jar'); Destination = (Join-Path $stage 'assets\jar') },
    @{ Source = (Join-Path $templateRoot 'ssh'); Destination = (Join-Path $stage 'ssh') }
)) {
    robocopy $copy.Source $copy.Destination /E /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -gt 7) { throw "复制失败：$($copy.Source) ($LASTEXITCODE)" }
}
foreach ($name in @('redis-server','redis-cli')) {
    Copy-Item -LiteralPath (Join-Path $redis $name) -Destination (Join-Path $stage 'redis\bin')
}
Copy-Item -LiteralPath (Join-Path $redis 'REDIS-LICENSE') -Destination (Join-Path $stage 'redis')
$envExample = [IO.File]::ReadAllText((Join-Path $packageRoot 'config\local.example.env'), [Text.Encoding]::UTF8)
if (!$envExample.Contains("JDDBMS_REDIS_PORT='6379'") -or !$envExample.Contains("JDDBMS_BACKEND_PORT='28080'")) {
    throw '配置模板缺少预期端口字段。'
}
$envExample = $envExample.Replace("JDDBMS_REDIS_PORT='6379'", "JDDBMS_REDIS_PORT='26379'")
$envExample = $envExample.Replace("JDDBMS_BACKEND_PORT='28080'", "JDDBMS_BACKEND_PORT='28081'")
[IO.File]::WriteAllText((Join-Path $stage 'config\local.example.env'), $envExample, [Text.UTF8Encoding]::new($false))
Copy-Item -LiteralPath (Join-Path $packageRoot 'nginx\mime.types') -Destination (Join-Path $stage 'nginx')
$nginxTemplate = [IO.File]::ReadAllText((Join-Path $packageRoot 'nginx\nginx.conf'), [Text.Encoding]::UTF8)
if (!$nginxTemplate.Contains('root web;')) { throw 'Nginx 模板缺少静态文件根目录占位。' }
[IO.File]::WriteAllText((Join-Path $stage 'nginx\nginx.conf.template'),
    $nginxTemplate.Replace('root web;', 'root dist;'), [Text.UTF8Encoding]::new($false))
foreach ($name in @('start.sh','stop.sh','status.sh')) {
    Copy-Item -LiteralPath (Join-Path $packageRoot $name) -Destination $stage
}
$binFiles = @{
    'bin-start.sh' = 'start.sh'
    'bin-stop.sh' = 'stop.sh'
    'bin-status.sh' = 'status.sh'
    'bin-install-service.sh' = 'install-service.sh'
}
foreach ($entry in $binFiles.GetEnumerator()) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $entry.Key) -Destination (Join-Path $stage "bin\$($entry.Value)")
}
Copy-Item -LiteralPath (Join-Path $templateRoot 'bin\dm-env.sh') -Destination (Join-Path $stage 'bin')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'README.md') -Destination $stage

$parts = @(Get-ChildItem -LiteralPath (Join-Path $packageRoot 'sql\parts') -File -Filter '*.sql' | Sort-Object Name)
if ($parts.Count -ne 12) { throw "预期 12 份元数据 SQL，实际 $($parts.Count) 份。" }
$combined = Join-Path $stage 'sql\JDDBMS_metadata_dm.sql'
$writer = [IO.StreamWriter]::new($combined, $false, [Text.UTF8Encoding]::new($false))
try {
    $writer.WriteLine('-- JDDBMS 达梦元数据库全新初始化脚本；仅用于空的 JDDBMS 模式。')
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
