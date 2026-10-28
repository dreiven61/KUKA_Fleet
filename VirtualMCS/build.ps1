param([switch]$SkipTests, [string]$OutputDirectory = 'dist-2.1.1')
$ErrorActionPreference = 'Stop'
$OutputDirectory = if ([IO.Path]::IsPathRooted($OutputDirectory)) { [IO.Path]::GetFullPath($OutputDirectory) } else { [IO.Path]::GetFullPath((Join-Path $PSScriptRoot $OutputDirectory)) }
Push-Location $PSScriptRoot
try {
    if (-not $SkipTests) {
        dotnet run --project Tests/KukaMcs.Tests.csproj -c Release
        if ($LASTEXITCODE -ne 0) { throw 'Local HSMS tests failed' }
    }
    dotnet publish App/KukaMcs.App.csproj -c Release -r win-x64 --self-contained false -o $OutputDirectory --nologo
    if ($LASTEXITCODE -ne 0) { throw 'Publish failed' }
    Copy-Item -LiteralPath README.md,THIRD_PARTY_NOTICES.md -Destination $OutputDirectory
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\적용방법.md') -Destination (Join-Path $OutputDirectory 'SECS_적용방법.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\화물입출고_적용방법.md') -Destination (Join-Path $OutputDirectory '화물입출고_적용방법.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\5556_전환방법.md') -Destination (Join-Path $OutputDirectory '5556_전환방법.md')
    $mcsReadme = Join-Path $OutputDirectory 'README.md'
    $mcsReadmeText = [IO.File]::ReadAllText($mcsReadme).Replace('../SECS/native_host/적용방법.md','SECS_적용방법.md').Replace('../SECS/native_host/화물입출고_적용방법.md','화물입출고_적용방법.md').Replace('../SECS/native_host/5556_전환방법.md','5556_전환방법.md')
    [IO.File]::WriteAllText($mcsReadme,$mcsReadmeText,[Text.UTF8Encoding]::new($false))
    Get-FileHash -LiteralPath (Join-Path $OutputDirectory 'KukaVirtualMCS.exe') -Algorithm SHA256
} finally { Pop-Location }
