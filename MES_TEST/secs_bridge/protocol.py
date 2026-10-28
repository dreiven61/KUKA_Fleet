"""Strict SECS-II parameter decoding for the two lab-only remote commands."""
import re


def parameters(data):
    if not data or len(data) > 4096:
        raise ValueError("Missing or oversized SECS-II parameter data")
    position = 0
    count = 0

    def item(depth=0):
        nonlocal position, count
        count += 1
        if depth > 4 or count > 64 or position >= len(data):
            raise ValueError("Invalid SECS-II structure")
        head = data[position]
        position += 1
        length_bytes = head & 3
        if not length_bytes or position + length_bytes > len(data):
            raise ValueError("Invalid item length")
        length = int.from_bytes(data[position:position + length_bytes], "big")
        position += length_bytes
        if head & 252 == 0:  # LIST: length counts child items, not bytes.
            if length > 16:
                raise ValueError("Too many list items")
            return [item(depth + 1) for _ in range(length)]
        if head & 252 != 64 or length > 128 or position + length > len(data):
            raise ValueError("Only bounded ASCII parameters are supported")
        value = data[position:position + length].decode("ascii", errors="strict")
        position += length
        return value

    root = item()
    if position != len(data) or not isinstance(root, list):
        raise ValueError("Expected one parameter list, without trailing bytes")
    result = {}
    for pair in root:
        if (not isinstance(pair, list) or len(pair) != 2
                or not all(isinstance(value, str) for value in pair)):
            raise ValueError("Expected ASCII CPNAME/CPVAL pairs")
        name, value = pair
        if name in result:
            raise ValueError("Duplicate parameter")
        result[name] = value
    return result


def validate(command, data):
    values = parameters(data)
    required = {"COMMANDID", "WORKFLOW"} if command == "RUN_WORKFLOW" else {"COMMANDID"}
    if command not in ("RUN_WORKFLOW", "CHECK_COMPLETED"):
        raise ValueError("Unsupported remote command")
    if set(values) != required:
        raise ValueError("Unexpected or missing parameters")
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,40}", values["COMMANDID"]):
        raise ValueError("COMMANDID must be 1-40 ASCII letters, numbers, underscores or hyphens")
    if command == "RUN_WORKFLOW" and values["WORKFLOW"] != "W000000002":
        raise ValueError("Only SIM_RT_01 / W000000002 is enabled")
    return values
