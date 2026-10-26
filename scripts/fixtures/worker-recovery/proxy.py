"""Disposable callback fault injector; never use as a production proxy."""
import hashlib
import http.client
import json
from http.server import BaseHTTPRequestHandler, HTTPServer


class Proxy(BaseHTTPRequestHandler):
    mode = "fail"
    stats = {"failed": 0, "forwarded": 0, "hashes": []}

    def log_message(self, *_):
        pass

    def reply(self, status, body=b""):
        self.send_response(status)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/__stats":
            self.reply(200, json.dumps({"mode": Proxy.mode, **Proxy.stats}).encode())
        else:
            self.forward(b"")

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        if self.path == "/__mode":
            mode = json.loads(body)["mode"]
            if mode not in ("fail", "pass", "drop_once"):
                self.reply(400)
                return
            Proxy.mode = mode
            self.reply(204)
            return
        if self.path.endswith("/result"):
            Proxy.stats["hashes"].append(hashlib.sha256(body).hexdigest())
            if Proxy.mode == "fail":
                Proxy.stats["failed"] += 1
                self.reply(503)
                return
        self.forward(body)

    def forward(self, body):
        upstream = http.client.HTTPConnection("api", 8080, timeout=15)
        try:
            headers = {key: value for key, value in self.headers.items()
                       if key.lower() not in ("host", "connection", "content-length")}
            upstream.request(self.command, self.path, body, headers)
            response = upstream.getresponse()
            data = response.read()
            if self.path.endswith("/result") and 200 <= response.status < 300:
                Proxy.stats["forwarded"] += 1
                if Proxy.mode == "drop_once":
                    Proxy.mode = "pass"
                    self.close_connection = True  # API committed, worker sees EOF.
                    return
            self.reply(response.status, data)
        except (OSError, http.client.HTTPException):
            self.reply(502)
        finally:
            upstream.close()


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 8080), Proxy).serve_forever()
