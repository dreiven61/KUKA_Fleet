@echo off
setlocal
cd /d "%~dp0"
set "PYTHONPATH=%~dp0..\..\tmp\grpc_readonly_deps"
echo Registers only SECS_ST_89/PORT_89 and SECS_ST_97/PORT_97 in the Station service.
echo No transport orders or map updates are sent.
"C:\Users\dreiv\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" "%~dp0station_registration\register_stations.py" --apply
echo.
pause
