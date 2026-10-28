using System.Text;
using System.Text.Json;

namespace KukaMcs.Core;

public enum JobPhase { Sending, Acknowledged, Initiated, Transferring, Completed, AbnormalEnded, Rejected, Uncertain, ManuallyReleased, Canceling, Aborting, Paused, Canceled, Aborted }
public sealed record TransferJob(TransferRequest Request, DateTimeOffset CreatedAt, JobPhase Phase, string Detail, uint? LastCeid = null, uint? ResultCode = null, DateTimeOffset? UpdatedAt = null, string? VehiclePort = null, string? VehicleId = null, string PendingControl = "", JobPhase? BeforeControl = null)
{
    public bool IsPending => Phase is JobPhase.Sending or JobPhase.Acknowledged or JobPhase.Initiated or JobPhase.Transferring or JobPhase.Uncertain or JobPhase.Canceling or JobPhase.Aborting or JobPhase.Paused;
    public string PhaseText => Phase switch
    {
        JobPhase.Sending => "전송 시도", JobPhase.Acknowledged => "응답 수신 · 접수 확인 대기", JobPhase.Initiated => "시작 보고 수신",
        JobPhase.Transferring => "운송 진행", JobPhase.Completed => "완료 보고 수신", JobPhase.AbnormalEnded => "비정상 종료 보고",
        JobPhase.Rejected => "명령 거부", JobPhase.Uncertain => "상태 확인 필요", JobPhase.ManuallyReleased => "사용자 대기 해제",
        JobPhase.Canceling => "취소 결과 대기", JobPhase.Aborting => "강제중단 결과 대기", JobPhase.Paused => "일시중지", JobPhase.Canceled => "취소 완료", JobPhase.Aborted => "강제중단 완료", _ => Phase.ToString()
    };
}

public sealed class JobStore
{
    private readonly object _sync = new();
    private readonly string _path;
    private readonly Dictionary<string, TransferJob> _jobs = new(StringComparer.Ordinal);
    private readonly Dictionary<string, List<TransferJob>> _history = new(StringComparer.Ordinal);
    public event Action<TransferJob>? Changed;
    public JobStore(string path)
    {
        _path = path; Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path))!);
        if (File.Exists(path))
            foreach (string line in File.ReadLines(path))
                if (!string.IsNullOrWhiteSpace(line)) { var job = JsonSerializer.Deserialize<TransferJob>(line) ?? throw new IOException("작업 기록이 손상되었습니다."); Remember(job); }
        foreach (TransferJob job in Snapshot().Where(j => j.IsPending)) Save(job with { Phase = JobPhase.Uncertain, Detail = "프로그램 재시작: Fleet 상태 확인 후 대기를 해제하세요." });
    }
    public IReadOnlyList<TransferJob> Snapshot() { lock (_sync) return _jobs.Values.OrderByDescending(j => j.CreatedAt).ToArray(); }
    public IReadOnlyList<TransferJob> History(string commandId) { lock (_sync) return _history.TryGetValue(commandId, out var rows) ? rows.ToArray() : []; }
    public TransferJob Reserve(TransferRequest request)
    {
        request.Validate();
        TransferJob job;
        lock (_sync)
        {
            if (_jobs.ContainsKey(request.CommandId)) throw new InvalidOperationException("이미 사용한 COMMANDID입니다.");
            if (_jobs.Values.Any(j => j.Request.CarrierId == request.CarrierId && j.IsPending)) throw new InvalidOperationException("이 화물의 기존 작업이 미확인 상태입니다. 완료 보고를 기다리거나 Fleet에서 확인한 뒤 대기를 해제하세요.");
            job = SaveLocked(new(request, DateTimeOffset.Now, JobPhase.Sending, "S2F49 전송 시도. 자동 재전송 없음."));
        }
        Changed?.Invoke(job); return job;
    }
    public void Acknowledge(string id, uint ack) => Update(id, job => job.Phase == JobPhase.Sending
        ? job with { Phase = ack is 0 or 4 ? JobPhase.Acknowledged : ack == 5 ? JobPhase.Uncertain : JobPhase.Rejected, Detail = ack == 0 ? "S2F50=0 (기존 ECS의 선행 응답). 실제 운송 접수/완료는 별도 확인." : $"S2F50 HCACK={ack}" + (ack == 5 ? " · 기존 명령 상태 확인 필요. 새 ID로 재전송하지 마세요." : "") } : job);
    public void BeginControl(string id, string command) => Update(id, j =>
    {
        if (!j.IsPending || j.Phase == JobPhase.Uncertain || !string.IsNullOrEmpty(j.PendingControl)) throw new InvalidOperationException("이 작업에는 제어 명령을 추가할 수 없습니다. 기존 결과부터 확인하세요.");
        return j with { PendingControl = command, BeforeControl = j.Phase, Detail = $"{command} 전송 시도 · 자동 재전송 없음" };
    });
    public void ControlAcknowledge(string id, string command, uint ack) => Update(id, j =>
    {
        if (!j.IsPending || j.PendingControl != command) return j;
        if (ack == 5) return j with { Phase = JobPhase.Uncertain, Detail = $"{command} HCACK=5 · 기존 제어 요청의 결과 확인 필요. 자동 재전송 없음." };
        return j with { PendingControl = ack == 4 ? command : "", Phase = ack == 4 ? command == "CANCEL" ? JobPhase.Canceling : command == "ABORT" ? JobPhase.Aborting : j.Phase : j.BeforeControl ?? j.Phase,
            Detail = $"{command} 응답 HCACK={ack} · 실제 결과 보고 별도 확인" };
    });
    public bool Report(TransferReport report)
    {
        bool matched = false;
        Update(report.CommandId, job =>
        {
            if (job.Request.CarrierId != report.CarrierId || job.Request.SourcePort != report.SourcePort || job.Request.DestPort != report.DestPort) return job;
            matched = true;
            if (!job.IsPending) return job;
            if (report.Ceid == 207 && report.ResultCode == 0 && report.CarrierLocation != job.Request.DestPort) return job;
            JobPhase phase = report.Ceid switch { 201 => JobPhase.Aborted, 204 => JobPhase.Canceled, 203 => JobPhase.Aborting, 206 => JobPhase.Canceling, 202 or 205 => job.BeforeControl ?? JobPhase.Transferring,
                209 => JobPhase.Paused, 210 => JobPhase.Transferring, 208 when job.Phase != JobPhase.Transferring => JobPhase.Initiated, 211 => JobPhase.Transferring, 207 when report.ResultCode == 0 => JobPhase.Completed, 207 => JobPhase.AbnormalEnded, _ => job.Phase };
            bool controlEnd = report.Ceid is 201 or 202 or 204 or 205 or 209 or 210;
            if (!controlEnd && report.Ceid != 207 && (job.PendingControl.Length != 0 || job.Phase == JobPhase.Paused)) phase = job.Phase;
            return job with { Phase = phase, LastCeid = report.Ceid, ResultCode = report.ResultCode, PendingControl = controlEnd ? "" : job.PendingControl,
                Detail = $"{Protocol.EventText(report.Ceid)} · CEID {report.Ceid}, Result {report.ResultCode}, CarrierLoc {report.CarrierLocation}" };
        });
        return matched;
    }
    public bool ReportVehicle(VehicleReport report)
    {
        bool matched = false;
        Update(report.CommandId, job =>
        {
            string port = report.Ceid is 602 or 603 or 604 or 605 ? job.Request.SourcePort : job.Request.DestPort;
            bool validPort = report.Ceid == 601 ? report.TransferPort == job.Request.SourcePort || report.TransferPort == job.Request.DestPort : report.TransferPort == port;
            if (report.Ceid is not (601 or 602 or 603 or 604 or 605 or 606 or 607 or 610) || !ValidVehicle(job, report.VehicleId)
                || report.CarrierId != job.Request.CarrierId || !validPort) return job;
            matched = true;
            if (!job.IsPending) return job;
            // A drop-completed vehicle report is not a transport-completed report.
            var phase = report.Ceid == 604 || report.Ceid == 601 && report.TransferPort == job.Request.SourcePort ? job.Phase : JobPhase.Transferring;
            if (job.PendingControl.Length != 0 || job.Phase == JobPhase.Paused) phase = job.Phase;
            string place = report.Ceid == 601 ? (report.TransferPort == job.Request.SourcePort ? "출발지 도착" : "도착지 도착") : report.EventText;
            return job with { Phase = phase, LastCeid = report.Ceid, VehiclePort = report.TransferPort, VehicleId = report.VehicleId,
                Detail = $"{place} · CEID {report.Ceid} · AMR {report.VehicleId} · {report.TransferPort}" };
        });
        return matched;
    }
    public bool ReportCarrier(CarrierReport report)
    {
        bool matched = false;
        Update(report.CommandId, job =>
        {
            string location = report.Ceid == 301 ? report.VehicleId : job.Request.DestPort;
            if (report.Ceid is not (301 or 302) || !ValidVehicle(job, report.VehicleId)
                || report.CarrierId != job.Request.CarrierId || report.CarrierLocation != location) return job;
            matched = true;
            if (!job.IsPending) return job;
            // Carrier removal alone is not the final transport completion.
            return job with { Phase = job.PendingControl.Length != 0 || job.Phase == JobPhase.Paused ? job.Phase : JobPhase.Transferring, LastCeid = report.Ceid, VehicleId = report.VehicleId,
                Detail = $"{report.EventText} · CEID {report.Ceid} · AMR {report.VehicleId} · CarrierLoc {report.CarrierLocation}" };
        });
        return matched;
    }
    // Bind the first correlated vehicle report, including legacy profiles without 604.
    private static bool ValidVehicle(TransferJob job, string vehicle) =>
        System.Text.RegularExpressions.Regex.IsMatch(vehicle, "^[1-9][0-9]{0,9}$")
        && (job.VehicleId is null || job.VehicleId == vehicle);
    public void Uncertain(string id, string reason) => Update(id, j => j.IsPending ? j with { Phase = JobPhase.Uncertain, Detail = reason } : j);
    public void Disconnected() { foreach (TransferJob job in Snapshot().Where(j => j.IsPending)) Uncertain(job.Request.CommandId, "연결 단절: 결과 미확인. 자동 재전송하지 않습니다."); }
    public void Release(string id) => Update(id, j => j.IsPending ? j with { Phase = JobPhase.ManuallyReleased, Detail = "사용자가 Fleet 확인 후 로컬 대기만 해제함. Fleet 작업 취소/완료 처리 아님." } : j);
    private void Update(string id, Func<TransferJob, TransferJob> change)
    {
        TransferJob? updated;
        lock (_sync) { if (!_jobs.TryGetValue(id, out var old)) return; updated = change(old); if (updated == old) return; updated = SaveLocked(updated); }
        Changed?.Invoke(updated);
    }
    private void Save(TransferJob job) { lock (_sync) SaveLocked(job); }
    private TransferJob SaveLocked(TransferJob job)
    {
        job = job with { UpdatedAt = DateTimeOffset.Now };
        // Persist the intent before any network submission. Never recycle an uncertain command ID.
        byte[] bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(job) + "\n");
        using var file = new FileStream(_path, FileMode.Append, FileAccess.Write, FileShare.Read);
        file.Write(bytes); file.Flush(flushToDisk: true); Remember(job); return job;
    }
    private void Remember(TransferJob job)
    {
        _jobs[job.Request.CommandId] = job;
        if (!_history.TryGetValue(job.Request.CommandId, out var rows)) _history[job.Request.CommandId] = rows = [];
        rows.Add(job);
    }
}
