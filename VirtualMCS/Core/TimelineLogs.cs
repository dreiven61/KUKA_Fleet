using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;

namespace KukaMcs.Core;

public sealed record TimelineLogTarget(string CommandId, DateTimeOffset? Time, byte Stream, byte Function, string Direction, uint? Ceid)
{
    public static TimelineLogTarget? From(TransferJob step)
    {
        DateTimeOffset? time = step.UpdatedAt ?? (step.Phase == JobPhase.Sending ? step.CreatedAt : null);
        string detail = step.Detail;
        if (detail.StartsWith("S2F49 ", StringComparison.Ordinal)) return new(step.Request.CommandId, time, 2, 49, "TX", null);
        if (detail.StartsWith("S2F50 ", StringComparison.Ordinal) || detail.StartsWith("S2F50=", StringComparison.Ordinal)) return new(step.Request.CommandId, time, 2, 50, "RX", null);
        foreach (string control in new[] { "CANCEL", "ABORT", "PAUSE", "RESUME" })
        {
            if (detail.StartsWith(control + " 전송 시도", StringComparison.Ordinal)) return new(step.Request.CommandId, time, 2, 41, "TX", null);
            if (detail.StartsWith(control + " · HCACK=", StringComparison.Ordinal)) return new(step.Request.CommandId, time, 2, 42, "RX", null);
        }
        if ((detail.Contains(" · CEID ", StringComparison.Ordinal) || detail.StartsWith("CEID ", StringComparison.Ordinal)) && step.LastCeid is uint ceid)
            return new(step.Request.CommandId, time, 6, 11, "RX", ceid);
        return null; // Restart/disconnect/manual release/uncertain states are not wire messages.
    }
    public LogEntry? Find(IEnumerable<LogEntry> entries) => Time is not DateTimeOffset time ? null : entries
        .Where(e => e.CommandId == CommandId && e.Stream == Stream && e.Function == Function && e.Direction == Direction
            && (Ceid is null || e.Ceid == Ceid) && Math.Abs((e.Time.DateTime - time.DateTime).TotalSeconds) <= 2)
        .OrderBy(e => Math.Abs((e.Time.DateTime - time.DateTime).Ticks)).FirstOrDefault();
}

/// <summary>Read existing disk logs without changing or replaying any MCS job.</summary>
public static class MessageLogArchive
{
    private static readonly Regex Header = new(@"^\[(?<time>\d{2}:\d{2}:\d{2}\.\d{3})\] (?<dir>[A-Z][A-Z0-9_]*)\s+(?<summary>.*)$");
    private static readonly Regex Message = new(@"^S(?<s>\d{1,3})F(?<f>\d{1,3})(?:\s|$)", RegexOptions.Multiline);
    private static readonly Regex SystemBytes = new(@"^SB=0x(?<sb>[A-Fa-f0-9]{8})\s*$", RegexOptions.Multiline);
    private static readonly Regex EventId = new(@"^S6F11[^\r\n]*\r?\n<L \[3\][^\r\n]*\r?\n\s*<(?:U[124]|I[124]) \[1\] \d+(?: \[[^\]\r\n]+\])?>[^\r\n]*\r?\n\s*<(?:U[124]|I[124]) \[1\] (?<ceid>\d+)(?: \[[^\]\r\n]+\])?>", RegexOptions.Multiline);
    public static IReadOnlyList<LogEntry> Read(string directory, string commandId)
    {
        var found = new List<LogEntry>();
        var pending = new Dictionary<(int, byte, byte, string), DateTimeOffset>();
        if (!Directory.Exists(directory)) return found;
        var files = Directory.EnumerateFiles(directory, "messages_????????.log")
            .Concat(Directory.EnumerateFiles(directory, "secs_????????.log"))
            .OrderBy(file => Path.GetFileNameWithoutExtension(file)[^8..], StringComparer.Ordinal)
            .ThenBy(file => Path.GetFileName(file), StringComparer.Ordinal);
        foreach (string file in files)
        {
            if (!DateTime.TryParseExact(Path.GetFileNameWithoutExtension(file)[^8..], "yyyyMMdd", CultureInfo.InvariantCulture, DateTimeStyles.None, out DateTime date)) continue;
            using var stream = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            using var reader = new StreamReader(stream, Encoding.UTF8);
            Match? header = null; var body = new StringBuilder();
            void Complete()
            {
                if (header is null) return;
                if (header.Groups["dir"].Value is not ("TX" or "RX")) return;
                string text = body.ToString().TrimEnd('\r', '\n');
                if (!text.EndsWith("\n.", StringComparison.Ordinal)) return; // Do not expose a partially written message.
                var message = Message.Match(text); var sb = SystemBytes.Match(text);
                if (!message.Success || !sb.Success || !byte.TryParse(message.Groups["s"].Value, out byte s) || !byte.TryParse(message.Groups["f"].Value, out byte f)) return;
                var time = new DateTimeOffset(date + TimeSpan.ParseExact(header.Groups["time"].Value, @"hh\:mm\:ss\.fff", CultureInfo.InvariantCulture));
                int id = unchecked((int)uint.Parse(sb.Groups["sb"].Value, NumberStyles.HexNumber, CultureInfo.InvariantCulture));
                string direction = header.Groups["dir"].Value;
                bool direct = text.Contains("\"" + commandId + "\"", StringComparison.Ordinal);
                var key = (id, s, f, direction);
                bool correlated = pending.Remove(key, out var primaryTime) && time >= primaryTime && time - primaryTime <= TimeSpan.FromSeconds(600);
                if (f % 2 == 1)
                {
                    var replyKey = (id, s, (byte)(f + 1), direction == "TX" ? "RX" : "TX");
                    pending.Remove(replyKey);
                    if (direct) pending[replyKey] = time;
                }
                if (!direct && !correlated) return;
                uint? ceid = null;
                if (s == 6 && f == 11 && uint.TryParse(EventId.Match(text).Groups["ceid"].Value, out uint parsed)) ceid = parsed;
                found.Add(new(time, direction, text, s, f, id, commandId, ceid, header.Groups["summary"].Value, file));
            }
            while (reader.ReadLine() is string line)
            {
                var match = Header.Match(line);
                if (match.Success) { Complete(); header = match; body.Clear(); }
                else if (header is not null) body.Append(line).Append('\n');
            }
            Complete();
        }
        return found.DistinctBy(e => (e.Time, e.Direction, e.SystemBytes, e.Text)).OrderByDescending(e => e.Time).ToArray();
    }
}
