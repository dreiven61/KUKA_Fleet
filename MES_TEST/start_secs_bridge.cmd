@echo off
setlocal
cd /d "%~dp0"
set "TASK_PYTHON=C:\Users\dreiv\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
set "PYTHONPATH=%~dp0..\tmp\grpc_readonly_deps"
set "FLEET_ACCESS_TOKEN="
echo Keep SIM_REPEAT_01 disabled. Robot 1 must be virtual.
echo This starts the listener only. Send RUN_WORKFLOW in SEComSimulator to move.
echo Leave this window open. Paste the API token at the hidden prompt.
"%TASK_PYTHON%" "%~dp0secs_bridge\server.py" --confirmed-virtual --repeat-event-disabled
echo.
echo Listener stopped. Existing Fleet jobs are NOT cancelled.
pause
