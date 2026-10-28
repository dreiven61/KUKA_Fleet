// 구조 검토용. 실제 Station compartment UUID 확정 전 전송 금지.
// 이것은 Workflow 번호를 호출하지 않는 단방향 TRANSFER 요청이다.
S2F49 W
<L [4]
  <U4 [1] 0>
  <A "">
  <A "TRANSFER">
  <L [2]
    <L [2]
      <A "COMMANDINFO">
      <L [3]
        <L [2] <A "COMMANDID"> <A "TRANSFER_TEST_001"> >
        <L [2] <A "PRIORITY"> <U2 [1] 1> >
        <L [2] <A "REPLACE"> <U2 [1] 0> >
      >
    >
    <L [2]
      <A "TRANSFERINFO">
      <L [3]
        <L [2] <A "CARRIERID"> <A "SIM_CARGO_001"> >
        <L [2] <A "SOURCEPORT"> <A "SOURCE_COMPARTMENT_TBD"> >
        <L [2] <A "DESTPORT"> <A "DEST_COMPARTMENT_TBD"> >
      >
    >
  >
>.
