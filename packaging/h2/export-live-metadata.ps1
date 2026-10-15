param(
    [Parameter(Mandatory = $true)][string]$TokenFile,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$BaseUrl = 'http://127.0.0.1:28080',
    [Parameter(Mandatory = $true)][long]$DataSourceId,
    [string]$SchemaName = 'JDDBMS'
)
$ErrorActionPreference = 'Stop'
if ($SchemaName -notmatch '^[A-Z][A-Z0-9_]*$' -or $DataSourceId -le 0) {
    throw '数据源或模式参数无效。'
}
$match = [regex]::Match([IO.File]::ReadAllText($TokenFile), '(?i)Bearer\s+([A-Za-z0-9._~+\-/=]+)')
if (!$match.Success) { throw '认证文件中没有 Bearer 令牌。' }
$headers = @{ Authorization = "Bearer $($match.Groups[1].Value)" }
$base = $BaseUrl.TrimEnd('/')
$consoleId = 920141
$batchSize = 200

function Invoke-ReadQuery([string]$sql, [string]$label) {
    $body = @{
        dataSourceId = $DataSourceId; schemaName = $SchemaName; consoleId = $consoleId
        sql = $sql; isPage = $false; pageSizeAll = $true; isColumn = $false
    } | ConvertTo-Json -Compress
    try {
        $result = Invoke-RestMethod -Uri "$base/api/rdb/dml/execute" -Method Post -Headers $headers `
            -Body ([Text.Encoding]::UTF8.GetBytes($body)) -ContentType 'application/json'
    } catch { throw "读取失败：$label" }
    if (!$result.success -or !$result.data[0].success) { throw "读取失败：$label" }
    return $result.data[0]
}

function Get-BlobBytes([string]$table, [string]$column, [string]$rowId, [int]$expectedLength) {
    $body = @{
        dataSourceId = $DataSourceId; schemaName = $SchemaName
        tableName = $table; blobColumn = $column; rowId = $rowId
    } | ConvertTo-Json -Compress
    try {
        $result = Invoke-RestMethod -Uri "$base/api/rdb/dml/getBlobData" -Method Post -Headers $headers `
            -Body ([Text.Encoding]::UTF8.GetBytes($body)) -ContentType 'application/json'
        if (!$result.success) { throw 'API error' }
        $bytes = [Convert]::FromBase64String([string]$result.data)
    } catch { throw "BLOB 读取失败：$table.$column" }
    if ($bytes.Length -ne $expectedLength) { throw "BLOB 长度不一致：$table.$column" }
    return ,$bytes
}

function Quote-Name([string]$name) { return '"' + $name.Replace('"', '""') + '"' }
function Quote-Text([string]$value) { return "'" + $value.Replace("'", "''") + "'" }

$metadata = Invoke-ReadQuery "SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM ALL_TAB_COLUMNS WHERE OWNER='$SchemaName' ORDER BY TABLE_NAME, COLUMN_ID" '字段清单'
$columnsByTable = [ordered]@{}
foreach ($row in $metadata.dataList) {
    $table = [string]$row[1]
    if (!$columnsByTable.Contains($table)) { $columnsByTable[$table] = [System.Collections.ArrayList]::new() }
    [void]$columnsByTable[$table].Add([pscustomobject]@{ Name = [string]$row[2]; Type = ([string]$row[3]).ToUpperInvariant() })
}
if ($columnsByTable.Count -eq 0) { throw '未读取到任何表。' }
$tables = @($columnsByTable.Keys | Sort-Object)
$tables = @($tables | Where-Object { $_ -ne 'CHART' }) + @($tables | Where-Object { $_ -eq 'CHART' })
$target = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath (Join-Path $target 'JDDBMS_data_h2.sql')) { throw '目标数据脚本已存在。' }
New-Item -ItemType Directory -Path $target -Force | Out-Null
$sqlPath = Join-Path $target 'JDDBMS_data_h2.sql'
$manifestPath = Join-Path $target 'JDDBMS_data_h2_manifest.json'
$writer = [IO.StreamWriter]::new($sqlPath, $false, [Text.UTF8Encoding]::new($false))
$manifest = [ordered]@{ schema = $SchemaName; exportedAt = (Get-Date).ToUniversalTime().ToString('o'); tables = [ordered]@{}; lobBytes = [ordered]@{} }
try {
    $writer.WriteLine('-- 达梦 JDDBMS 当前数据迁入 H2；目标必须是空表。含账号、加密连接配置等敏感数据。')
    $writer.WriteLine('SET SCHEMA JDDBMS;')
    foreach ($table in $tables) {
        $quotedTable = (Quote-Name $SchemaName) + '.' + (Quote-Name $table)
        $countResult = Invoke-ReadQuery "SELECT COUNT(*) FROM $quotedTable" "$table 行数"
        $expected = [long]$countResult.dataList[0][1]
        $columns = @($columnsByTable[$table])
        $selects = [System.Collections.ArrayList]::new()
        [void]$selects.Add('ROWID AS SOURCE_ROWID')
        foreach ($column in $columns) {
            $name = Quote-Name $column.Name
            if ($column.Type -in @('DATETIME', 'TIMESTAMP')) {
                [void]$selects.Add("TO_CHAR($name, 'YYYY-MM-DD HH24:MI:SS.FF6') AS $name")
            } elseif ($column.Type -eq 'DATE') {
                [void]$selects.Add("TO_CHAR($name, 'YYYY-MM-DD HH24:MI:SS') AS $name")
            } else {
                [void]$selects.Add($name)
            }
        }
        $lobColumns = @($columns | Where-Object { $_.Type -in @('TEXT', 'BLOB') })
        foreach ($column in $lobColumns) {
            [void]$selects.Add('DBMS_LOB.GETLENGTH(' + (Quote-Name $column.Name) + ') AS ' + (Quote-Name ('LEN_' + $column.Name)))
        }
        $columnSql = ($columns | ForEach-Object { Quote-Name $_.Name }) -join ', '
        $writer.WriteLine("-- $table : $expected rows")
        $actual = [long]0
        while ($actual -lt $expected) {
            $query = 'SELECT ' + ($selects -join ', ') + " FROM $quotedTable ORDER BY ROWID LIMIT $actual, $batchSize"
            $page = Invoke-ReadQuery $query "$table 第 $actual 行"
            $rows = @($page.dataList)
            if ($rows.Count -eq 0) { throw "分页提前结束：$table" }
            foreach ($row in $rows) {
                if ($row.Count -ne (2 + $columns.Count + $lobColumns.Count)) { throw "字段数量不符：$table" }
                $sourceRowId = [string]$row[1]
                $values = [System.Collections.ArrayList]::new()
                for ($index = 0; $index -lt $columns.Count; $index++) {
                    $column = $columns[$index]
                    $value = $row[2 + $index]
                    if ($null -eq $value) { [void]$values.Add('NULL'); continue }
                    $value = [string]$value
                    switch ($column.Type) {
                        { $_ -in @('BIGINT', 'INT', 'INTEGER', 'TINYINT') } {
                            if ($value -notmatch '^-?[0-9]+$') { throw "数值无效：$table.$($column.Name)" }
                            [void]$values.Add($value); break
                        }
                        'BIT' {
                            if ($value -match '^(1|true)$') { [void]$values.Add('TRUE') }
                            elseif ($value -match '^(0|false)$') { [void]$values.Add('FALSE') }
                            else { throw "布尔值无效：$table.$($column.Name)" }
                            break
                        }
                        'BLOB' {
                            $lobIndex = [array]::IndexOf(@($lobColumns.Name), $column.Name)
                            $length = [int]$row[2 + $columns.Count + $lobIndex]
                            $bytes = Get-BlobBytes $table $column.Name $sourceRowId $length
                            $lobKey = "$table.$($column.Name)"
                            if (!$manifest.lobBytes.Contains($lobKey)) { $manifest.lobBytes[$lobKey] = [long]0 }
                            $manifest.lobBytes[$lobKey] += $bytes.Length
                            [void]$values.Add("X'$([Convert]::ToHexString($bytes))'"); break
                        }
                        'TEXT' {
                            $lobIndex = [array]::IndexOf(@($lobColumns.Name), $column.Name)
                            $length = [int]$row[2 + $columns.Count + $lobIndex]
                            if ($value.Length -ne $length) { throw "TEXT 长度不一致：$table.$($column.Name)" }
                            [void]$values.Add((Quote-Text $value)); break
                        }
                        'DATE' {
                            if ($value -notmatch '^\d{4}-\d{2}-\d{2} ') { throw "日期无效：$table.$($column.Name)" }
                            [void]$values.Add("TIMESTAMP '$value'"); break
                        }
                        { $_ -in @('DATETIME', 'TIMESTAMP') } {
                            if ($value -notmatch '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{6}$') { throw "时间无效：$table.$($column.Name)" }
                            [void]$values.Add("TIMESTAMP '$value'"); break
                        }
                        default { [void]$values.Add((Quote-Text $value)); break }
                    }
                }
                $writer.WriteLine("INSERT INTO $quotedTable ($columnSql) VALUES ($($values -join ', '));")
                $actual++
            }
        }
        $after = Invoke-ReadQuery "SELECT COUNT(*) FROM $quotedTable" "$table 二次行数"
        if ([long]$after.dataList[0][1] -ne $expected -or $actual -ne $expected) {
            throw "源表行数在导出期间变化：$table"
        }
        $manifest.tables[$table] = $actual
        Write-Host "$table : $actual"
    }
    $writer.WriteLine('COMMIT;')
} finally { $writer.Dispose() }
[IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
Write-Host "完成：$($manifest.tables.Count) 张表；SQL 文件位于 $sqlPath（含敏感数据，请勿外发）。"
