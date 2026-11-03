# Actual workflow warning snapshots

The seven job rows are selected, unchanged API response rows from the 2026-10-30 virtual Fleet tests, workflows 6899 and 6900. `captureFile` and `clientTime` identify their original observations under `tmp/alarm_test_20261030/`. Job6899 warned before assignment; job6900 warned after AMR2 picked up its carrier. Both reported `warnFlag=1 / warnCode=200103` and recovered after disabling the temporary forbidden area.

The captured workflow logs contained MOVE/ACTION/NODE/TASK events but no `WARNING` or `WARN_CLEAR` mission-status messages. The raw snapshot, not a synthetic callback, is the regression input. Server timestamps retain the server's 2026-10-24 clock; client times use the Windows 2026-10-30 clock. No authorization/token fields are included.

WorkflowAlarmTest replays these rows through the production polling code and also creates explicitly modified negative fixtures. Its HSMS portion uses localhost and a fixture Fleet client. It does not contact the live Fleet or execute AMR commands.
