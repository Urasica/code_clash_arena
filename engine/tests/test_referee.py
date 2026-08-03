import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

ENGINE_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ENGINE_DIR))

import referee


class RefereeTest(unittest.TestCase):

    def test_prepare_player_compiles_c_source(self):
        with tempfile.TemporaryDirectory(dir=ENGINE_DIR) as temp_dir:
            player_dir = Path(temp_dir) / "p1"
            player_dir.mkdir()
            source = player_dir / "p1.c"
            source.write_text("int main(void) { return 0; }", encoding="utf-8")
            completed = Mock(returncode=0, stderr="")

            with patch.object(referee.subprocess, "run", return_value=completed) as run:
                command = referee.prepare_player("p1", temp_dir)

            self.assertEqual(str(player_dir / "p1.out"), command[0])
            self.assertEqual("gcc", run.call_args.args[0][0])


if __name__ == "__main__":
    unittest.main()
