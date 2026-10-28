@echo off
if not exist "%~dp0dist-2.1.1\KukaVirtualMCS.exe" (
  echo dist-2.1.1\KukaVirtualMCS.exe not found. Run build.ps1 first.
  pause
  exit /b 1
)
start "" "%~dp0dist-2.1.1\KukaVirtualMCS.exe"
