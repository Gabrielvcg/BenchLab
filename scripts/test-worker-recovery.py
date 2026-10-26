"""Finite integration scenario for an explicitly disposable local Linux Docker host.

Uses only Python's standard library. No production Compose file or host socket mount.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = ROOT / "scripts/fixtures/worker-recovery"
PROJECT_PATTERN = re.compile(r"bl-recovery-[0-9a-f]{32}\Z")


def require_project(project):
    if not PROJECT_PATTERN.fullmatch(project):
        raise ValueError("Refusing arbitrary project name")


def manifest(project):
    require_project(project)
    # All credentials are deliberately fictitious and scoped to the fresh stack.
    common = {"SPRING_PROFILES_ACTIVE": "prod", "SPRING_LIQUIBASE_CONTEXTS": "prod", "SPRING_DOCKER_COMPOSE_ENABLED": "false",
              "SPRING_DATASOURCE_URL": "jdbc:postgresql://db:5432/fixture",
              "SPRING_DATASOURCE_USERNAME": "fixture", "SPRING_DATASOURCE_PASSWORD": "fixture-only",
              "SPRING_RABBITMQ_HOST": "rabbit", "SPRING_RABBITMQ_USERNAME": "fixture",
              "SPRING_RABBITMQ_PASSWORD": "fixture-only", "JHIPSTER_CACHE_REDIS_SERVER": "redis://redis:6379",
              "BENCHLAB_WORKER_TOKEN": "fixture-token", "BENCHLAB_SECURITY_ADMIN_PASSWORD": "fixture-only-admin",
              "JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET": base64.b64encode(b"fixture-only-" * 8).decode()}
    port = lambda: [{"target": 8080, "published": "0", "host_ip": "127.0.0.1"}]
    services = {
        "db": {"image": "postgres:17.4-alpine", "environment": {
            "POSTGRES_DB": "fixture", "POSTGRES_USER": "fixture", "POSTGRES_PASSWORD": "fixture-only"},
            "healthcheck": {"test": ["CMD-SHELL", "pg_isready -U fixture -d fixture"], "interval": "2s", "retries": 60}},
        "redis": {"image": "redis:8.0.0-alpine"},
        "rabbit": {"image": "rabbitmq:3.13-management", "environment": {
            "RABBITMQ_DEFAULT_USER": "fixture", "RABBITMQ_DEFAULT_PASS": "fixture-only",
            "RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS": "-rabbit collect_statistics_interval 1000"},
            "ports": [{"target": 15672, "published": "0", "host_ip": "127.0.0.1"}],
            "healthcheck": {"test": ["CMD", "rabbitmq-diagnostics", "-q", "ping"], "interval": "2s", "retries": 60}},
        "api": {"image": project + "-api", "build": {"context": str(ROOT),
            "dockerfile": str(FIXTURE / "Dockerfile.api")}, "environment": common,
            "depends_on": {"db": {"condition": "service_healthy"}, "rabbit": {"condition": "service_healthy"},
                           "redis": {"condition": "service_started"}}},
        "proxy": {"image": "python:3.12-alpine", "command": ["python", "/fixture/proxy.py"],
            "volumes": [{"type": "bind", "source": str(FIXTURE / "proxy.py"), "target": "/fixture/proxy.py", "read_only": True}],
            "ports": port()},
        # Privileged DinD is a test prerequisite on a disposable host, NOT a security boundary.
        "engine": {"image": "docker:28-dind", "privileged": True,
            "environment": {"DOCKER_TLS_CERTDIR": ""}, "command": ["--storage-driver=vfs"],
            "volumes": ["workspaces:/fixture-workspaces"],
            "tmpfs": ["/var/lib/docker:size=2147483648"],
            "healthcheck": {"test": ["CMD", "docker", "info"], "interval": "2s", "retries": 60}},
        "worker": {"image": project + "-worker", "build": {"context": str(ROOT / "runner-worker")},
            "environment": {"RABBITMQ_URL": "amqp://fixture:fixture-only@rabbit:5672/",
                "BENCHLAB_API_BASE_URL": "http://proxy:8080", "BENCHLAB_WORKER_TOKEN": "fixture-token",
                "DOCKER_HOST": "tcp://engine:2375", "DOCKER_API_VERSION": "1.47",
                "TMPDIR": "/fixture-workspaces", "WORKER_RESULT_PATH": "/results"},
            "volumes": ["results:/results", "workspaces:/fixture-workspaces"], "mem_limit": "256m",
            "depends_on": {"engine": {"condition": "service_healthy"}, "rabbit": {"condition": "service_healthy"}}},
    }
    budgets = {"db": ("384m", "1.0"), "redis": ("128m", "0.5"), "rabbit": ("768m", "1.0"),
               "api": ("1g", "2.0"), "proxy": ("128m", "0.5"), "engine": ("2g", "2.0"), "worker": ("256m", "1.0")}
    for name, (memory, cpus) in budgets.items():
        services[name].update(mem_limit=memory, cpus=cpus, pids_limit=256)
    return {"name": project, "services": services,
            "volumes": {"results": {}, "workspaces": {}}}


def wait_for(label, operation, timeout=120):
    deadline = time.monotonic() + timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            result = operation()
            if result:
                return result
        except (OSError, ValueError, subprocess.SubprocessError, urllib.error.URLError) as error:
            last_error = error
        time.sleep(0.5)
    raise RuntimeError(f"Timed out: {label}; last error: {last_error}")


def request(url, body=None, rabbit=False):
    headers = {"Content-Type": "application/json"}
    if rabbit:
        headers["Authorization"] = "Basic " + base64.b64encode(b"fixture:fixture-only").decode()
    data = None if body is None else json.dumps(body).encode()
    with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers), timeout=20) as response:
        result = response.read()
        return json.loads(result) if result else None


def local_docker_environment():
    # An inherited remote daemon/context is never a valid target for this script.
    environment = os.environ.copy()
    if environment.get("DOCKER_HOST") or environment.get("DOCKER_CONTEXT"):
        raise RuntimeError("Unset DOCKER_HOST/DOCKER_CONTEXT; this fixture requires a disposable local daemon")
    context = subprocess.check_output(["docker", "context", "inspect"], text=True, timeout=15)
    endpoint = json.loads(context)[0]["Endpoints"]["docker"]["Host"]
    if not endpoint.startswith(("unix://", "npipe://")):
        raise RuntimeError("Refusing a TCP/remote Docker endpoint")
    info = json.loads(subprocess.check_output(["docker", "info", "--format", "{{json .}}"], text=True, timeout=20))
    if info.get("OSType") != "linux":
        raise RuntimeError("Linux containers are required")
    return environment


class Scenario:
    def __init__(self, directory, environment):
        self.project = "bl-recovery-" + uuid.uuid4().hex
        require_project(self.project)
        self.file = Path(directory) / "compose.json"
        self.file.write_text(json.dumps(manifest(self.project), indent=2), encoding="utf-8")
        self.environment = environment
        self.created = False

    def compose(self, *arguments, input_text=None, timeout=120):
        require_project(self.project)
        command = ["docker", "compose", "--project-name", self.project, "--file", str(self.file), *arguments]
        return subprocess.check_output(command, text=True, input=input_text, env=self.environment, timeout=timeout)

    def endpoint(self, service, port):
        address = self.compose("port", service, str(port)).strip()
        if not re.fullmatch(r"127\.0\.0\.1:[0-9]+", address):
            raise RuntimeError("Unexpected non-loopback fixture port: " + address)
        return "http://" + address

    def sql(self, query):
        return self.compose("exec", "-T", "db", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-At", "-U", "fixture", "-d", "fixture",
                            input_text=query).strip()

    def journal(self, job):
        return json.loads(self.compose("exec", "-T", "worker", "cat", f"/results/{job}.json"))

    def queue(self):
        return request(self.rabbit + "/api/queues/%2F/benchlab.run.requested.q", rabbit=True)

    def publish(self, job):
        event = {"jobId": job, "implementationId": job, "datasetId": 1, "datasetSize": 1, "datasetSeed": 1,
                 "language": "PYTHON", "sourceCode": 'print("fixture-result")' if job == 1 else 'import sys; print("fixture-result"); sys.stderr.write("e" * 12000)', "compileConfig": "", "runtimeConfig": "",
                 "datasetVersion": "1", "timeoutMs": 15000, "memoryMb": 128, "cpuLimit": 1.0,
                 "iterations": 1, "warmupIterations": 0, "traceId": f"fixture-run-{job}"}
        result = request(self.rabbit + "/api/exchanges/%2F/benchlab.run.exchange/publish", {
            "properties": {"delivery_mode": 2, "content_type": "application/json"},
            "routing_key": "benchlab.run.requested", "payload": json.dumps(event), "payload_encoding": "string"}, rabbit=True)
        if result != {"routed": True}:
            raise RuntimeError("Message was not routed")

    def creations(self):
        # The dedicated nested daemon has only this fixture's runner containers.
        output = self.compose("exec", "-T", "engine", "docker", "events", "--since", str(self.started),
                              "--until", str(int(time.time()) + 1), "--filter", "type=container", "--filter", "event=create", "--format", "{{json .}}")
        return [event for event in map(json.loads, output.splitlines())
                if event.get("Actor", {}).get("Attributes", {}).get("name", "").startswith("benchlab-")]

    def run(self):
        self.created = True  # Also clean uniquely tagged images after a partial build.
        self.compose("build", "api", "worker", timeout=1800)
        self.compose("up", "-d", "db", "redis", "rabbit", "api", "proxy", "engine", timeout=240)
        self.proxy = self.endpoint("proxy", 8080)
        self.rabbit = self.endpoint("rabbit", 15672)
        wait_for("real API readiness", lambda: request(self.proxy + "/management/health").get("status") == "UP", 240)
        wait_for("RabbitMQ queue declaration", self.queue)
        self.sql((FIXTURE / "seed.sql").read_text())
        self.compose("exec", "-T", "engine", "docker", "pull", "python:3.12-alpine", timeout=240)
        self.started = int(time.time())
        self.compose("up", "-d", "worker")
        self.publish(1)
        stats = lambda: request(self.proxy + "/__stats")
        wait_for("failed callback after actual calculation", lambda: stats()["failed"] >= 1)
        before = self.journal(1)
        assert before.get("deliveredAt") is None and before["result"]["status"] == "SUCCEEDED", before
        assert before["result"]["stdoutPreview"].strip() == "fixture-result", before
        assert len(self.creations()) == 1, "Expected exactly one actual benchmark container"
        old_id = self.compose("ps", "-q", "worker").strip()
        self.compose("kill", "-s", "SIGKILL", "worker")
        wait_for("unacknowledged message returned to queue", lambda: self.queue().get("messages_ready", 0) == 1)
        returned = request(self.rabbit + "/api/queues/%2F/benchlab.run.requested.q/get", {
            "count": 1, "ackmode": "ack_requeue_true", "encoding": "auto", "truncate": 100000}, rabbit=True)
        assert len(returned) == 1 and returned[0]["redelivered"] is True, returned
        failed_before_restart = stats()["failed"]
        self.compose("rm", "-f", "worker")
        self.compose("up", "-d", "--no-deps", "worker")
        assert self.compose("ps", "-q", "worker").strip() != old_id, "Worker container was not replaced"
        wait_for("pending journal callback replay after replacement", lambda: stats()["failed"] > failed_before_restart)
        assert self.journal(1) == before, "Result volume changed/lost across container replacement"
        assert len(self.creations()) == 1, "Calculation repeated during recovery"
        request(self.proxy + "/__mode", {"mode": "pass"})
        wait_for("delivery marker", lambda: self.journal(1).get("deliveredAt"), 90)
        wait_for("redelivery acknowledged", lambda: self.queue().get("messages", -1) == 0)
        assert self.sql("SELECT status FROM benchmark_run WHERE id=1;") == "SUCCEEDED"
        assert self.sql("SELECT count(*) FROM run_metric WHERE benchmark_run_id=1;") == "1"
        assert self.sql("SELECT count(*) FROM run_artifact WHERE benchmark_run_id=1;") == "1"
        first_stats = stats()
        assert len(set(first_stats["hashes"])) == 1, "Persisted callback payload changed"
        acknowledgements = self.queue().get("message_stats", {}).get("ack", 0)
        self.publish(1)
        wait_for("delivered duplicate acknowledged", lambda: self.queue().get("message_stats", {}).get("ack", 0) > acknowledgements)
        assert stats()["forwarded"] == first_stats["forwarded"], "Delivered duplicate resent callback"
        assert len(self.creations()) == 1, "Delivered duplicate repeated calculation"
        request(self.proxy + "/__mode", {"mode": "drop_once"})
        forwarded = stats()["forwarded"]
        self.publish(2)
        wait_for("lost response retried against real API", lambda: stats()["forwarded"] >= forwarded + 2)
        wait_for("second delivery marker", lambda: self.journal(2).get("deliveredAt"))
        assert len(self.creations()) == 2, "Lost response repeated calculation"
        for table in ("run_metric", "run_artifact"):
            assert self.sql(f"SELECT count(*) FROM {table} WHERE benchmark_run_id=2;") == "1"
        assert self.sql("SELECT status FROM benchmark_run WHERE id=2;") == "SUCCEEDED"
        second = self.journal(2)["result"]
        assert second["stderrTruncated"] is True and len(second["stderrPreview"]) == 8000
        assert second["outputSizeBytes"] == 12015, second
        # Prove the freshly produced PostgreSQL backup is restorable, using only this fixture DB/container.
        self.compose("exec", "-T", "db", "sh", "-ec",
                     "pg_dump -U fixture -d fixture -Fc -f /tmp/benchlab-fixture.dump; "
                     "createdb -U fixture fixture_restored; "
                     "pg_restore -U fixture -d fixture_restored --exit-on-error /tmp/benchlab-fixture.dump", timeout=120)
        restored = self.compose("exec", "-T", "db", "psql", "-X", "-At", "-U", "fixture", "-d", "fixture_restored",
                                input_text="SELECT count(*) FROM benchmark_run WHERE status='SUCCEEDED';").strip()
        assert restored == "2", restored
        print(json.dumps({"result": "PASS", "project": self.project, "actual_benchmark_containers": 2,
                          "callback_stats": stats(), "worker_container_replaced": True, "rabbit_redelivered": True, "long_stderr_frame": True, "backup_restored": True}, indent=2))

    def cleanup(self):
        require_project(self.project)
        # Fixed manifest + generated project only. No prune, arbitrary resource names or host deletion.
        self.compose("down", "--volumes", "--remove-orphans", "--rmi", "local", timeout=180)
        # Compose's --rmi local excludes custom tags. Delete only our generated tags.
        for service in ("api", "worker"):
            tag = self.project + "-" + service
            exists = subprocess.run(["docker", "image", "inspect", tag], env=self.environment,
                                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=20)
            if exists.returncode == 0:
                subprocess.run(["docker", "image", "rm", tag], env=self.environment, check=True, timeout=60)


def check():
    project = "bl-recovery-" + "0" * 32
    configuration = manifest(project)
    assert "worker" in configuration["services"]
    for service in configuration["services"].values():
        assert all("docker.sock" not in str(volume) for volume in service.get("volumes", []))
        assert not service.get("container_name")
    for path in ("Dockerfile.api", "Dockerfile.api.dockerignore", "proxy.py", "seed.sql"):
        assert (FIXTURE / path).is_file(), path
    compile((FIXTURE / "proxy.py").read_text(), "proxy.py", "exec")
    for invalid in ("benchlab", "bl-recovery-prod", "../other", "", project + "x"):
        try:
            require_project(invalid)
        except ValueError:
            continue
        raise AssertionError("Unsafe project accepted: " + invalid)
    print("PASS: static fixture structure and project-name guards (Docker not invoked)")


def main():
    if not __debug__:
        raise RuntimeError("Run without -O: scenario assertions must remain enabled")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Local structure/guard checks; no Docker calls")
    parser.add_argument("--run-on-disposable-host", action="store_true", help="Explicitly acknowledge privileged DinD on a disposable host")
    arguments = parser.parse_args()
    if arguments.check:
        check()
        return
    if not arguments.run_on_disposable_host:
        parser.error("Choose --check or --run-on-disposable-host; read the fixture runbook first")
    environment = local_docker_environment()  # Read-only preflight, before creating anything.
    directory = Path(tempfile.mkdtemp(prefix="benchlab-recovery-"))
    scenario = Scenario(directory, environment)
    print("Fixture manifest:", scenario.file, flush=True)
    try:
        scenario.run()
    finally:
        # Keep the exact manifest if cleanup fails, so its own resources remain identifiable.
        if scenario.created:
            scenario.cleanup()
        scenario.file.unlink()
        directory.rmdir()  # No recursive deletion or user-supplied directory.


if __name__ == "__main__":
    main()
