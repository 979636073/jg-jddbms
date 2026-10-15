#requires -Version 7.0
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$OutputDirectory,
    [string]$TestUserHome
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path "$PSScriptRoot/../..").Path
Push-Location $root
try {
    if (git status --porcelain) { throw '发布包必须从干净的已提交代码构建。' }
    $revision = (git rev-parse HEAD).Trim()
    if (!$OutputDirectory) { $OutputDirectory = Join-Path $root ".local/releases/$($revision.Substring(0,12))" }
    $destination = [IO.Path]::GetFullPath($OutputDirectory)
    if (Test-Path -LiteralPath $destination) { throw '发布目录已存在，请选择新目录，保留旧包供回退。' }
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome/bin;$env:Path"
    if (!$TestUserHome) { $TestUserHome = Join-Path $root '.local/test-home' }
    & mvn --batch-mode "-DargLine=-Duser.home=$TestUserHome" verify
    if ($LASTEXITCODE) { throw '后端验证失败。' }
    Push-Location (Join-Path $root 'jd-dbms-ui')
    try {
        foreach ($script in @('test:core', 'lint', 'build')) {
            & npm run $script
            if ($LASTEXITCODE) { throw "前端 $script 失败。" }
        }
    } finally { Pop-Location }
    if ((git rev-parse HEAD).Trim() -ne $revision -or (git status --porcelain)) { throw '构建期间源码发生变化，停止生成发布包。' }
    New-Item -ItemType Directory -Path $destination | Out-Null
    Copy-Item -LiteralPath "$root/jd-dbms-admin/target/jd-dbms-admin.jar" -Destination "$destination/backend.jar"
    Copy-Item -LiteralPath "$root/jd-dbms-ui/dist" -Destination "$destination/ui" -Recurse
    Copy-Item -LiteralPath "$PSScriptRoot/local-web-server.mjs" -Destination $destination
    $files = @(Get-ChildItem -LiteralPath $destination -Recurse -File | ForEach-Object {
        @{ path = [IO.Path]::GetRelativePath($destination, $_.FullName).Replace('\', '/'); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
    })
    @{ schema = 1; revision = $revision; createdAt = (Get-Date).ToUniversalTime().ToString('o'); files = $files } |
        ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$destination/release.json" -Encoding utf8NoBOM
    $null = & "$PSScriptRoot/verify-release.ps1" -Directory $destination
    Write-Output "发布包验证完成：$destination；Git=$revision（未包含本地密钥、配置及元数据库）。"
} finally { Pop-Location }
