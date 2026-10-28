@echo off
setlocal
chcp 65001 >nul
set "PYTHON_EXE=C:\Users\dreiv\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
set "FLEET_ACCESS_TOKEN="
set "RUN_JOURNAL=%~dp0runs\manual_%RANDOM%_%RANDOM%.json"
echo Fleet API test: virtual robot 1, SIM_RT_01, one round trip 89 - 97 - 89.
echo Before proceeding: open Monitor and keep SIM_REPEAT_01 disabled.
echo Paste the API Access Token when prompted. Input is hidden.
echo Pressing Enter after the token starts the test if preflight checks pass.
echo.
if not exist "%PYTHON_EXE%" (
  echo ERROR: Python runtime was not found at "%PYTHON_EXE%".
  pause
  exit /b 1
)
"%PYTHON_EXE%" "%~dp0run_workflow_once.py" --journal "%RUN_JOURNAL%" --submit --confirmed-virtual --repeat-event-disabled
set "TEST_EXIT_CODE=%ERRORLEVEL%"
echo.
echo Test process exit code: %TEST_EXIT_CODE%
echo Journal: %RUN_JOURNAL%
echo This window stays open so that errors and results can be reviewed.
pause
exit /b %TEST_EXIT_CODE%
