#!/usr/bin/env python3
"""Drives a dev client through MCP Fabric in ONE session (fast consecutive calls, e.g. animation frames).

  python3 scripts/mcp_session.py <client run dir> '<steps>' <png prefix> [rcon host] [rcon port]

<client run dir>: Windows path of the client's run dir (C:\\Users\\yanni\\code\\craftmaster\\run), whose
config/mcpfabric.config.json gives the bridge port and token.
<steps>: JSON list of [tool, arguments, seconds to wait after]. Tool "!rcon" runs a server command instead
(arguments = the command string), handy to trigger something between two screenshots.
Screenshots are saved as <png prefix>1.png, 2.png...  Requires Windows Node.js and the mcpfabric checkout
(see the minecraft-mod-dev skill).
"""
import base64, json, queue, socket, struct, subprocess, sys, threading, time

NODE = "/mnt/c/Program Files/nodejs/node.exe"
LAUNCHER = r"C:\Users\yanni\.claude\skills\minecraft-mod-dev\mcpfabric.mjs"


def rcon(host, port, password, command):
    s = socket.create_connection((host, port), timeout=10)

    def send(kind, body):
        data = struct.pack("<ii", 1, kind) + body.encode() + b"\0\0"
        s.sendall(struct.pack("<i", len(data)) + data)
        n = struct.unpack("<i", s.recv(4))[0]
        out = b""
        while len(out) < n:
            out += s.recv(n - len(out))
        return out[8:-2].decode("utf-8", "replace")

    send(3, password)
    return send(2, command)


def main():
    run_dir, steps, prefix = sys.argv[1], json.loads(sys.argv[2]), sys.argv[3]
    host = sys.argv[4] if len(sys.argv) > 4 else "127.0.0.1"
    port = int(sys.argv[5]) if len(sys.argv) > 5 else 25692
    p = subprocess.Popen([NODE, LAUNCHER, run_dir], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    lines = queue.Queue()
    threading.Thread(target=lambda: [lines.put(l) for l in p.stdout], daemon=True).start()

    def call(i, method, params=None):
        msg = {"jsonrpc": "2.0", "id": i, "method": method}
        if params is not None:
            msg["params"] = params
        p.stdin.write((json.dumps(msg) + "\n").encode())
        p.stdin.flush()
        while True:
            d = json.loads(lines.get(timeout=30))
            if d.get("id") == i:
                return d

    call(0, "initialize", {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "cli", "version": "1"}})
    p.stdin.write(b'{"jsonrpc":"2.0","method":"notifications/initialized"}\n')
    p.stdin.flush()
    shots = 0
    for i, (tool, args, delay) in enumerate(steps, 1):
        if tool == "!rcon":
            print("rcon ->", rcon(host, port, "cmtest", args).replace("\n", " ")[:300])
        else:
            d = call(i, "tools/call", {"name": tool, "arguments": args})
            for c in d.get("result", {}).get("content", []):
                if c.get("type") == "image":
                    shots += 1
                    f = f"{prefix}{shots}.png"
                    open(f, "wb").write(base64.b64decode(c["data"]))
                    print(tool, "->", f)
                else:
                    print(tool, "->", c.get("text", "")[:4000])
            if "error" in d:
                print(tool, "ERROR", d["error"])
        time.sleep(delay)
    p.terminate()


if __name__ == "__main__":
    main()
