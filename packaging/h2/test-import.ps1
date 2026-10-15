#requires -Version 7.0
param([Parameter(Mandatory)][string]$H2Jar, [Parameter(Mandatory)][string]$Java)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
$testDirectory = Join-Path $root ".local/h2-import-tests/$([guid]::NewGuid())"
New-Item -ItemType Directory -Path $testDirectory | Out-Null
$schema = Join-Path $testDirectory 'schema.sql'
$data = Join-Path $testDirectory 'data.sql'
$manifest = Join-Path $testDirectory 'manifest.json'
'CREATE SCHEMA JDDBMS; CREATE TABLE JDDBMS.TEST_BLOB(ID INT PRIMARY KEY, PAYLOAD BLOB);' | Set-Content $schema
"INSERT INTO JDDBMS.TEST_BLOB VALUES(1, X'010203');" | Set-Content $data
@{ schema = 'JDDBMS'; tables = @{ TEST_BLOB = 1 }; lobBytes = @{ 'TEST_BLOB.PAYLOAD' = 3 } } |
    ConvertTo-Json -Depth 4 | Set-Content $manifest
& "$PSScriptRoot/import-h2.ps1" -H2Jar $H2Jar -Java $Java -DataSql $data -Manifest $manifest -SchemaSql $schema -TargetDirectory "$testDirectory/valid"
@{ schema = 'JDDBMS'; tables = @{ TEST_BLOB = 1 }; lobBytes = @{ 'TEST_BLOB.PAYLOAD' = 4 } } |
    ConvertTo-Json -Depth 4 | Set-Content $manifest
$rejected = $false
try {
    & "$PSScriptRoot/import-h2.ps1" -H2Jar $H2Jar -Java $Java -DataSql $data -Manifest $manifest -SchemaSql $schema -TargetDirectory "$testDirectory/invalid"
} catch {
    if ($_.Exception.Message -notlike '*BLOB 长度不一致*') { throw }
    $rejected = $true
}
if (!$rejected) { throw '错误的 BLOB 字节数没有被拒绝。' }
Write-Output '通过：正确 BLOB 恢复成功，错误 BLOB 字节数被拒绝。'
