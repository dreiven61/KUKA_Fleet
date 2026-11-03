using System.Text.Json;
using KukaMcs.Core;
string profile = args[0];
var history = File.ReadLines(Path.Combine(profile,"jobs.jsonl")).Where(l => !string.IsNullOrWhiteSpace(l)).Select(l => JsonSerializer.Deserialize<TransferJob>(l)!).ToArray();
var commands = args.Skip(1).ToArray();
foreach (var id in commands)
{
    var messages = MessageLogArchive.Read(Path.Combine(profile,"logs"), id);
    var steps = history.Where(j=>j.Request.CommandId == id).ToArray();
    Console.WriteLine(JsonSerializer.Serialize(new { id, messageCount=messages.Count,
        steps=steps.Select(s=>new {time=s.UpdatedAt, s.Detail, target=TimelineLogTarget.From(s), match=TimelineLogTarget.From(s)?.Find(messages)?.MessageType}),
        messages=messages.Select(e=>new {e.Time,e.MessageType,e.SystemBytes,e.Ceid}) }));
}
