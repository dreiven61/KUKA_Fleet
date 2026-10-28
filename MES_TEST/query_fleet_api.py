"""Read-only checks of the documented Fleet standard API. Never submits a mission."""
import argparse
from datetime import datetime, timezone
import getpass
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request

READ_ONLY_APIS = {"robotQuery", "jobQuery", "containerQuery", "containerQueryAll"}


def query(base_url, token, api_name, body):
    if api_name not in READ_ONLY_APIS:
        raise ValueError("Only read-only APIs are allowed")
    request = urllib.request.Request(
        base_url.rstrip("/") + "/interfaces/api/amr/" + api_name,
        data=json.dumps(body).encode("utf-8"),
        headers={"Authorization": token, "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            status = response.status
            text = response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as error:
        status = error.code
        text = error.read().decode("utf-8", errors="replace")
    except urllib.error.URLError as error:
        return {"error": str(error.reason).replace(token, "[REDACTED]")}
    text = text.replace(token, "[REDACTED]")
    try:
        content = json.loads(text)
    except json.JSONDecodeError:
        content = text[:1500]
    return {"http_status": status, "response": content}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://192.168.0.45:5000")
    args = parser.parse_args()
    token = os.environ.get("FLEET_ACCESS_TOKEN") or getpass.getpass("Fleet access token: ")
    if not token.strip():
        raise SystemExit("An access token is required")
    result = {
        "queried_at": datetime.now(timezone.utc).isoformat(),
        "base_url": args.base_url,
        "robotQuery": query(args.base_url, token, "robotQuery", {}),
        "jobQuery": query(args.base_url, token, "jobQuery", {"robotId": "1", "limit": 10}),
    }
    output = Path(__file__).parent / "last_query_result.json"
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
