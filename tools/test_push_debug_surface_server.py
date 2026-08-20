#!/usr/bin/env python3

"""Trust-boundary tests for the local Push debugger HTTP server."""

from __future__ import annotations

from functools import partial
from http import HTTPStatus
from http.client import HTTPConnection
from http.server import ThreadingHTTPServer
import importlib.util
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
import threading
import unittest
from unittest.mock import patch


SERVER_PATH = Path(__file__).with_name("push-debug-surface-server.py")
SPEC = importlib.util.spec_from_file_location("push_debug_surface_server", SERVER_PATH)
assert SPEC is not None and SPEC.loader is not None
SERVER_MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SERVER_MODULE)


class PushSurfaceServerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = TemporaryDirectory()
        root = Path(self.temporary.name)
        self.app_dir = root / "app"
        self.debug_dir = root / "debug"
        self.app_dir.mkdir()
        self.debug_dir.mkdir()
        (self.app_dir / "index.html").write_text("<!doctype html><title>Push</title>", encoding="utf-8")
        (self.debug_dir / SERVER_MODULE.INPUT_INFO_FILE).write_text(
            json.dumps({"connected": True, "session": "test-session"}), encoding="utf-8")
        (self.debug_dir / "surface-state.json").write_text(
            json.dumps({"connected": True, "revision": 1, "lights": {}, "pressed": [], "events": []}),
            encoding="utf-8")
        self.lease_active = True

        placeholder = partial(
            SERVER_MODULE.PushSurfaceHandler,
            directory=str(self.app_dir),
            authority="",
            debug_dir=self.debug_dir,
            font_dir=None,
            lease_active=lambda: self.lease_active,
            origin="")
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), placeholder)
        self.authority = f"127.0.0.1:{self.server.server_port}"
        self.origin = f"http://{self.authority}"
        self.server.RequestHandlerClass = partial(
            SERVER_MODULE.PushSurfaceHandler,
            directory=str(self.app_dir),
            authority=self.authority,
            debug_dir=self.debug_dir,
            font_dir=None,
            lease_active=lambda: self.lease_active,
            origin=self.origin)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.temporary.cleanup()

    def request(self, method: str, path: str, headers: dict[str, str], body: bytes | None = None) -> tuple[int, bytes]:
        connection = HTTPConnection("127.0.0.1", self.server.server_port, timeout=2)
        connection.request(method, path, body=body, headers=headers)
        response = connection.getresponse()
        result = response.status, response.read()
        connection.close()
        return result

    def input_body(self, control: str = "push.button.play", phase: str = "BEGIN", value: int = 127) -> bytes:
        return json.dumps({
            "session": "test-session",
            "control": control,
            "kind": "BUTTON",
            "phase": phase,
            "value": value,
        }).encode("utf-8")

    def test_foreign_host_cannot_read_session(self) -> None:
        status, body = self.request("GET", "/api/state", {"Host": "attacker.example"})

        self.assertEqual(HTTPStatus.MISDIRECTED_REQUEST, status)
        self.assertNotIn(b"test-session", body)

    def test_exact_loopback_host_can_read_state(self) -> None:
        status, body = self.request("GET", "/api/state", {"Host": self.authority})

        self.assertEqual(HTTPStatus.OK, status)
        self.assertEqual("test-session", json.loads(body)["input"]["session"])

    def test_foreign_origin_cannot_enqueue_input(self) -> None:
        status, _body = self.request("POST", "/api/input", {
            "Host": self.authority,
            "Origin": "https://attacker.example",
            "Content-Type": "application/json",
        }, self.input_body())

        self.assertEqual(HTTPStatus.FORBIDDEN, status)
        self.assertFalse((self.debug_dir / SERVER_MODULE.INPUT_REQUEST_DIRECTORY).exists())

    def test_exact_loopback_origin_can_enqueue_input(self) -> None:
        status, _body = self.request("POST", "/api/input", {
            "Host": self.authority,
            "Origin": self.origin,
            "Content-Type": "application/json",
        }, self.input_body())

        self.assertEqual(HTTPStatus.ACCEPTED, status)
        queued = list((self.debug_dir / SERVER_MODULE.INPUT_REQUEST_DIRECTORY).glob("input-*.txt"))
        self.assertEqual(1, len(queued))

    def test_expired_live_lease_cannot_enqueue_input(self) -> None:
        self.lease_active = False
        status, _body = self.request("POST", "/api/input", {
            "Host": self.authority,
            "Origin": self.origin,
            "Content-Type": "application/json",
        }, self.input_body())

        self.assertEqual(HTTPStatus.SERVICE_UNAVAILABLE, status)
        self.assertFalse((self.debug_dir / SERVER_MODULE.INPUT_REQUEST_DIRECTORY).exists())

    def test_live_lease_requires_matching_live_supervisor(self) -> None:
        owner_file = Path(self.temporary.name) / "live.owner"
        owner_file.write_text(f"pid={os.getpid()}\ntoken=test-token\n", encoding="utf-8")
        with patch.dict(os.environ, {
                "PULL_LIVE_LOCK_OWNER_FILE": str(owner_file),
                "PULL_LIVE_LOCK_TOKEN": "test-token"}):
            self.assertTrue(SERVER_MODULE.live_lease_active())
            owner_file.unlink()
            self.assertFalse(SERVER_MODULE.live_lease_active())

    def test_port_can_queue_multiple_button_edges(self) -> None:
        headers = {
            "Host": self.authority,
            "Origin": self.origin,
            "Content-Type": "application/json",
        }
        requests = (
            self.input_body("push.button.shift"),
            self.input_body("push.button.play"),
            self.input_body("push.button.play", "END", 0),
            self.input_body("push.button.shift", "END", 0),
        )

        for body in requests:
            status, _response = self.request("POST", "/api/input", headers, body)
            self.assertEqual(HTTPStatus.ACCEPTED, status)

        queued = list((self.debug_dir / SERVER_MODULE.INPUT_REQUEST_DIRECTORY).glob("input-*.txt"))
        self.assertEqual(4, len(queued))


if __name__ == "__main__":
    unittest.main()
