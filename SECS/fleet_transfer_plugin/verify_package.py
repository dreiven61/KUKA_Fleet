"""Validate the final importable JAR, including the 1.5.0 runtime baseline."""
import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from zipfile import ZipFile

parser = argparse.ArgumentParser()
parser.add_argument("jar", type=Path)
parser.add_argument("--version", required=True)
parser.add_argument("--baseline", type=Path)
parser.add_argument("--record", type=Path)
args = parser.parse_args()
required = (
    "servostar/fleet/transfer/TransferPlugin.class",
    "servostar/fleet/transfer/NativeHostService.class",
    "servostar/fleet/transfer/HostProtocol.class",
    "servostar/fleet/transfer/VehicleNames.class",
    "servostar/fleet/transfer/FleetMapReader.class",
    "io/grpc/Channel.class",
    "io/grpc/internal/DnsNameResolverProvider.class",
    "META-INF/services/io.grpc.NameResolverProvider",
    "com/google/protobuf/Message.class",
    "com/kuka/wcs/grpc/mission/WcsMissionServiceGrpc.class",
    "com/kuka/ecs/common/transport/secs/hsmsss/HsmsSsCommunicator.class",
)
with ZipFile(args.jar) as target:
    assert target.testzip() is None, "Corrupt JAR entry"
    entries = target.namelist()
    names = set(entries)
    assert len(entries) == len(names), "Duplicate JAR entries"
    assert all(name in names for name in required), "Required plugin/runtime classes missing"
    manifest = target.read("META-INF/MANIFEST.MF").decode("utf-8")
    assert f"Plugin-Version: {args.version}\r\n" in manifest.replace("\r\n", "\n").replace("\n", "\r\n"), "Unexpected plugin version"
    baseline_runtime = 0
    if args.baseline:
        with ZipFile(args.baseline) as baseline:
            baseline_files = [name for name in baseline.namelist() if not name.endswith("/")]
            missing = set(baseline_files) - names
            assert not missing, f"Files present in baseline are missing: {sorted(missing)[:10]}"
            for name in baseline_files:
                if name == "META-INF/MANIFEST.MF" or name.startswith("servostar/fleet/transfer/"):
                    continue
                assert target.read(name) == baseline.read(name), f"Runtime differs from baseline: {name}"
                baseline_runtime += 1
    report = {
        "file": str(args.jar.resolve()), "version": args.version,
        "bytes": args.jar.stat().st_size, "entries": len(entries),
        "classes": sum(name.endswith(".class") for name in names),
        "grpcEntries": sum(name.startswith("io/grpc/") for name in names),
        "secsEntries": sum(name.startswith("com/kuka/ecs/common/transport/secs/") for name in names),
        "baseline": str(args.baseline.resolve()) if args.baseline else None,
        "identicalBaselineRuntimeFiles": baseline_runtime,
        "sha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
        "verifiedUtc": datetime.now(timezone.utc).isoformat(),
    }
    assert report["grpcEntries"] > 1000 and report["secsEntries"] > 100, "Thin JAR is not importable"
if args.record:
    args.record.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print(f"PASS: final JAR package: {report['bytes']} bytes, {report['entries']} entries, "
      f"{baseline_runtime} baseline runtime files identical; SHA256 {report['sha256']}")
