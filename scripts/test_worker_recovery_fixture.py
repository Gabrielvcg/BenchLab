"""Offline checks for the disposable integration harness and fault injector."""
import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


HARNESS = load("recovery_harness", Path(__file__).with_name("test-worker-recovery.py"))
PROXY = load("recovery_proxy", HARNESS.FIXTURE / "proxy.py")


class FixtureChecks(unittest.TestCase):
    def handler(self, mode, status=204):
        PROXY.Proxy.mode = mode
        PROXY.Proxy.stats = {"failed": 0, "forwarded": 0, "hashes": []}
        handler = object.__new__(PROXY.Proxy)
        handler.path = "/api/internal/runs/1/result"
        handler.command = "POST"
        body = b'{"status":"SUCCEEDED"}'
        handler.headers = {"Content-Length": str(len(body)), "X-Worker-Token": "fixture-token"}
        handler.rfile = io.BytesIO(body)
        handler.responses = []
        handler.reply = lambda *args: handler.responses.append(args)
        return handler

    def test_failure_never_reaches_real_api(self):
        handler = self.handler("fail")
        with patch.object(PROXY.http.client, "HTTPConnection") as upstream:
            handler.do_POST()
            upstream.assert_not_called()
        self.assertEqual(handler.responses, [(503,)])
        self.assertEqual(PROXY.Proxy.stats["failed"], 1)

    def test_response_loss_occurs_after_success_then_allows_retry(self):
        handler = self.handler("drop_once")
        with patch.object(PROXY.http.client, "HTTPConnection") as upstream:
            upstream.return_value.getresponse.return_value.status = 204
            upstream.return_value.getresponse.return_value.read.return_value = b""
            handler.do_POST()
            self.assertEqual(PROXY.Proxy.stats["forwarded"], 1)
            self.assertEqual(handler.responses, [])
            self.assertTrue(handler.close_connection)
            self.assertEqual(PROXY.Proxy.mode, "pass")
            handler.rfile.seek(0)
            handler.do_POST()
            self.assertEqual(handler.responses, [(204, b"")])
            self.assertEqual(PROXY.Proxy.stats["forwarded"], 2)
            self.assertEqual(len(set(PROXY.Proxy.stats["hashes"])), 1)

    def test_api_rejection_is_not_reported_as_success(self):
        handler = self.handler("pass")
        with patch.object(PROXY.http.client, "HTTPConnection") as upstream:
            upstream.return_value.getresponse.return_value.status = 400
            upstream.return_value.getresponse.return_value.read.return_value = b"rejected"
            handler.do_POST()
        self.assertEqual(PROXY.Proxy.stats["forwarded"], 0)
        self.assertEqual(handler.responses, [(400, b"rejected")])

    def test_manifest_guards_no_host_socket_or_fixed_host_ports(self):
        HARNESS.check()
        configuration = HARNESS.manifest("bl-recovery-" + "1" * 32)
        self.assertEqual(configuration["services"]["worker"]["mem_limit"], "256m")
        self.assertEqual(configuration["services"]["worker"]["environment"]["DOCKER_HOST"], "tcp://engine:2375")
        for service in configuration["services"].values():
            for port in service.get("ports", []):
                self.assertEqual(port["published"], "0")
                self.assertEqual(port["host_ip"], "127.0.0.1")
        self.assertTrue(configuration["services"]["engine"]["privileged"])
        self.assertNotIn("/var/run/docker.sock", json.dumps(configuration))
        self.assertNotIn("deploy/docker-compose", json.dumps(configuration))

    def test_remote_context_refused_before_docker_commands(self):
        with patch.dict(HARNESS.os.environ, {"DOCKER_HOST": "tcp://remote:2375"}):
            with patch.object(HARNESS.subprocess, "check_output") as command:
                with self.assertRaises(RuntimeError):
                    HARNESS.local_docker_environment()
                command.assert_not_called()

    def test_tcp_context_refused_before_any_mutation(self):
        with patch.dict(HARNESS.os.environ, {}, clear=True):
            with patch.object(HARNESS.subprocess, "check_output", return_value='[{"Endpoints":{"docker":{"Host":"tcp://remote:2375"}}}]') as command:
                with self.assertRaises(RuntimeError):
                    HARNESS.local_docker_environment()
                self.assertEqual(command.call_count, 1)
                self.assertEqual(command.call_args.args[0], ["docker", "context", "inspect"])


if __name__ == "__main__":
    unittest.main()
