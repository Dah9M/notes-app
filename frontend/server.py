"""Static file server with one JSON event per request, written to stdout."""
import json
import os
import re
import socket
import sys
import time
import traceback
import uuid
from contextvars import ContextVar
from datetime import datetime, timezone
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer


REQUEST_CONTEXT = ContextVar("request_context", default={})
HOSTNAME = os.environ.get("HOSTNAME") or socket.gethostname()
TRACEPARENT = re.compile(r"00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})")


def trace_context(header):
    match = TRACEPARENT.fullmatch(header or "")
    valid = match and int(match[1], 16) != 0 and int(match[2], 16) != 0
    trace_id = match[1] if valid else uuid.uuid4().hex
    flags = match[3] if valid else "00"
    return trace_id, f"00-{trace_id}-{uuid.uuid4().hex[:16]}-{flags}"


def emit(level, action, message, **fields):
    metadata = {
        "service.name": "notes-frontend",
        "service.version": os.environ.get("APP_VERSION", "dev"),
        "deployment.environment.name": os.environ.get("DEPLOYMENT_ENVIRONMENT", "local"),
        "host.name": HOSTNAME,
    }
    if os.environ.get("K8S_POD_NAME"):
        metadata["k8s.pod.name"] = os.environ["K8S_POD_NAME"]
    record = {
        "timestamp": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
        "level": level,
        "severity_number": {"TRACE": 1, "DEBUG": 5, "INFO": 9, "WARN": 13, "ERROR": 17}[level],
        "message": message,
    }
    for key, value in {**REQUEST_CONTEXT.get(), "event.action": action, **fields, **metadata}.items():
        parts = key.split(".")
        target = record
        for part in parts[:-1]:
            target = target.setdefault(part, {})
        target[parts[-1]] = value
    print(json.dumps(record, ensure_ascii=False), flush=True)


class Handler(SimpleHTTPRequestHandler):
    def handle_one_request(self):
        self.request_id = str(uuid.uuid4())
        self.started_at = time.monotonic_ns()
        self.response_status = None
        trace_id, self.traceparent = trace_context(None)
        token = REQUEST_CONTEXT.set({"request_id": self.request_id, "trace_id": trace_id})
        try:
            super().handle_one_request()
        except Exception as error:
            self.close_connection = True
            emit("ERROR", "http.request.failed", "Static server request failed", **{
                "error.type": type(error).__name__, "error.message": str(error),
                "error.stack": traceback.format_exc(),
                "duration_ms": (time.monotonic_ns() - self.started_at) / 1_000_000,
            })
        else:
            if self.response_status is not None:
                self.log_completion()
        finally:
            REQUEST_CONTEXT.reset(token)

    def parse_request(self):
        valid = super().parse_request()
        if valid:
            trace_id, parent = trace_context(self.headers.get("traceparent"))
            self.traceparent = parent
            REQUEST_CONTEXT.set({"request_id": self.request_id, "trace_id": trace_id})
        return valid

    def end_headers(self):
        self.send_header("X-Request-Id", self.request_id)
        if hasattr(self, "traceparent"):
            self.send_header("traceparent", self.traceparent)
        super().end_headers()

    def log_request(self, code="-", size="-"):
        self.response_status = int(code)

    def log_completion(self):
        status = self.response_status
        # No raw URL, query string, client address, headers or request body.
        emit("ERROR" if status >= 500 else "INFO", "http.request.completed",
             "HTTP request completed", **{
                 "request_id": self.request_id,
                 "http.request.method": getattr(self, "command", "unknown"),
                 "http.response.status_code": status,
                 "duration_ms": (time.monotonic_ns() - self.started_at) / 1_000_000,
                 "event.outcome": "failure" if status >= 400 else "success",
             })

    def log_message(self, format, *args):
        # Default messages can contain user-controlled paths and headers.
        pass


class Server(ThreadingHTTPServer):
    def handle_error(self, request, client_address):
        error = sys.exception()
        emit("ERROR", "http.request.failed", "Static server request failed", **{
            "error.type": type(error).__name__, "error.message": str(error),
            "error.stack": traceback.format_exc(),
        })


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "80"))
    server = Server(("0.0.0.0", port), Handler)
    emit("INFO", "server.started", "Static file server started", **{"server.port": port})
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        emit("INFO", "server.stopped", "Static file server stopped")
