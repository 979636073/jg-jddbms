param(
    [string]$TemplateDirectory = 'E:\code\dbms',
    [string]$OutputDirectory = 'E:\code\build\dbms',
    [string]$Version = '20261008-r9',
    [string]$EncryptionKeySourceFile = '',
    [string]$MetaDbPassword = ''
)
$ErrorActionPreference = 'Stop'
$sourceRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..\..')).Path
$templateRoot = (Resolve-Path -LiteralPath $TemplateDirectory).Path
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
$packageName = "jg-jddbms-kylin-preserved-$Version"
$stage = Join-Path $outputRoot $packageName
$archive = Join-Path $outputRoot "$packageName.tar.gz"
$newJar = Join-Path $sourceRoot 'jd-dbms-admin\target\jd-dbms-admin.jar'
$newWeb = Join-Path $sourceRoot 'jd-dbms-ui\dist'
$sqlParts = Join-Path $sourceRoot 'packaging\linux-arm64\sql\parts'
$existingMenuUpgrade = Join-Path $sourceRoot 'packaging\linux-arm64\sql\JDDBMS_existing_menu_upgrade_dm.sql'
function Remove-ExactlyOnce([string]$content, [string]$pattern, [string]$label) {
    $regex = [regex]::new($pattern)
    if ($regex.Matches($content).Count -ne 1) { throw "配置项数量异常：$label" }
    return $regex.Replace($content, '', 1)
}
function Replace-ExactlyOnce([string]$content, [string]$pattern, [string]$value, [string]$label) {
    $regex = [regex]::new($pattern)
    if ($regex.Matches($content).Count -ne 1) { throw "配置项数量异常：$label" }
    return $regex.Replace($content, [Text.RegularExpressions.MatchEvaluator]{ param($m) $m.Groups[1].Value + $value + $m.Groups[2].Value }, 1)
}

foreach ($required in @($newJar, (Join-Path $newWeb 'index.html'),
    (Join-Path $templateRoot 'application.yml'), (Join-Path $templateRoot 'application-druid.yml'),
    (Join-Path $templateRoot 'bin\start.sh'), (Join-Path $templateRoot 'redis\redis.conf'),
    $sqlParts, $existingMenuUpgrade)) {
    if (!(Test-Path -LiteralPath $required)) { throw "缺少构建输入：$required" }
}
if (Test-Path -LiteralPath $stage) { throw "目标目录已存在：$stage" }
if (Test-Path -LiteralPath $archive) { throw "目标压缩包已存在：$archive" }
New-Item -ItemType Directory -Force -Path $stage | Out-Null

# 完整保留旧包，只避开需要替换的 Jar 与前端目录；不修改任何旧脚本。
robocopy $templateRoot $stage /E /XD (Join-Path $templateRoot 'dist') /XF 'jd-dbms-admin.jar' /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -gt 7) { throw "复制模板失败：$LASTEXITCODE" }
Copy-Item -LiteralPath $newJar -Destination (Join-Path $stage 'jd-dbms-admin.jar')
$stageWeb = Join-Path $stage 'dist'
New-Item -ItemType Directory -Force -Path $stageWeb | Out-Null
robocopy $newWeb $stageWeb /E /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -gt 7) { throw "复制新版前端失败：$LASTEXITCODE" }

$appYmlPath = Join-Path $stage 'application.yml'
$reader = [IO.StreamReader]::new($appYmlPath, $true)
try { $appYml = $reader.ReadToEnd(); $encoding = $reader.CurrentEncoding } finally { $reader.Dispose() }
$portPattern = '(?m)^([ ]{2}port:[ ]*)28080(?=\r?$)'
$portMatches = [regex]::Matches($appYml, $portPattern)
if ($portMatches.Count -ne 1) { throw "application.yml 中预期 1 处后端端口，实际 $($portMatches.Count) 处。" }
$appYml = [regex]::Replace($appYml, $portPattern, [Text.RegularExpressions.MatchEvaluator]{ param($m) $m.Groups[1].Value + '28081' })
$appYml = Remove-ExactlyOnce $appYml '(?m)^  # 版权年份\r?\n  copyrightYear:.*\r?\n' 'dbms.copyrightYear'
$appYml = Remove-ExactlyOnce $appYml '(?m)^    model-base-url:.*\r?\n' 'chat2db.gateway.model-base-url'
$appYml = Remove-ExactlyOnce $appYml '(?m)^  # 服务模块\r?\n  devtools:\r?\n    restart:\r?\n      # 热部署开关\r?\n      enabled:.*\r?\n' 'spring.devtools.restart'
$legacyDmpPattern = '(?s)exportDmp:\r?\n.*?(?=pigenhole:\r?\n)'
$legacyDmpMatches = [regex]::Matches($appYml, $legacyDmpPattern)
if ($legacyDmpMatches.Count -ne 1) { throw "旧 DMP 配置块数量异常：$($legacyDmpMatches.Count)" }
$dmpLines = @(
    'exportDmp:'
    '  dm: ${JDDBMS_DM_EXPORT_TOOL:/opt/dmdbms/bin/dexp}'
    '  oracle: ${JDDBMS_ORACLE_EXPORT_TOOL:exp}'
    'dmp:'
    '  dm:'
    '    user: ${JDDBMS_DM_DMP_USER:}'
    '    password: ${JDDBMS_DM_DMP_PASSWORD:}'
    '  oracle:'
    '    user: ${JDDBMS_ORACLE_DMP_USER:}'
    '    password: ${JDDBMS_ORACLE_DMP_PASSWORD:}'
    'importDmp:'
    '  dm: ${JDDBMS_DM_IMPORT_TOOL:/opt/dmdbms/bin/dimp}'
    '  oracle: ${JDDBMS_ORACLE_IMPORT_TOOL:imp}'
)
$dmpBlock = ($dmpLines -join $(if ($appYml.Contains("`r`n")) { "`r`n" } else { "`n" })) + $(if ($appYml.Contains("`r`n")) { "`r`n" } else { "`n" })
$appYml = [regex]::Replace($appYml, $legacyDmpPattern, [Text.RegularExpressions.MatchEvaluator]{ param($m) $dmpBlock }, 1)
if ($appYml -match '(?m)^jddbms:') { throw '模板已包含 jddbms 配置，请人工核对旧密钥后再构建。' }
if ($EncryptionKeySourceFile) {
    $existing = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $EncryptionKeySourceFile).Path)
    $match = [regex]::Match($existing, 'datasource-encryption-key: \$\{JDDBMS_DATASOURCE_ENCRYPTION_KEY:([0-9a-f]{64})\}')
    if (!$match.Success) { throw '原包中未找到可沿用的数据源加密密钥。' }
    $generatedKey = $match.Groups[1].Value
} else {
    $randomBytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($randomBytes) } finally { $rng.Dispose() }
    $generatedKey = -join ($randomBytes | ForEach-Object { $_.ToString('x2') })
}
$newline = if ($appYml.Contains("`r`n")) { "`r`n" } else { "`n" }
$appYml = $appYml.TrimEnd("`r", "`n") + $newline + $newline +
    '# 新版 JDDBMS 数据源密码加密配置；已有 v2 密文时须沿用原密钥。' + $newline +
    'jddbms:' + $newline +
    ('  datasource-encryption-key: ${JDDBMS_DATASOURCE_ENCRYPTION_KEY:' + $generatedKey + '}') + $newline +
    '  datasource-encryption-legacy-keys: ${JDDBMS_DATASOURCE_ENCRYPTION_LEGACY_KEYS:}' + $newline
[IO.File]::WriteAllText($appYmlPath, $appYml, $encoding)

$druidPath = Join-Path $stage 'application-druid.yml'
$reader = [IO.StreamReader]::new($druidPath, $true)
try { $druidYml = $reader.ReadToEnd(); $druidEncoding = $reader.CurrentEncoding } finally { $reader.Dispose() }
$druidYml = Remove-ExactlyOnce $druidYml '(?m)^# 可视化接口配置\r?\napi:\r?\n    # 牛高工\r?\n    niu:\r?\n\r?\n' 'api.niu'
$druidYml = Remove-ExactlyOnce $druidYml '(?m)^            # 从库数据源\r?\n            slave:\r?\n                # 从数据源开关/默认关闭\r?\n                enabled: false\r?\n                url:.*\r?\n                username:.*\r?\n                password:.*\r?\n' 'disabled slave datasource'
if ($MetaDbPassword) {
    $quotedPassword = "'" + $MetaDbPassword.Replace("'", "''") + "'"
    $druidYml = Replace-ExactlyOnce $druidYml '(?m)^( {16}url:[ ]*)[^\r\n]*(\r?)$' "'jdbc:dm://127.0.0.1:5236/JDDBMS?useUnicode=true&characterEncoding=UTF-8'" 'master JDBC URL'
    $druidYml = Replace-ExactlyOnce $druidYml '(?m)^( {16}username:[ ]*)[^\r\n]*(\r?)$' "'JDDBMS'" 'master username'
    $druidYml = Replace-ExactlyOnce $druidYml '(?m)^( {16}password:[ ]*)[^\r\n]*(\r?)$' $quotedPassword 'master password'
}
[IO.File]::WriteAllText($druidPath, $druidYml, $druidEncoding)

$stageSql = Join-Path $stage 'sql'
New-Item -ItemType Directory -Force -Path (Join-Path $stageSql 'parts') | Out-Null
$parts = @(Get-ChildItem -LiteralPath $sqlParts -File -Filter '*.sql' | Sort-Object Name)
if ($parts.Count -ne 12) { throw "预期 12 份元数据 SQL，实际 $($parts.Count) 份。" }
$combined = Join-Path $stageSql 'JDDBMS_metadata_dm.sql'
$writer = [IO.StreamWriter]::new($combined, $false, [Text.UTF8Encoding]::new($false))
try {
    $writer.WriteLine('-- JDDBMS 达梦元数据库全新初始化脚本；仅用于空的 JDDBMS 模式。')
    foreach ($part in $parts) {
        Copy-Item -LiteralPath $part.FullName -Destination (Join-Path $stageSql 'parts')
        $writer.WriteLine()
        $writer.WriteLine("-- ===== BEGIN $($part.Name) =====")
        $writer.WriteLine([IO.File]::ReadAllText($part.FullName, [Text.Encoding]::UTF8))
        $writer.WriteLine("-- ===== END $($part.Name) =====")
    }
} finally { $writer.Dispose() }
Copy-Item -LiteralPath $existingMenuUpgrade -Destination $stageSql

& docker run --rm --platform linux/arm64 -v "${outputRoot}:/bundle" -v "${PSScriptRoot}:/helper:ro" alpine:3.20 `
    sh /helper/finalize-linux-tar.sh $packageName
if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $archive)) { throw '创建 tar.gz 失败。' }
$hash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$archive.sha256", "$hash  $packageName.tar.gz`n", [Text.Encoding]::ASCII)
Write-Output "已创建：$archive"
Write-Output "SHA-256：$hash"
