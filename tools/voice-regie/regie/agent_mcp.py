"""MCP server (stdio) for the régie agent in subscription mode (`claude -p`, regie/agent.py).

Claude Code starts it and lists its tools; each call is forwarded to the voice control service
(POST /agent/tool on 127.0.0.1), which hands it to the game, waits for the answer and returns it.
No dependency: the few JSON-RPC messages of MCP are written by hand (one JSON object per line).

    python agent_mcp.py <service url> <job id>

When Claude Code runs in WSL, this script runs with the Windows Python (WSL interop), so 127.0.0.1 is the
Windows machine where the service listens.
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vision  # noqa: E402  (the pictures of « voir »: same folder)

# Run as a script: no package import, the tool list comes from the service (GET /agent/tools)
PROTOCOL = "2025-06-18"


def _http(url: str, payload: dict | None, timeout: float) -> dict:
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, method="GET" if payload is None else "POST",
                                 headers={"Content-Type": "application/json; charset=utf-8"})
    with urllib.request.urlopen(req, timeout=timeout) as res:
        return json.loads(res.read().decode("utf-8"))


def main() -> None:
    base, job = sys.argv[1].rstrip("/"), sys.argv[2]
    stdin = sys.stdin.buffer
    out = sys.stdout.buffer

    def send(obj: dict) -> None:
        out.write((json.dumps(obj, ensure_ascii=False) + "\n").encode("utf-8"))
        out.flush()

    tools = None
    for raw in stdin:
        line = raw.decode("utf-8").strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except ValueError:
            continue
        method, mid = msg.get("method"), msg.get("id")
        if mid is None:          # notifications (initialized, cancelled): nothing to answer
            continue
        if method == "initialize":
            send({"jsonrpc": "2.0", "id": mid, "result": {
                "protocolVersion": (msg.get("params") or {}).get("protocolVersion", PROTOCOL),
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "craftmaster", "version": "1"}}})
        elif method == "tools/list":
            if tools is None:
                try:
                    tools = _http(base + "/agent/tools", None, 10)["tools"]
                except Exception as e:  # the service is gone: no tools rather than a crash
                    tools = []
                    print(f"agent_mcp: tool list unavailable: {e}", file=sys.stderr)
            send({"jsonrpc": "2.0", "id": mid, "result": {"tools": tools}})
        elif method == "tools/call":
            p = msg.get("params") or {}
            try:
                r = _http(base + "/agent/tool", {"job": job, "tool": p.get("name"), "args": p.get("arguments") or {}}, 330)
                text, err = r.get("output", ""), not r.get("ok", False)
            except (urllib.error.URLError, OSError, ValueError) as e:
                text, err = f"Service de régie injoignable : {e}", True
            content = None if err else vision.blocks(text, "mcp")
            send({"jsonrpc": "2.0", "id": mid, "result": {"content": content or [{"type": "text", "text": text}], "isError": err}})
        elif method == "ping":
            send({"jsonrpc": "2.0", "id": mid, "result": {}})
        else:
            send({"jsonrpc": "2.0", "id": mid, "error": {"code": -32601, "message": f"unknown method {method}"}})


if __name__ == "__main__":
    main()
