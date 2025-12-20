import sys
import os
import importlib
import subprocess
import json

# --- 설정 상수 ---
BASE_DIR = "/app"
DATA_DIR = os.path.join(BASE_DIR, "data")
PLAYERS_DIR = os.path.join(BASE_DIR, "players")
MAP_FILE = os.path.join(DATA_DIR, "map.json")

# 컴파일 제한 시간 (초)
COMPILE_TIMEOUT = 10 

def prepare_player(player_prefix):
    """
    언어별 실행 커맨드 생성 및 컴파일 수행 (Timeout 방어 적용)
    """
    try:
        # 1. Java (Main.java)
        if player_prefix == "p1" and os.path.exists(os.path.join(PLAYERS_DIR, "Main.java")):
            java_src = os.path.join(PLAYERS_DIR, "Main.java")
            compile_cmd = ["javac", java_src]
            # [방어] timeout 추가
            result = subprocess.run(compile_cmd, capture_output=True, text=True, timeout=COMPILE_TIMEOUT)
            if result.returncode != 0:
                raise Exception(f"[Java Compilation Error]\n{result.stderr}")
            return ["java", "-cp", PLAYERS_DIR, "Main"]

        # 2. C++ (p1.cpp)
        cpp_src = os.path.join(PLAYERS_DIR, f"{player_prefix}.cpp")
        if os.path.exists(cpp_src):
            out_file = os.path.join(PLAYERS_DIR, f"{player_prefix}.out")
            compile_cmd = ["g++", cpp_src, "-o", out_file]
            result = subprocess.run(compile_cmd, capture_output=True, text=True, timeout=COMPILE_TIMEOUT)
            if result.returncode != 0:
                raise Exception(f"[C++ Compilation Error]\n{result.stderr}")
            return [out_file]

        # 3. C (p1.c)
        c_src = os.path.join(PLAYERS_DIR, f"{player_prefix}.c")
        if os.path.exists(c_src):
            out_file = os.path.join(PLAYERS_DIR, f"{player_prefix}.out")
            compile_cmd = ["gcc", c_src, "-o", out_file]
            result = subprocess.run(compile_cmd, capture_output=True, text=True, timeout=COMPILE_TIMEOUT)
            if result.returncode != 0:
                raise Exception(f"[C Compilation Error]\n{result.stderr}")
            return [out_file]

        # 4. Python
        py_src = os.path.join(PLAYERS_DIR, f"{player_prefix}.py")
        if os.path.exists(py_src):
            return ["python3", py_src]

        # 5. Node.js
        js_src = os.path.join(PLAYERS_DIR, f"{player_prefix}.js")
        if os.path.exists(js_src):
            return ["node", js_src]

        raise Exception(f"Code file not found for {player_prefix}")

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
                p1_cmd = prepare_player("p1")
            except Exception as e:
                # P1 실패 -> P2 승리 처리 + Turn 0 로그 반환
                print(json.dumps({
                    "winner": "p2",
                    "p1_error": str(e),
                    "error": "Player 1 Initialization Failed",
                    "logs": _make_turn0_log(game_module, MAP_FILE)
                }))
                sys.exit(0)

            # P2 준비
            try:
                p2_cmd = prepare_player("p2")
            except Exception as e:
                # P2 실패 -> P1 승리 처리
                print(json.dumps({
                    "winner": "p1",
                    "p2_error": str(e),
                    "error": "Player 2 Initialization Failed",
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
        print(json.dumps({"error": f"System Error: {str(e)}"}))