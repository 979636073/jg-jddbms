#requires -Version 7.0
param([Parameter(Mandatory)][string]$Directory)
$ErrorActionPreference = 'Stop'
$release = (Resolve-Path -LiteralPath $Directory).Path
$manifest = Get-Content -Raw -LiteralPath (Join-Path $release 'release.json') | ConvertFrom-Json
if ($manifest.schema -ne 1 -or $manifest.revision -notmatch '^[a-f0-9]{40}$' -or !$manifest.files.Count) {
    throw '发布清单格式无效。'
}
foreach ($file in $manifest.files) {
    $full = [IO.Path]::GetFullPath((Join-Path $release $file.path))
    if (!$full.StartsWith($release + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw '发布清单包含越界路径。'
    }
    if (!(Test-Path -LiteralPath $full -PathType Leaf) -or (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash -ne $file.sha256) {
        throw "发布文件缺失或校验失败：$($file.path)"
    }
}
foreach ($required in @('backend.jar', 'ui/index.html', 'local-web-server.mjs')) {
    if ($required -notin $manifest.files.path) { throw "发布清单缺少：$required" }
}
return $manifest
