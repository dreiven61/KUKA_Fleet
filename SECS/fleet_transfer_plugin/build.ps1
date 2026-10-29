param([string]$JavaHome = 'C:\Program Files\Java\jdk-21',
      [string]$SdkJar = 'C:\work\KUKA\SW\KUKA.AMR_Fleet_2.15.1\STANDARD-API-2.15.5-jar-with-dependencies.jar',
      [string]$SecsJar = (Join-Path $PSScriptRoot 'lib\secs-2.11.0.jar'))
$ErrorActionPreference = 'Stop'
$output = Join-Path $PSScriptRoot 'build'
$classes = Join-Path $output 'classes'
$testClasses = Join-Path $output 'tests'
foreach ($taskBuildDirectory in @($classes,$testClasses)) {
    $taskResolvedDirectory = [IO.Path]::GetFullPath($taskBuildDirectory)
    $taskBuildRoot = [IO.Path]::GetFullPath($output) + [IO.Path]::DirectorySeparatorChar
    if (-not $taskResolvedDirectory.StartsWith($taskBuildRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Build cleanup path outside build root' }
    if (Test-Path -LiteralPath $taskResolvedDirectory) { Remove-Item -LiteralPath $taskResolvedDirectory -Recurse -Force }
}
New-Item -ItemType Directory -Force -Path $classes,$testClasses | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
& "$JavaHome\bin\javac.exe" --release 8 -Xlint:-options -encoding UTF-8 -proc:none -cp "$SdkJar;$SecsJar" -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compile failed' }
$jarPath = Join-Path $output 'servostar-mes-transfer-1.1.3.jar'
& "$JavaHome\bin\jar.exe" cfm $jarPath (Join-Path $PSScriptRoot 'MANIFEST.MF') -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
python (Join-Path $PSScriptRoot 'package_runtime.py') $SdkJar $jarPath $SecsJar
if ($LASTEXITCODE -ne 0) { throw 'Runtime packaging failed' }
$tests = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test') -Filter '*.java' | ForEach-Object FullName)
& "$JavaHome\bin\javac.exe" -encoding UTF-8 -proc:none -cp "$SdkJar;$SecsJar;$classes" -d $testClasses @tests
if ($LASTEXITCODE -ne 0) { throw 'Test compile failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.disable.jmx=true' -cp "$SdkJar;$classes;$testClasses" servostar.fleet.transfer.TransferTest $jarPath
if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$classes;$testClasses" servostar.fleet.transfer.ReportingTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Reporting tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$classes;$testClasses" servostar.fleet.transfer.VehicleLifecycleTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Vehicle lifecycle tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$classes;$testClasses" servostar.fleet.transfer.DynamicDispatchTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Dynamic dispatch tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$classes;$testClasses" servostar.fleet.transfer.WorkPortsTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Work port tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$classes;$testClasses" servostar.fleet.transfer.StatusMonitorTest
if ($LASTEXITCODE -ne 0) { throw 'Status monitor tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.NativeHostTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Native HSMS/control tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.PauseResumeTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Pause/resume state confirmation tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.CargoTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Inventory/scan tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.disable.jmx=true' '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' '-Dservostar.transfer.offlineTest=true' -cp "$SdkJar;$testClasses" servostar.fleet.transfer.PluginLoadTest $jarPath $SdkJar
if ($LASTEXITCODE -ne 0) { throw 'Plugin load test failed' }
Get-FileHash -LiteralPath $jarPath -Algorithm SHA256
