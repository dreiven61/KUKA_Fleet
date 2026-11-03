# Actual carried-return callback capture

Collected from the operator's read-only export on 2026-10-30, workflow6894, command4BC594E5FF7F4BCBAADC8E4FBB2389A1. Original server event timestamps are retained (server configured to2026-10-24); they are not client elapsed-time measurements.

The JSONL contains this command's original RESERVE, ACCEPTED and EVENT records. It has sourceVehicle2 and sourceNode105, MOVE_BEGIN/MOVE_END at the carried source, UP_CONTAINER ACTION_END **without ACTION_BEGIN**, destination travel and DOWN_CONTAINER BEGIN/END, then TASK_COMPLETED. No token or authorization headers are included.

CarriedCompletionTest replays this exact failure shape and rejects modified records with missing ownership proof, changed identity, stream gaps, missing terminal stages or conflicting final-state fixtures. It does not connect to the Fleet server or send robot actions.
