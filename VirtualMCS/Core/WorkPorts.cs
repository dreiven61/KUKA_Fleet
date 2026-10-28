namespace KukaMcs.Core;

/// <summary>lay01/dist1 lift workstations from the 2026-10-28 map export.
/// Charging, charging entrance and transit nodes are deliberately excluded.</summary>
public static class WorkPorts
{
    public static IReadOnlyList<string> All { get; } = Array.AsReadOnly(new[]
    {
        "PORT_84", "PORT_86", "PORT_89", "PORT_90", "PORT_93",
        "PORT_94", "PORT_95", "PORT_96", "PORT_97", "PORT_98"
    });

    public static bool Contains(string port) => All.Contains(port, StringComparer.Ordinal);
}
