param([switch]$SkipTests, [string]$OutputDirectory = 'dist-2.4.0')
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
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\CANONICAL_ID_1.5.1_2.4.0_적용방법.md') -Destination $OutputDirectory
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\PACKAGE_REPAIR_1.5.2_20261103.md') -Destination $OutputDirectory
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\적용방법.md') -Destination (Join-Path $OutputDirectory 'SECS_적용방법.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\화물입출고_적용방법.md') -Destination (Join-Path $OutputDirectory '화물입출고_적용방법.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\5556_전환방법.md') -Destination (Join-Path $OutputDirectory '5556_전환방법.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\HCACK_1.1.4_적용방법.md') -Destination (Join-Path $OutputDirectory 'HCACK_1.1.4_적용방법.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\PAUSE_RESUME_1.1.5_적용방법.md') -Destination (Join-Path $OutputDirectory 'PAUSE_RESUME_1.1.5_적용방법.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\PAUSE_LIFT_1.1.7_적용방법.md') -Destination (Join-Path $OutputDirectory 'PAUSE_LIFT_1.1.7_적용방법.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\LOADED_ABORT_1.2.0_적용방법.md') -Destination (Join-Path $OutputDirectory 'LOADED_ABORT_1.2.0_적용방법.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\ROBOT_NAME_XML_1.5.0_적용방법.md') -Destination (Join-Path $OutputDirectory 'ROBOT_NAME_XML_1.5.0_적용방법.md')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\SECS\native_host\servostar-robot-names.example.xml') -Destination $OutputDirectory
    $mcsReadme = Join-Path $OutputDirectory 'README.md'
    $mcsReadmeText = [IO.File]::ReadAllText($mcsReadme).Replace('../SECS/native_host/적용방법.md','SECS_적용방법.md').Replace('../SECS/native_host/화물입출고_적용방법.md','화물입출고_적용방법.md').Replace('../SECS/native_host/5556_전환방법.md','5556_전환방법.md').Replace('../SECS/native_host/HCACK_1.1.4_적용방법.md','HCACK_1.1.4_적용방법.md')
    $mcsReadmeText = $mcsReadmeText.Replace('../SECS/native_host/LOADED_ABORT_1.2.0_적용방법.md','LOADED_ABORT_1.2.0_적용방법.md')
    $mcsReadmeText = $mcsReadmeText.Replace('../SECS/native_host/ROBOT_NAME_XML_1.5.0_적용방법.md','ROBOT_NAME_XML_1.5.0_적용방법.md')
    $mcsReadmeText = $mcsReadmeText.Replace('../SECS/native_host/CANONICAL_ID_1.5.1_2.4.0_적용방법.md','CANONICAL_ID_1.5.1_2.4.0_적용방법.md')
    $mcsReadmeText = $mcsReadmeText.Replace('../SECS/native_host/PACKAGE_REPAIR_1.5.2_20261103.md','PACKAGE_REPAIR_1.5.2_20261103.md')
    [IO.File]::WriteAllText($mcsReadme,$mcsReadmeText,[Text.UTF8Encoding]::new($false))
    Get-FileHash -LiteralPath (Join-Path $OutputDirectory 'KukaVirtualMCS.exe') -Algorithm SHA256
} finally { Pop-Location }
