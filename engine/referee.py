import sys
import os
import importlib
import subprocess
import json
import shutil
import traceback

# --- 설정 상수 ---
BASE_DIR = "/app"
DATA_DIR = os.path.join(BASE_DIR, "data")
INPUT_PLAYERS_DIR = os.path.join(BASE_DIR, "players")
PLAYERS_DIR = "/run/players"
MAP_FILE = os.path.join(DATA_DIR, "map.json")
PLAYER_IDS = {"p1": 10001, "p2": 10002}
SAFE_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

# 컴파일 제한 시간 (초)
COMPILE_TIMEOUT = 10 

def stage_player(player_prefix, input_dir=INPUT_PLAYERS_DIR, runtime_dir=PLAYERS_DIR):
    """Copy one player's fixed source directory into its private tmpfs area."""
    source = os.path.join(input_dir, player_prefix)
    target = os.path.join(runtime_dir, player_prefix)
    if not os.path.isdir(source):
        raise Exception(f"Code directory not found for {player_prefix}")
    shutil.rmtree(target, ignore_errors=True)
    shutil.copytree(source, target)
    uid = PLAYER_IDS[player_prefix]
    # Work bottom-up: once a directory is handed to the player, the root
    # referee intentionally lacks DAC/FOWNER capabilities to traverse it.
    for root, _directories, files in os.walk(target, topdown=False):
        for name in files:
            path = os.path.join(root, name)
            os.chmod(path, 0o600)
            os.chown(path, uid, uid)
        os.chmod(root, 0o700)
        os.chown(root, uid, uid)
    return target


def _as_player(command, player_prefix):
    uid = PLAYER_IDS[player_prefix]
    home = os.path.join(PLAYERS_DIR, player_prefix)
    return [
        "/usr/bin/setpriv",
        f"--reuid={uid}",
        f"--regid={uid}",
        "--clear-groups",
        "--",
        "/usr/bin/env",
        "-i",
        f"PATH={SAFE_PATH}",
        f"HOME={home}",
        "LANG=C.UTF-8",
        *command,
    ]


def prepare_player(player_prefix, players_dir=PLAYERS_DIR, isolate=True):
    """
    폴더 격리 방식 적용: /app/players/{p1|p2}/Main.java 등을 찾음
    """
    # 플레이어별 서브 디렉토리 경로 (예: /app/players/p1)
    player_dir = os.path.join(players_dir, player_prefix)
    
    try:
        # 1. Java (Main.java가 서브 폴더에 있음)
        java_src = os.path.join(player_dir, "Main.java")
        if os.path.exists(java_src):
            compile_cmd = ["javac", java_src]
            if isolate:
                compile_cmd = _as_player(compile_cmd, player_prefix)
            result = subprocess.run(compile_cmd, capture_output=True, text=True, timeout=COMPILE_TIMEOUT)
            
            if result.returncode != 0:
                raise Exception(f"[Java Compilation Error]\n{result.stderr}")
            
            # [중요] 실행 시 Classpath(-cp)를 해당 폴더로 지정
            command = ["java", "-cp", player_dir, "Main"]
            return _as_player(command, player_prefix) if isolate else command

        # 2. C++ (p1.cpp가 서브 폴더에 있음)
        cpp_src = os.path.join(player_dir, f"{player_prefix}.cpp")
        if os.path.exists(cpp_src):
            out_file = os.path.join(player_dir, f"{player_prefix}.out") # 실행 파일도 그 안에 생성
            compile_cmd = ["g++", cpp_src, "-o", out_file]
            if isolate:
                compile_cmd = _as_player(compile_cmd, player_prefix)
            result = subprocess.run(compile_cmd, capture_output=True, text=True, timeout=COMPILE_TIMEOUT)
            
            if result.returncode != 0:
                raise Exception(f"[C++ Compilation Error]\n{result.stderr}")
            command = [out_file]
            return _as_player(command, player_prefix) if isolate else command

        # 3. C (p1.c가 서브 폴더에 있음)
        c_src = os.path.join(player_dir, f"{player_prefix}.c")
        if os.path.exists(c_src):
            out_file = os.path.join(player_dir, f"{player_prefix}.out")
            compile_cmd = ["gcc", c_src, "-O2", "-o", out_file]
            if isolate:
                compile_cmd = _as_player(compile_cmd, player_prefix)
            result = subprocess.run(compile_cmd, capture_output=True, text=True, timeout=COMPILE_TIMEOUT)

            if result.returncode != 0:
                raise Exception(f"[C Compilation Error]\n{result.stderr}")
            command = [out_file]
            return _as_player(command, player_prefix) if isolate else command

        # 4. Python (p1.py가 서브 폴더에 있음)
        py_src = os.path.join(player_dir, f"{player_prefix}.py")
        if os.path.exists(py_src):
            command = ["python3", py_src]
            return _as_player(command, player_prefix) if isolate else command

        # 5. Node.js
        js_src = os.path.join(player_dir, f"{player_prefix}.js")
        if os.path.exists(js_src):
            command = ["node", js_src]
            return _as_player(command, player_prefix) if isolate else command

        raise Exception(f"Code file not found for {player_prefix} in {player_dir}")

    except subprocess.TimeoutExpired:
        raise Exception(f"Compilation Timed Out ({COMPILE_TIMEOUT}s)")
    except Exception as e:
        raise e

# 헬퍼: 에러가 나도 맵은 보여주기 위해 Turn 0 로그 생성
def _make_turn0_log(game_module, map_file):
    try:
        with open(map_file, "r") as f:
            game_map = json.load(f)
        
        # 기본 초기화 데이터 (게임 모듈마다 다를 수 있지만 공통 포맷 가정)
        # LandGrab 기준
        board_size = 15
        board_state = [[0] * board_size for _ in range(board_size)]
        p1_pos = [0, 0]
        p2_pos = [board_size-1, board_size-1]
        board_state[p1_pos[1]][p1_pos[0]] = 1
        board_state[p2_pos[1]][p2_pos[0]] = 2
        
        return [{
            "turn": 0,
            "p1": {"act": "START", "pos": p1_pos, "alive": True},
            "p2": {"act": "START", "pos": p2_pos, "alive": True},
            "coins": game_map.get("coins", []),
            "walls": game_map.get("walls", []),
            "board": board_state,
            "scores": {"p1": 0, "p2": 0},
            "board_size": board_size
        }]
    except:
        return []

# [Mode 3] COMPILE: 컴파일만 수행 (검증용)
def mode_compile():
    try:
        # P1 코드만 확인
        stage_player("p1")
        prepare_player("p1")
        # 성공 시
        print(json.dumps({"status": "success"}))
    except Exception as e:
        # 실패 시 (컴파일 에러 메시지 반환)
        print(json.dumps({"status": "error", "error": str(e)}))

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(json.dumps({"error": "Usage: referee.py [game_type] [init|run|compile]"}));
        sys.exit(1)

    game_type = sys.argv[1]
    mode = sys.argv[2]

    try:
        game_module = importlib.import_module(f"games.{game_type}")

        if mode == "init":
            game_module.init(MAP_FILE)

        elif mode == "run":
            # [방어] 플레이어 준비 단계 분리 및 에러 핸들링 강화
            p1_cmd = None
            p2_cmd = None
            
            # P1 준비
            try:
                stage_player("p1")
                p1_cmd = prepare_player("p1")
            except Exception as e:
                # P1 실패 -> P2 승리 처리 + Turn 0 로그 반환
                print(json.dumps({
                    "winner": "p2",
                    "p1_error": str(e),
                    "final_scores": {"p1": 0, "p2": 0},
                    "total_turns": 0,
                    "logs": _make_turn0_log(game_module, MAP_FILE)
                }))
                sys.exit(0)

            # P2 준비
            try:
                stage_player("p2")
                p2_cmd = prepare_player("p2")
            except Exception as e:
                # P2 실패 -> P1 승리 처리
                print(json.dumps({
                    "winner": "p1",
                    "p2_error": str(e),
                    "final_scores": {"p1": 0, "p2": 0},
                    "total_turns": 0,
                    "logs": _make_turn0_log(game_module, MAP_FILE)
                }))
                sys.exit(0)

            # 게임 실행
            game_module.run(MAP_FILE, p1_cmd, p2_cmd)
        
        elif mode == "compile":
            mode_compile()

        else:
            print(json.dumps({"error": f"Unknown mode: {mode}"}))

    except ModuleNotFoundError:
        print(json.dumps({"error": f"Game module 'games.{game_type}' not found."}))
    except Exception as e:
        traceback.print_exc(file=sys.stderr)
        print(json.dumps({"error": f"System Error: {str(e)}"}))
