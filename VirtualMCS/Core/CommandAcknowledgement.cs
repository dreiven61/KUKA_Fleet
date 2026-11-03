using Secs4Net;

namespace KukaMcs.Core;

public sealed record CommandAcknowledgement(uint Code, string ParameterDetail = "")
{
    public bool Accepted => Code is 0 or 4;
    public string Description => Code switch
    {
        0 => "접수 응답(기존 방식) · 실제 실행/완료는 별도 보고 확인",
        1 => "명령 거부 · 지원하지 않는 RCMD",
        2 => "명령 거부 · 현재 상태에서 실행 불가",
        3 => "명령 거부 · 파라미터 오류",
        4 => "명령 접수 · 실행 결과는 별도 이벤트 보고",
        5 => "중복 요청 거부 · 기존 요청의 결과 확인 필요",
        6 => "명령 거부 · 대상 객체 없음",
        _ => "정의되지 않은 응답 · 실행 여부 확인 필요"
    };
    public string Summary => $"HCACK={Code} · {Description}" + (ParameterDetail.Length == 0 ? "" : " · " + ParameterDetail);

    public static CommandAcknowledgement Parse(SecsMessage message, byte function)
    {
        if (message.S != 2 || message.F != function || function is not (42 or 50)) throw new FormatException("S2F42/S2F50 응답 필요");
        Item body = message.SecsItem ?? throw new FormatException("빈 HCACK");
        // Accept the deployed older ECS single-B response, without assuming completion.
        if (body.Format == SecsFormat.Binary && body.Count == 1) return new(Protocol.Number(body));
        if (body.Format != SecsFormat.List || body.Count != 2 || body[0].Format != SecsFormat.Binary || body[0].Count != 1 || body[1].Format != SecsFormat.List)
            throw new FormatException("HCACK는 L[2](B[1], L[n])이어야 합니다.");
        uint code = Protocol.Number(body[0]);
        if (code != 3 && body[1].Count != 0) throw new FormatException("HCACK != 3의 CPACK 목록은 비어 있어야 합니다.");
        var details = new List<string>();
        if (code == 3)
        {
            if (body[1].Count == 0) throw new FormatException("HCACK=3에 오류 파라미터가 없습니다.");
            ReadParameters(body[1], "", details, 0);
        }
        return new(code, string.Join(" / ", details));
    }
    private static void ReadParameters(Item list, string prefix, List<string> details, int depth)
    {
        if (depth > 4 || list.Count == 0) throw new FormatException("잘못된 CPACK 그룹");
        foreach (Item pair in list.Items)
        {
            if (pair.Format != SecsFormat.List || pair.Count != 2 || pair[0].Format != SecsFormat.ASCII) throw new FormatException("CPACK는 L[2](CPNAME, CPACK) 필요");
            string name = prefix + pair[0].GetString();
            if (pair[1].Format == SecsFormat.List) { ReadParameters(pair[1], name + ".", details, depth + 1); continue; }
            if (pair[1].Format != SecsFormat.Binary || pair[1].Count != 1) throw new FormatException("CPACK B[1] 필요");
            uint code = Protocol.Number(pair[1]);
            string meaning = code switch { 0 => "오류 없음", 1 => "파라미터 이름 없음/필수 항목 누락", 2 => "값 오류", 3 => "데이터 형식 오류", _ => "장비 정의 오류" };
            details.Add($"{name}: CPACK={code}({meaning})");
        }
    }
}

/// <summary>ServoStar test-profile extension; not an SKI standard event.</summary>
public sealed record CommandRejection(uint DataId, string CommandId, string Command, uint Hcack, string Reason, string Tsc, string BlockingId)
{
    public string Description => Reason.Split(':')[0] switch
    {
        "TSC_NOT_AUTO" => "운송 시스템이 AUTO가 아니어서 신규 운송 차단",
        "DUPLICATE_COMMAND_ID" => "이미 사용한 COMMANDID",
        "JOB_CAPACITY" => "SECS 작업 기록 한도 도달",
        "UNCERTAIN_INVENTORY_OPERATION" => "화물 입출고 결과 미확인",
        "CARRIER_HAS_PENDING_TRANSFER" => "동일 화물의 기존 운송이 미완료/미확인",
        "CARRIER_NOT_REGISTERED" => "Fleet에 화물이 등록되지 않음",
        "CARRIER_NOT_AT_PORT_OR_CARRIED" => "화물이 출발 포트에 없거나 이미 적재 중",
        "CARRIER_NOT_ON_SOURCE_AMR" => "지정한 AMR이 해당 화물을 적재하고 있지 않음",
        "SOURCE_AMR_NOT_IDLE" => "회수할 AMR이 정지·작업 해제 상태가 아님",
        "SOURCE_AMR_HAS_FLEET_WORK" => "회수할 AMR 또는 화물에 기존 Fleet 작업이 있음",
        "LOADED_RECOVERY_NOT_READY" => "적재 ABORT 대기 도착이 아직 확인되지 않음",
        "RECOVERY_REPORT_NOT_ACKED" => "회수 READY 보고의 호스트 ACK 처리 대기",
        "AMR_NOT_AT_RECOVERY_WAIT_NODE" => "회수 AMR이 지정 대기 노드에 있지 않음",
        "RECOVERY_QUERY_UNAVAILABLE" => "회수 차량 상태 조회 실패",
        "RECOVERY_WAIT_MOVE_ACTIVE" => "적재 대기 이동이 진행 중이거나 확인 필요 · 시스템 제어 거부",
        "SOURCE_AMR_NODE_UNCONFIRMED" => "회수 차량의 실제 지도 노드 확인 불가",
        "AMBIGUOUS_RECOVERY" => "동일 화물·차량의 회수 대기 기록이 중복됨",
        "DESTINATION_OCCUPIED" => "도착 포트에 다른 화물이 있음",
        "SOURCE_INVENTORY_CONFLICT" => "출발 포트 화물 정보 충돌",
        "CARRIER_MODEL_INVALID" => "화물 모델 정보 오류",
        "PREFLIGHT_OR_MONITOR_UNAVAILABLE" => "사전 조회 또는 작업 추적 기록 사용 불가 · 운송 미전송",
        "FLEET_AUTHENTICATION_FAILED" => "Fleet API 인증 실패",
        "FLEET_QUERY_UNAVAILABLE" => "Fleet 상태 조회 실패",
        "FLEET_SUBMIT_REJECTED" => "Fleet 운송 API가 요청 거부",
        "FLEET_CONTROL_REJECTED" => "Fleet 제어 API가 요청 거부",
        "UNKNOWN_COMMAND" => "지원하지 않는 RCMD",
        "COMMAND_NOT_FOUND" => "제어 대상 COMMANDID 없음",
        "CONTROL_ALREADY_PENDING" => "기존 제어 결과 확인 대기",
        "JOB_NOT_ACTIVE" => "제어 가능한 진행 중 작업이 아님",
        "CARGO_LOCATION_UNCONFIRMED" => "실제 화물 위치 조회 실패",
        "PICKUP_STATE_UNCONFIRMED" => "픽업 진행 상태 확인 불가",
        "HANDLING_STATE_UNCONFIRMED" => "이적재 동작 상태 확인 불가 · 제어 차단",
        "LIFT_OPERATION_ACTIVE" => "리프트 이적재 동작 중 CANCEL·PAUSE 금지 · 중단은 ABORT 사용",
        "CANCEL_AFTER_PICKUP_USE_ABORT" => "픽업 시작 후 CANCEL 불가 · ABORT 대상",
        "ABORT_BEFORE_PICKUP_USE_CANCEL" => "픽업 시작 전 ABORT 불가 · CANCEL 대상",
        "PAUSE_REQUIRES_AUTO" => "PAUSE는 AUTO 상태에서 가능",
        "RESUME_REQUIRES_PAUSED" => "RESUME은 PAUSED 확인 후 가능",
        _ => "서버 상세 거부 사유 확인"
    };
    public string Summary => $"{Command} · HCACK={Hcack} · {Description} [{Reason}] · TSC={Tsc}" + (BlockingId.Length == 0 ? "" : $" · 관련 ID={BlockingId}");
    public static CommandRejection? Parse(SecsMessage message)
    {
        if (message.S != 6 || message.F != 11) return null;
        Item? root = message.SecsItem;
        if (root?.Format != SecsFormat.List || root.Count != 3 || Protocol.Number(root[1]) != 9003) return null;
        if (root[0].Format != SecsFormat.U4 || root[0].Count != 1 || root[2].Format != SecsFormat.List || root[2].Count != 1) throw new FormatException("거부 진단 CEID9003 형식 오류");
        Item report = root[2][0];
        if (report.Format != SecsFormat.List || report.Count != 2 || Protocol.Number(report[0]) != 92 || report[1].Format != SecsFormat.List || report[1].Count != 6) throw new FormatException("거부 진단 RPTID92/L[6] 필요");
        Item v = report[1];
        if (v[2].Format != SecsFormat.Binary || v[2].Count != 1 || new[] { 0, 1, 3, 4, 5 }.Any(i => v[i].Format != SecsFormat.ASCII)) throw new FormatException("거부 진단 필드 형식 오류");
        uint code = Protocol.Number(v[2]);
        if (code is not (1 or 2 or 3 or 5 or 6)) throw new FormatException("거부 진단 HCACK 범위 오류");
        return new(Protocol.Number(root[0]), v[0].GetString(), v[1].GetString(), code, v[3].GetString(), v[4].GetString(), v[5].GetString());
    }
}
