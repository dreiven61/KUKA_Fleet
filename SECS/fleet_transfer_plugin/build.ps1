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
$jarPath = Join-Path $output 'servostar-mes-transfer-1.5.8.jar'
& "$JavaHome\bin\jar.exe" cfm $jarPath (Join-Path $PSScriptRoot 'MANIFEST.MF') -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Packaging failed' }
python (Join-Path $PSScriptRoot 'package_runtime.py') $SdkJar $jarPath $SecsJar
if ($LASTEXITCODE -ne 0) { throw 'Runtime packaging failed' }
$taskPackageAuditArgs = @($jarPath, '--version', '1.5.8')
$taskPackageBaseline = Join-Path $output 'servostar-mes-transfer-1.5.0.jar'
if (Test-Path -LiteralPath $taskPackageBaseline) { $taskPackageAuditArgs += @('--baseline', $taskPackageBaseline) }
python (Join-Path $PSScriptRoot 'verify_package.py') @taskPackageAuditArgs
if ($LASTEXITCODE -ne 0) { throw 'Required runtime package verification failed' }
$taskPackagedHash = (Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash
$tests = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'test') -Filter '*.java' | ForEach-Object FullName)
& "$JavaHome\bin\javac.exe" -encoding UTF-8 -proc:none -cp "$SdkJar;$SecsJar;$classes" -d $testClasses @tests
if ($LASTEXITCODE -ne 0) { throw 'Test compile failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.ColdStartCatalogTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Cold-start journal/catalog ordering tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.DynamicCatalogTest
if ($LASTEXITCODE -ne 0) { throw 'Dynamic facility catalog tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.VehicleNamesTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'RobotID/VehicleID XML mapping tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.disable.jmx=true' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.TransferTest $jarPath
if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.ReportingTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Reporting tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.VehicleLifecycleTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Vehicle lifecycle tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.SourceReassignmentTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Captured source-vehicle reassignment and destination update tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.DynamicDispatchTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Dynamic dispatch tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.WorkPortsTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Work port tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.StatusMonitorTest
if ($LASTEXITCODE -ne 0) { throw 'Status monitor tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.NativeHostTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Native HSMS/control tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.PauseResumeTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Pause/resume state confirmation tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.CarriedPauseResumeTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Loaded pause/resume query-failure regression tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.HandlingControlTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Handling admission and abort cleanup tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.HandlingWireTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Handling HSMS alarm/control/release tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.LoadedRecoveryTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Loaded ABORT standby and AMR-source recovery tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.HistoricalRecoveryTest
if ($LASTEXITCODE -ne 0) { throw 'Historical loaded ABORT explicit recovery tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.CarriedCompletionTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Actual carried-return completion callback tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.RetainedCompletionTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Retained completed-job recovery tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.CargoTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Inventory/scan tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.RejectionTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'HCACK rejection diagnostics tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.WorkflowAlarmTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Captured workflow warning and HSMS alarm transition tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.SelectedStatusTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'S1F3 status snapshot and pending-report duplicate guard tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.CanonicalIdentityTest
if ($LASTEXITCODE -ne 0) { throw 'Canonical wire identity tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.HostCommandsWireTest $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Host mode, alarm list, update and buzzer interoperability tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' -cp "$SdkJar;$SecsJar;$classes;$testClasses" servostar.fleet.transfer.FixtureRunner servostar.fleet.transfer.VehicleInstallationWireTest
if ($LASTEXITCODE -ne 0) { throw 'Vehicle installation/removal reporting tests failed' }
& "$JavaHome\bin\java.exe" '-Dlog4j2.disable.jmx=true' '-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory' '-Dservostar.transfer.offlineTest=true' -cp "$SdkJar;$testClasses" servostar.fleet.transfer.PluginLoadTest $jarPath $SdkJar
if ($LASTEXITCODE -ne 0) { throw 'Plugin load test failed' }
if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash -cne $taskPackagedHash) { throw 'JAR changed after packaging; isolated validation is no longer valid' }
python (Join-Path $PSScriptRoot 'verify_package.py') @taskPackageAuditArgs --record ($jarPath + '.verified.json')
if ($LASTEXITCODE -ne 0) { throw 'Final delivery artifact verification failed' }
Get-FileHash -LiteralPath $jarPath -Algorithm SHA256
