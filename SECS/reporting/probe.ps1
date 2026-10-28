param([string]$JavaHome='C:\Program Files\Java\jdk-21')
$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$libraries=Join-Path $root 'tmp\rcs_native_link_inspect\ecs-engine\*'
$sdk=Join-Path $root 'SW\KUKA.AMR_Fleet_2.15.1\STANDARD-API-2.15.5-jar-with-dependencies.jar'
$out=Join-Path $PSScriptRoot 'build\probe'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$collections=Join-Path $root 'tmp\rcs_native_link_inspect\wcs-engine\commons-collections-3.2.2.jar'
$cp="$libraries;$sdk;$collections"
& "$JavaHome\bin\javac.exe" -encoding UTF-8 -proc:none -cp $cp -d $out (Join-Path $PSScriptRoot 'CheckEcsReportSupport.java')
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed' }
$result=& "$JavaHome\bin\java.exe" '-Dlog4j2.disable.jmx=true' '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$out;$cp" CheckEcsReportSupport $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Probe failed' }
$result | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'probe_result.txt') -Encoding utf8
$result
