import contextlib
import io
import json
import random
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ENGINE_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ENGINE_DIR))

from games import land_grab


FAST_BOT = """import json, sys
for line in sys.stdin:
    state = json.loads(line)
    x, y = state['my_pos']
    if y % 2 == 0 and x < 14:
        action = 'MOVE_RIGHT'
    elif y % 2 == 1 and x > 0:
        action = 'MOVE_LEFT'
    else:
        action = 'MOVE_DOWN'
    print(action, flush=True)
"""

STAY_BOT = """import sys
for _ in sys.stdin:
    print('STAY', flush=True)
"""


class LandGrabTest(unittest.TestCase):

    def run_game(self, p1_code=FAST_BOT, p2_code=STAY_BOT):
        with tempfile.TemporaryDirectory(dir=ENGINE_DIR) as temp_dir:
            map_file = Path(temp_dir) / "map.json"
            map_file.write_text(json.dumps({"walls": [], "coins": []}), encoding="utf-8")
            output = io.StringIO()
            with contextlib.redirect_stdout(output), patch.object(
                land_grab, "_spawn_coin", return_value=None
            ):
                land_grab.run(
                    str(map_file),
                    [sys.executable, "-u", "-c", p1_code],
                    [sys.executable, "-u", "-c", p2_code],
                )
            return json.loads(output.getvalue())

    def test_generated_maps_have_unique_walls_and_reachable_coins(self):
        random.seed(20260803)

        for _ in range(100):
            game_map = land_grab._generate_map_data()
            walls = {tuple(point) for point in game_map["walls"]}
            reachable = land_grab._reachable_cells(game_map["walls"], (0, 0))

            self.assertEqual(len(game_map["walls"]), len(walls))
            self.assertEqual(45, len(walls))
            self.assertTrue(all(tuple(coin) in reachable for coin in game_map["coins"]))

    def test_final_score_includes_the_last_turn(self):
        result = self.run_game()
        final_log = result["logs"][-1]
        p1_area = sum(row.count(1) for row in final_log["board"])

        self.assertEqual(50, result["total_turns"])
        self.assertEqual(p1_area, result["final_scores"]["p1"])
        self.assertEqual(result["final_scores"], final_log["scores"])

    def test_a_timed_out_player_loses_without_hanging_the_match(self):
        sleeping_bot = "import time; time.sleep(5)"

        with patch.object(land_grab, "TURN_TIMEOUT_SECONDS", 0.05):
            result = self.run_game(p1_code=sleeping_bot)

        self.assertEqual("p2", result["winner"])
        self.assertIn("timed out", result["p1_error"])


if __name__ == "__main__":
    unittest.main()
