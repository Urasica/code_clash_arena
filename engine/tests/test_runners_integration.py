import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENGINE_DIR = ROOT / "engine"
RUNNER_DIR = ROOT / "backend" / "code" / "src" / "main" / "resources" / "templates" / "runners"
IMAGE = "code-battle-engine"

USER_CODE = {
    "python": "def strategy(my_pos, coins, walls, board_size):\n    return 'STAY'",
    "java": "public static String strategy(int[] p, List<int[]> c, List<int[]> w, int s) { return \"STAY\"; }",
    "cpp": "string strategy(Point p, vector<Point> c, vector<Point> w, int s) { return \"STAY\"; }",
    "c": "const char* strategy(Point p, Point* c, int cn, Point* w, int wn, int s) { return \"STAY\"; }",
    "javascript": "function strategy(myPos, coins, walls, boardSize) { return 'STAY'; }",
}

RUNNER_FILES = {
    "python": ("python_runner.py", "p1.py"),
    "java": ("java_runner.txt", "Main.java"),
    "cpp": ("cpp_runner.cpp", "p1.cpp"),
    "c": ("c_runner.c", "p1.c"),
    "javascript": ("js_runner.js", "p1.js"),
}

SECURITY_CORPUS = {
    "filesystem_boundaries": """
import os

_checked = False

def strategy(my_pos, coins, walls, board_size):
    global _checked
    if not _checked:
        for protected_path in ('/run/players/p2/p2.py', '/app/referee.py'):
            try:
                with open(protected_path, 'r', encoding='utf-8') as protected:
                    protected.read(1)
                raise RuntimeError('isolation breach: ' + protected_path)
            except PermissionError:
                pass
        try:
            with open('/app/security-probe', 'w', encoding='utf-8') as probe:
                probe.write('breach')
            raise RuntimeError('isolation breach: read-only root')
        except OSError:
            pass
        _checked = True
    return 'STAY'
""",
    "capability_and_signal_boundaries": """
import os

_checked = False

def strategy(my_pos, coins, walls, board_size):
    global _checked
    if not _checked:
        with open('/proc/self/status', 'r', encoding='utf-8') as status_file:
            status = status_file.read()
        effective_caps = next(
            line.split()[1] for line in status.splitlines() if line.startswith('CapEff:')
        )
        if int(effective_caps, 16) != 0:
            raise RuntimeError('isolation breach: effective capabilities')
        try:
            os.kill(1, 0)
            raise RuntimeError('isolation breach: referee signal')
        except PermissionError:
            pass
        _checked = True
    return 'STAY'
""",
    "network_disabled": """
import socket

_checked = False

def strategy(my_pos, coins, walls, board_size):
    global _checked
    if not _checked:
        connection = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        connection.settimeout(0.1)
        try:
            connection.connect(('1.1.1.1', 53))
            raise RuntimeError('isolation breach: outbound network')
        except OSError:
            pass
        finally:
            connection.close()
        _checked = True
    return 'STAY'
""",
    "environment_scrubbed": """
import os

def strategy(my_pos, coins, walls, board_size):
    if 'CCA_SECURITY_SENTINEL' in os.environ:
        raise RuntimeError('isolation breach: inherited environment')
    if set(os.environ) - {'PATH', 'HOME', 'LANG'}:
        raise RuntimeError('isolation breach: unexpected environment')
    return 'STAY'
""",
}


def docker_is_ready():
    if not shutil.which("docker"):
        return False
    result = subprocess.run(
        ["docker", "inspect", "--type", "image", IMAGE],
        capture_output=True,
        text=True,
        timeout=10,
    )
    return result.returncode == 0


DOCKER_READY = docker_is_ready()
if os.environ.get("CCA_REQUIRE_DOCKER_TESTS") == "true" and not DOCKER_READY:
    raise RuntimeError(f"Required Docker image {IMAGE} is not available")


@unittest.skipUnless(DOCKER_READY, f"Docker image {IMAGE} is not available")
class RunnerIntegrationTest(unittest.TestCase):

    def docker_command(self, temp_dir, mode, include_data=False, environment=None):
        command = [
            "docker", "run", "--rm",
            "--network", "none",
            "--memory", "512m",
            "--pids-limit", "128",
            "--read-only",
            "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
            "--tmpfs", "/run/players:rw,exec,nosuid,nodev,size=128m",
            "--cap-drop", "ALL",
            "--cap-add", "CHOWN",
            "--cap-add", "DAC_READ_SEARCH",
            "--cap-add", "KILL",
            "--cap-add", "SETUID",
            "--cap-add", "SETGID",
            "--security-opt", "no-new-privileges",
        ]
        for key, value in (environment or {}).items():
            command.extend(["--env", f"{key}={value}"])
        if include_data:
            command.extend(["-v", f"{Path(temp_dir).as_posix()}:/app/data"])
        command.extend([
            "-v", f"{Path(temp_dir).as_posix()}:/app/players",
            IMAGE,
            "python3", "referee.py", "land_grab", mode,
        ])
        return command

    def write_player(self, temp_dir, language):
        template_name, source_name = RUNNER_FILES[language]
        player_dir = Path(temp_dir) / "p1"
        player_dir.mkdir()
        template = (RUNNER_DIR / template_name).read_text(encoding="utf-8")
        (player_dir / source_name).write_text(
            template.replace("%USER_CODE%", USER_CODE[language]),
            encoding="utf-8",
        )

    def test_init_emits_map_without_writing_to_the_bind_mount(self):
        with tempfile.TemporaryDirectory(dir=ENGINE_DIR) as temp_dir:
            result = subprocess.run(
                self.docker_command(temp_dir, "init"),
                capture_output=True,
                text=True,
                timeout=25,
            )
            payload = json.loads(result.stdout)

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(45, len(payload["walls"]))
            self.assertEqual(5, len(payload["coins"]))
            self.assertFalse((Path(temp_dir) / "map.json").exists())

    def test_all_advertised_languages_compile(self):
        for language in RUNNER_FILES:
            with self.subTest(language=language), tempfile.TemporaryDirectory(dir=ENGINE_DIR) as temp_dir:
                self.write_player(temp_dir, language)
                command = self.docker_command(temp_dir, "compile")
                result = subprocess.run(command, capture_output=True, text=True, timeout=25)

                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual("success", json.loads(result.stdout)["status"])

    def test_all_advertised_languages_complete_a_match(self):
        opponent = "import sys\nfor _ in sys.stdin:\n print('STAY', flush=True)\n"

        for language in RUNNER_FILES:
            with self.subTest(language=language), tempfile.TemporaryDirectory(dir=ENGINE_DIR) as temp_dir:
                self.write_player(temp_dir, language)
                p2_dir = Path(temp_dir) / "p2"
                p2_dir.mkdir()
                (p2_dir / "p2.py").write_text(opponent, encoding="utf-8")
                (Path(temp_dir) / "map.json").write_text(
                    json.dumps({"walls": [], "coins": []}),
                    encoding="utf-8",
                )

                command = self.docker_command(temp_dir, "run", include_data=True)
                result = subprocess.run(command, capture_output=True, text=True, timeout=40)
                payload = json.loads(result.stdout)

                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn("total_turns", payload, (payload, result.stderr))
                self.assertEqual(50, payload["total_turns"])
                self.assertIsNone(payload["p1_error"])

    def test_security_attack_corpus_is_blocked(self):
        opponent = "import sys\nfor _ in sys.stdin:\n print('STAY', flush=True)\n"

        for attack_name, attack in SECURITY_CORPUS.items():
            with self.subTest(attack=attack_name), tempfile.TemporaryDirectory(
                dir=ENGINE_DIR
            ) as temp_dir:
                p1_dir = Path(temp_dir) / "p1"
                p1_dir.mkdir()
                template = (RUNNER_DIR / "python_runner.py").read_text(encoding="utf-8")
                (p1_dir / "p1.py").write_text(
                    template.replace("%USER_CODE%", attack), encoding="utf-8"
                )
                p2_dir = Path(temp_dir) / "p2"
                p2_dir.mkdir()
                (p2_dir / "p2.py").write_text(opponent, encoding="utf-8")
                (Path(temp_dir) / "map.json").write_text(
                    json.dumps({"walls": [], "coins": []}), encoding="utf-8"
                )

                result = subprocess.run(
                    self.docker_command(
                        temp_dir,
                        "run",
                        include_data=True,
                        environment={"CCA_SECURITY_SENTINEL": "must-not-reach-player"},
                    ),
                    capture_output=True,
                    text=True,
                    timeout=40,
                )
                payload = json.loads(result.stdout)

                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn("p1_error", payload, (payload, result.stderr))
                self.assertIsNone(payload["p1_error"], (attack_name, payload, result.stderr))
                self.assertEqual(50, payload["total_turns"])


if __name__ == "__main__":
    unittest.main()
