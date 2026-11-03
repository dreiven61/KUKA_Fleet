namespace KukaMcs.Core;

/// <summary>Exact SECS identities received from SV58/SV25. No local aliases or site mapping.</summary>
public static class WorkPorts
{
    private sealed record Catalog(string[] Ports, string[] Vehicles, bool PortsReady, bool VehiclesReady);
    private static volatile Catalog _current = new([], [], false, false);
    private static readonly object Gate = new();
    public static IReadOnlyList<string> All => Array.AsReadOnly(_current.Ports);
    public static IReadOnlyList<string> Vehicles => Array.AsReadOnly(_current.Vehicles);
    public static IReadOnlyList<string> Sources => _current.Ports.Concat(_current.Vehicles).ToArray();
    public static bool Ready => _current.PortsReady && _current.VehiclesReady;
    public static void Reset() { lock (Gate) _current = new([], [], false, false); }
    public static bool Contains(string port) => _current.Ports.Contains(port, StringComparer.Ordinal);
    public static bool IsSource(string source) => Contains(source) || _current.Vehicles.Contains(source, StringComparer.Ordinal);
    public static string SourceVehicle(string source)
    {
        if (Contains(source)) return "";
        if (_current.Vehicles.Contains(source, StringComparer.Ordinal)) return source;
        // Decode old persisted requests only. IsSource never admits these aliases.
        return source.StartsWith("AMR_", StringComparison.Ordinal) ? source[4..] : "";
    }
    private static string[] Ids(IEnumerable<string> values, int max)
    {
        string[] ids = values.ToArray();
        if (ids.Length > 1000 || ids.Distinct(StringComparer.Ordinal).Count() != ids.Length
            || ids.Any(id => !System.Text.RegularExpressions.Regex.IsMatch(id, $"\\A[A-Za-z0-9_.:-]{{1,{max}}}\\z")))
            throw new FormatException("서버가 중복되거나 잘못된 PortID/VehicleID를 보고했습니다.");
        return ids;
    }
    public static void Install(IEnumerable<string> ports, IEnumerable<string> vehicles)
    {
        string[] p = Ids(ports, 64), v = Ids(vehicles, 32);
        if (p.Intersect(v, StringComparer.Ordinal).Any()) throw new FormatException("PortID와 VehicleID가 충돌합니다.");
        lock (Gate) _current = new(p, v, true, true);
    }
    public static void Apply(StatusQueryResult result)
    {
        lock (Gate) ApplyLocked(result);
    }
    private static void ApplyLocked(StatusQueryResult result)
    {
        if (!result.Success) { _current = _current with { PortsReady = false, VehiclesReady = false }; return; }
        var next = _current;
        foreach (var table in result.Tables)
        {
            if (table.Svid == 58) next = next with { Ports = Ids(table.Rows.Select(r => r[0]), 64), PortsReady = true };
            if (table.Svid == 25) next = next with { Vehicles = Ids(table.Rows.Select(r => r[0]), 32), VehiclesReady = true };
        }
        if (next.Ports.Intersect(next.Vehicles, StringComparer.Ordinal).Any()) throw new FormatException("PortID와 VehicleID가 충돌합니다.");
        _current = next;
    }
    public static void RequireReady() { if (!Ready) throw new InvalidOperationException("현재 연결의 AGV(SV25)·포트(SV58) 조회가 필요합니다."); }
}
