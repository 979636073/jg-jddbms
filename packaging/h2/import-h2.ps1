param(
    [Parameter(Mandatory = $true)][string]$H2Jar,
    [Parameter(Mandatory = $true)][string]$DataSql,
    [Parameter(Mandatory = $true)][string]$Manifest,
    [Parameter(Mandatory = $true)][string]$TargetDirectory,
    [string]$SchemaSql = (Join-Path $PSScriptRoot '..\..\jd-dbms-admin\src\main\resources\db\h2\schema.sql'),
    [string]$Java = 'java'
)
$ErrorActionPreference = 'Stop'
foreach ($path in @($H2Jar, $DataSql, $Manifest, $SchemaSql)) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "缺少输入文件：$path" }
}
$source = Get-Content -Raw -LiteralPath $Manifest | ConvertFrom-Json
if ($source.schema -ne 'JDDBMS' -or @($source.tables.PSObject.Properties).Count -eq 0) {
    throw '迁移清单无效。'
}
$directory = [IO.Path]::GetFullPath($TargetDirectory)
$databaseFile = Join-Path $directory 'jddbms.mv.db'
if (Test-Path -LiteralPath $databaseFile) { throw "目标 H2 库已存在：$databaseFile" }
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$url = 'jdbc:h2:file:' + ($directory -replace '\\', '/') + '/jddbms;MODE=MySQL;DATABASE_TO_UPPER=TRUE;DB_CLOSE_ON_EXIT=FALSE'

foreach ($script in @($SchemaSql, $DataSql)) {
    & $Java -cp $H2Jar org.h2.tools.RunScript -url $url -user sa -script $script
    if ($LASTEXITCODE -ne 0) { throw "H2 导入失败：$script；目标库可能只导入了一部分，请勿启动应用。" }
}

$countSql = (@($source.tables.PSObject.Properties | ForEach-Object {
    $name = $_.Name
    if ($name -notmatch '^[A-Z][A-Z0-9_]*$') { throw "无效表名：$name" }
    "SELECT '$name' AS TABLE_NAME, COUNT(*) AS ROW_COUNT FROM `"JDDBMS`".`"$name`""
}) -join ' UNION ALL ') + ' ORDER BY TABLE_NAME'
$countOutput = & $Java -cp $H2Jar org.h2.tools.Shell -url $url -user sa -sql $countSql
if ($LASTEXITCODE -ne 0) { throw 'H2 行数校验失败。' }
$actual = @{}
foreach ($line in $countOutput) {
    if ($line -match '^([A-Z][A-Z0-9_]*)\s*\|\s*(\d+)\s*$') { $actual[$Matches[1]] = [long]$Matches[2] }
}
foreach ($table in $source.tables.PSObject.Properties) {
    if (!$actual.ContainsKey($table.Name) -or $actual[$table.Name] -ne [long]$table.Value) {
        throw "H2 行数不一致：$($table.Name)；目标库未通过验收。"
    }
}

if ($source.PSObject.Properties.Name -contains 'lobBytes') {
    foreach ($item in $source.lobBytes.PSObject.Properties) {
        if ($item.Name -notmatch '^([A-Z][A-Z0-9_]*)\.([A-Z][A-Z0-9_]*)$') { throw "无效 LOB 列名：$($item.Name)" }
        $table, $column = $Matches[1], $Matches[2]
        $sql = "SELECT COALESCE(SUM(OCTET_LENGTH(`"$column`")),0) FROM `"JDDBMS`".`"$table`""
        $output = & $Java -cp $H2Jar org.h2.tools.Shell -url $url -user sa -sql $sql
        if ($LASTEXITCODE -ne 0 -or ($output -join ' ') -notmatch '(?m)^\s*(\d+)\s*$' -or
                [long]$Matches[1] -ne [long]$item.Value) {
            throw "H2 BLOB 长度不一致：$($item.Name)；目标库未通过验收。"
        }
    }
}

$markerSql = 'CREATE TABLE JDDBMS.JDDBMS_BOOTSTRAP(VERSION INT NOT NULL); INSERT INTO JDDBMS.JDDBMS_BOOTSTRAP(VERSION) VALUES(1)'
& $Java -cp $H2Jar org.h2.tools.Shell -url $url -user sa -sql $markerSql | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'H2 初始化标记写入失败。' }
Write-Output "通过：$($actual.Count) 张表；H2 库位于 $databaseFile（含敏感数据，请勿外发）。"
