#!/usr/bin/env python3

"""Serve the Push SVG, debugger output, and bounded local input queue."""

from __future__ import annotations

import argparse
from functools import partial
from http import HTTPStatus
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import secrets
import threading
import time
from urllib.parse import urlsplit


MAX_STATE_BYTES = 64 * 1024
MAX_FRAME_STATUS_BYTES = 1024
MAX_INPUT_INFO_BYTES = 1024
MAX_INPUT_STATUS_BYTES = 4096
MAX_INPUT_BODY_BYTES = 2048
MAX_INPUT_QUEUE = 64
MAX_FONT_BYTES = 2 * 1024 * 1024
EVENT_POLL_SECONDS = 0.008
EVENT_HEARTBEAT_SECONDS = 10.0
INPUT_INFO_FILE = "surface-input-info.json"
INPUT_STATUS_FILE = "surface-input-status.json"
INPUT_REQUEST_DIRECTORY = "surface-input-requests"
FONT_ROUTES = {
    "/fonts/lato-regular.ttf": "Lato-Regular.ttf",
    "/fonts/lato-medium.ttf": "Lato-Medium.ttf",
    "/fonts/lato-semibold.ttf": "Lato-Semibold.ttf",
}
CONTROL_PATTERN = re.compile(r"push\.[a-z0-9.-]{1,74}\Z")
IDENTIFIER_PATTERN = re.compile(r"[A-Za-z0-9._-]{1,80}\Z")


class PushSurfaceHandler(SimpleHTTPRequestHandler):
    """Static app plus fixed debugger output and input endpoints."""

    input_lock = threading.Lock()
    input_sequence = 0
    event_slots = threading.BoundedSemaphore(4)

    def __init__(self, *args, authority: str, debug_dir: Path, font_dir: Path | None, origin: str, **kwargs):
        self.authority = authority
        self.debug_dir = debug_dir
        self.font_dir = font_dir
        self.origin = origin
        super().__init__(*args, **kwargs)

    def do_GET(self) -> None:  # noqa: N802 - stdlib handler API
        if not self._trusted_host():
            self.send_error(HTTPStatus.MISDIRECTED_REQUEST)
            return
        path = urlsplit(self.path).path
        if path == "/api/events":
            self._serve_events()
            return
        if path == "/api/state":
            self._serve_state()
            return
        if path == "/api/display":
            self._serve_display()
            return
        if path in FONT_ROUTES:
            self._serve_font(path)
            return
        super().do_GET()

    def do_POST(self) -> None:  # noqa: N802 - stdlib handler API
        if not self._trusted_host():
            self.send_error(HTTPStatus.MISDIRECTED_REQUEST)
            return
        if urlsplit(self.path).path == "/api/input":
            if self.headers.get("Origin") != self.origin:
                self.send_error(HTTPStatus.FORBIDDEN)
                return
            self._queue_input()
            return
        self.send_error(HTTPStatus.NOT_FOUND)

    def end_headers(self) -> None:
        if urlsplit(self.path).path.startswith("/api/"):
            self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, _format: str, *_args: object) -> None:
        """Keep the local state stream and frame fetches from flooding the terminal."""

    def _trusted_host(self) -> bool:
        return self.headers.get_all("Host", []) == [self.authority]

    def _serve_state(self) -> None:
        state = self._read_state()
        display = self._display_state()
        if display is not None:
            state["display"] = display
        state["input"] = self._input_state()
        self._send_json(HTTPStatus.OK, state)

    def _serve_events(self) -> None:
        if not type(self).event_slots.acquire(blocking=False):
            self.send_error(HTTPStatus.TOO_MANY_REQUESTS)
            return
        try:
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/event-stream; charset=utf-8")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.end_headers()

            previous = None
            last_write = time.monotonic()
            self.wfile.write(b"retry: 500\n\n")
            self.wfile.flush()
            while True:
                snapshot = self._event_snapshot()
                now = time.monotonic()
                if snapshot != previous:
                    payload = json.dumps({"revision": max(snapshot)}, separators=(",", ":"))
                    self.wfile.write(f"event: state\ndata: {payload}\n\n".encode("utf-8"))
                    self.wfile.flush()
                    previous = snapshot
                    last_write = now
                elif now - last_write >= EVENT_HEARTBEAT_SECONDS:
                    self.wfile.write(b": keepalive\n\n")
                    self.wfile.flush()
                    last_write = now
                time.sleep(EVENT_POLL_SECONDS)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
            return
        finally:
            type(self).event_slots.release()

    def _serve_font(self, route: str) -> None:
        if self.font_dir is None:
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        font_path = self.font_dir / FONT_ROUTES[route]
        if not self._is_bounded_file(font_path, MAX_FONT_BYTES):
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        try:
            payload = font_path.read_bytes()
        except OSError:
            self.send_error(HTTPStatus.SERVICE_UNAVAILABLE)
            return
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "font/ttf")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "public, max-age=3600")
        self.end_headers()
        self.wfile.write(payload)

    def _queue_input(self) -> None:
        content_type = self.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
        try:
            content_length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": "invalid content length"})
            return
        if content_type != "application/json" or content_length < 1 or content_length > MAX_INPUT_BODY_BYTES:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": "input requests require bounded JSON"})
            return
        try:
            request = json.loads(self.rfile.read(content_length).decode("utf-8"))
        except (UnicodeError, json.JSONDecodeError):
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": "invalid JSON"})
            return
        error = self._validate_input(request)
        if error is not None:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": error})
            return

        info = self._read_json(self.debug_dir / INPUT_INFO_FILE, MAX_INPUT_INFO_BYTES)
        if not info or not info.get("connected") or request["session"] != info.get("session"):
            self._send_json(HTTPStatus.CONFLICT, {"error": "Bitwig debug input is unavailable or stale"})
            return

        request_id = secrets.token_hex(8)
        try:
            with type(self).input_lock:
                request_dir = self.debug_dir / INPUT_REQUEST_DIRECTORY
                if request_dir.exists() and (request_dir.is_symlink() or not request_dir.is_dir()):
                    raise OSError("debug input queue is unavailable")
                request_dir.mkdir(parents=True, exist_ok=True)
                queued = [path for path in request_dir.iterdir()
                          if path.name.startswith("input-") and path.name.endswith(".txt") and path.is_file() and not path.is_symlink()]
                if len(queued) >= MAX_INPUT_QUEUE:
                    self._send_json(HTTPStatus.TOO_MANY_REQUESTS, {"error": "debug input queue is full"})
                    return
                type(self).input_sequence = (type(self).input_sequence + 1) % 1_000_000
                name = f"input-{time.time_ns():020d}-{type(self).input_sequence:06d}-{request_id}.txt"
                output = request_dir / name
                temporary = request_dir / f".{name}.tmp"
                content = "\t".join((
                    request["session"], request_id, request["control"], request["kind"],
                    request["phase"], str(request["value"]))) + "\n"
                temporary.write_text(content, encoding="utf-8")
                os.replace(temporary, output)
        except OSError:
            self._send_json(HTTPStatus.SERVICE_UNAVAILABLE, {"error": "could not queue debug input"})
            return
        self._send_json(HTTPStatus.ACCEPTED, {"requestId": request_id, "queued": True})

    def _validate_input(self, request: object) -> str | None:
        if not isinstance(request, dict):
            return "input request must be an object"
        required = {"session", "control", "kind", "phase", "value"}
        if set(request) != required:
            return "input request fields are invalid"
        if not isinstance(request["session"], str) or not IDENTIFIER_PATTERN.fullmatch(request["session"]):
            return "invalid debug input session"
        if not isinstance(request["control"], str) or not CONTROL_PATTERN.fullmatch(request["control"]):
            return "invalid control"
        if request["kind"] not in {"BUTTON", "PAD", "TOUCH", "POLY_PRESSURE", "RELATIVE"}:
            return "invalid input kind"
        if request["phase"] not in {"BEGIN", "CHANGE", "END", "KEEPALIVE"}:
            return "invalid input phase"
        if isinstance(request["value"], bool) or not isinstance(request["value"], int):
            return "input value must be an integer"
        if request["kind"] == "RELATIVE":
            if request["value"] == 0 or not -63 <= request["value"] <= 63:
                return "relative value must be from -63 through -1 or 1 through 63"
        elif not 0 <= request["value"] <= 127:
            return "input value must be an integer from 0 through 127"
        if request["phase"] == "KEEPALIVE":
            return None
        if request["kind"] == "RELATIVE":
            return None if request["phase"] == "CHANGE" else "relative input requires CHANGE"
        if request["kind"] == "POLY_PRESSURE":
            return None if request["phase"] == "CHANGE" else "pressure requires CHANGE"
        return None if request["phase"] in {"BEGIN", "END"} else "edge input requires BEGIN or END"

    def _input_state(self) -> dict:
        info = self._read_json(self.debug_dir / INPUT_INFO_FILE, MAX_INPUT_INFO_BYTES)
        if not info or not isinstance(info.get("connected"), bool) or not isinstance(info.get("session"), str):
            return {"connected": False, "session": ""}
        result = {"connected": info["connected"], "session": info["session"] if info["connected"] else ""}
        status = self._read_json(self.debug_dir / INPUT_STATUS_FILE, MAX_INPUT_STATUS_BYTES)
        if isinstance(status, dict):
            result["status"] = status
        return result

    def _send_json(self, status: HTTPStatus, value: dict) -> None:
        payload = json.dumps(value, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _serve_display(self) -> None:
        image = self.debug_dir / "latest.png"
        if not self._is_bounded_file(image, 16 * 1024 * 1024):
            self.send_error(HTTPStatus.NOT_FOUND, "No debugger display frame is available")
            return
        payload = image.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "image/png")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _read_state(self) -> dict:
        state = self._read_json(self.debug_dir / "surface-state.json", MAX_STATE_BYTES)
        if state is None:
            return self._empty_state()
        lights = state.get("lights")
        pressed = state.get("pressed")
        events = state.get("events")
        if not isinstance(lights, dict) or not isinstance(pressed, list) or not isinstance(events, list):
            return self._empty_state()
        return state

    def _read_json(self, path: Path, limit: int) -> dict | None:
        if not self._is_bounded_file(path, limit):
            return None
        try:
            value = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError):
            return None
        return value if isinstance(value, dict) else None

    def _display_state(self) -> dict | None:
        status_path = self.debug_dir / "latest-frame.txt"
        image_path = self.debug_dir / "latest.png"
        if not self._is_bounded_file(status_path, MAX_FRAME_STATUS_BYTES) or not self._is_bounded_file(image_path, 16 * 1024 * 1024):
            return None
        try:
            fields = status_path.read_text(encoding="utf-8").strip().split("\t")
            revision = int(fields[0])
        except (OSError, UnicodeError, ValueError, IndexError):
            return None
        return {"revision": revision, "url": "/api/display"}

    def _event_snapshot(self) -> tuple[int, ...]:
        return tuple(self._modified_nanos(self.debug_dir / name) for name in (
            "latest-frame.txt",
            "surface-state.json",
            INPUT_INFO_FILE,
            INPUT_STATUS_FILE,
        ))

    @staticmethod
    def _empty_state() -> dict:
        return {"connected": False, "revision": 0, "lights": {}, "pressed": [], "events": []}

    @staticmethod
    def _is_bounded_file(path: Path, limit: int) -> bool:
        try:
            return not path.is_symlink() and path.is_file() and path.stat().st_size <= limit
        except OSError:
            return False

    @staticmethod
    def _modified_nanos(path: Path) -> int:
        try:
            return 0 if path.is_symlink() or not path.is_file() else path.stat().st_mtime_ns
        except OSError:
            return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Serve the Pull Push 2 debugger surface")
    parser.add_argument("--app-dir", type=Path, required=True)
    parser.add_argument("--port", type=int, default=8765)
    return parser.parse_args()


def find_bitwig_font_dir() -> Path | None:
    configured = os.environ.get("PUSH_DEBUG_SURFACE_FONT_DIR")
    candidates = [Path(configured).expanduser()] if configured else []
    candidates.extend((
        Path("/Applications/Bitwig Studio.app/Contents/Resources/fonts"),
        Path("/opt/bitwig-studio/resources/fonts"),
        Path("/usr/share/bitwig-studio/resources/fonts"),
    ))
    program_files = os.environ.get("ProgramFiles")
    if program_files:
        candidates.append(Path(program_files) / "Bitwig Studio" / "resources" / "fonts")
    required = tuple(FONT_ROUTES.values())
    return next((candidate for candidate in candidates if all((candidate / name).is_file() for name in required)), None)


def main() -> None:
    args = parse_args()
    if args.port < 1 or args.port > 65535:
        raise SystemExit("port must be between 1 and 65535")
    app_dir = args.app_dir.resolve(strict=True)
    configured_debug_dir = os.environ.get("PUSH_DEBUG_SURFACE_DEBUG_DIR")
    debug_dir = Path(configured_debug_dir).expanduser() if configured_debug_dir else Path.home() / ".drivenbymoss" / "pull" / "debug"
    authority = f"127.0.0.1:{args.port}"
    origin = f"http://{authority}"
    handler = partial(PushSurfaceHandler, directory=str(app_dir), authority=authority, debug_dir=debug_dir, font_dir=find_bitwig_font_dir(), origin=origin)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), handler)
    print(f"Serving Push debugger surface on http://127.0.0.1:{args.port}/", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
