import json
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


def docker_is_ready():
    if not shutil.which("docker"):
        return False
    result = subprocess.run(
        ["docker", "image", "inspect", IMAGE],
        capture_output=True,
        text=True,
        timeout=10,
    )
    return result.returncode == 0


@unittest.skipUnless(docker_is_ready(), f"Docker image {IMAGE} is not available")
class RunnerIntegrationTest(unittest.TestCase):

    def docker_command(self, temp_dir, mode, include_data=False):
        command = [
            "docker", "run", "--rm",
            "--network", "none",
            "--memory", "512m",
            "--pids-limit", "128",
            "--read-only",
            "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m",
            "--cap-drop", "ALL",
            "--security-opt", "no-new-privileges",
        ]
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
                self.assertEqual(50, payload["total_turns"])
                self.assertIsNone(payload["p1_error"])


if __name__ == "__main__":
    unittest.main()
