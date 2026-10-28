@echo off
setlocal
cd /d "%~dp0"
set "PYTHONPATH=%~dp0..\..\tmp\grpc_readonly_deps"
"C:\Users\dreiv\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe" "%~dp0station_registration\register_stations.py"
echo.
pause
