param([string]$JavaHome='C:\Program Files\Java\jdk-21')
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$secsJar=Join-Path $root 'tmp\rcs_native_link_inspect\ecs-engine\secs-2.11.0.jar'
if (!(Test-Path -LiteralPath $secsJar)) { throw 'Extracted Fleet Secs2 SDK not found' }
$supportJar=Join-Path $root 'tmp\native_secs_inspect\slf4j-api-1.7.25.jar'
if (!(Test-Path -LiteralPath $supportJar)) { throw 'Fleet SDK logging dependency not found' }
$classpath="$secsJar;$supportJar"
$build=Join-Path $PSScriptRoot 'build'
$classes=Join-Path $build 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources=@(Get-ChildItem (Join-Path $PSScriptRoot 'src'),(Join-Path $PSScriptRoot 'test') -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JavaHome\bin\javac.exe" --release 8 -Xlint:-options -encoding UTF-8 -proc:none -cp $classpath -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Report compile failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.disable.jmx=true' -cp "$classpath;$classes" servostar.fleet.reporting.IbsemReportsTest $build
if ($LASTEXITCODE -ne 0) { throw 'Report body verification failed' }
# This is a body library, NOT an installable ECS node or Interface Platform plugin.
& "$JavaHome\bin\jar.exe" cf (Join-Path $build 'ibsem-report-bodies-0.1.0.jar') -C $classes servostar/fleet/reporting/IbsemReports.class
if ($LASTEXITCODE -ne 0) { throw 'Report packaging failed' }
