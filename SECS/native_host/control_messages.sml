// SKI S2F41 control examples. COMMANDID must identify the actual current job.
Cancel:S2F41 W
<L [2]
  <A "CANCEL">
  <L [1] <L [2] <A "COMMANDID"> <A "ACTUAL_COMMAND_ID"> > >
>.
Abort:S2F41 W
<L [2]
  <A "ABORT">
  <L [1] <L [2] <A "COMMANDID"> <A "ACTUAL_COMMAND_ID"> > >
>.
Pause:S2F41 W
<L [2] <A "PAUSE"> <L [0]> >.
Resume:S2F41 W
<L [2] <A "RESUME"> <L [0]> >.
Accepted:S2F42
<L [2] <B 0x04> <L [0]> >.
Duplicate:S2F42
<L [2] <B 0x05> <L [0]> >.
