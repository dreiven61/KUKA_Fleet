param([string]$JavaHome='C:\Program Files\Java\jdk-21',
      [string]$SdkJar='C:\work\KUKA\SW\KUKA.AMR_Fleet_2.15.1\STANDARD-API-2.15.5-jar-with-dependencies.jar')
$ErrorActionPreference='Stop'
$nativeRoot=$PSScriptRoot
$nativeReady=Join-Path $nativeRoot ('build\interop-ready-'+[guid]::NewGuid().ToString('N')+'.txt')
$nativeClasspath="$SdkJar;$(Join-Path $nativeRoot 'lib\secs-2.11.0.jar');$(Join-Path $nativeRoot 'build\classes');$(Join-Path $nativeRoot 'build\tests')"
$nativeArgs=@('-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory','-cp',('"'+$nativeClasspath+'"'),'servostar.fleet.transfer.NativeInteropFixture',('"'+$nativeRoot+'"'),('"'+$nativeReady+'"'))
$nativeProcess=Start-Process -FilePath "$JavaHome\bin\java.exe" -ArgumentList $nativeArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $nativeRoot 'build\interop-java.log') -RedirectStandardError (Join-Path $nativeRoot 'build\interop-java-error.log')
try {
    for($nativePoll=0;$nativePoll -lt 100 -and -not (Test-Path -LiteralPath $nativeReady);$nativePoll++){if($nativeProcess.HasExited){throw 'Java fixture failed; inspect build/interop-java-error.log'};Start-Sleep -Milliseconds 100}
    if(-not (Test-Path -LiteralPath $nativeReady)){throw 'Loopback fixture not ready'}
    $nativePort=[int](Get-Content -LiteralPath $nativeReady -Raw)
    dotnet run --project (Join-Path $nativeRoot '..\..\VirtualMCS\Tests\KukaMcs.Tests.csproj') -c Release -- --native-interop $nativePort
    if($LASTEXITCODE -ne 0){throw 'Cross-library HSMS test failed'}
    if(-not $nativeProcess.WaitForExit(5000) -or $nativeProcess.ExitCode -ne 0){throw 'Java fixture did not confirm final ACK'}
    Get-Content -LiteralPath (Join-Path $nativeRoot 'build\interop-java.log') | Select-Object -Last 1
} finally {if(-not $nativeProcess.HasExited){Stop-Process -Id $nativeProcess.Id -Force}}
