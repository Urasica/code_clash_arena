import subprocess
import os
import json
import random
import sys
import queue
import threading
from collections import deque

# --- 게임 설정 상수 ---
BOARD_SIZE = 15     # 맵 크기
MAX_TURNS = 50      # 총 턴 수
WALL_RATIO = 0.2    # 벽 생성 비율
COIN_SCORE = 5      # 코인 1개당 점수
TURN_TIMEOUT_SECONDS = 0.5
VALID_ACTIONS = {"MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT", "STAY"}

# ==========================================
# [Mode 1] INIT: 맵 생성 및 저장
# ==========================================
def init(map_file):
    # 1. 맵 데이터 생성
    game_map = _generate_map_data()
    
    # 2. 파일로 저장
    os.makedirs(os.path.dirname(map_file), exist_ok=True)
    with open(map_file, "w") as f:
        json.dump(game_map, f)
    
    # 3. 결과 출력 (백엔드 전달용)
    print(json.dumps(game_map))

# ==========================================
# [Mode 2] RUN: 게임 루프 실행
# ==========================================
def run(map_file, p1_cmd, p2_cmd):
    # 1. 맵 파일 로드
    if not os.path.exists(map_file):
        print(json.dumps({"error": "Map file not found"}))
        return

    with open(map_file, "r") as f:
        game_map = json.load(f)

    walls = game_map["walls"]
    coins = game_map["coins"]

    # 2. 게임 상태 초기화
    board_state = [[0] * BOARD_SIZE for _ in range(BOARD_SIZE)]
    
    p1_pos = [0, 0]
    p2_pos = [BOARD_SIZE-1, BOARD_SIZE-1]
    
    # 시작점 점령
    board_state[p1_pos[1]][p1_pos[0]] = 1
    board_state[p2_pos[1]][p2_pos[0]] = 2

    p1_coins_count = 0
    p2_coins_count = 0
    scores = _calculate_scores(board_state, p1_coins_count, p2_coins_count)

    # 3. 프로세스 실행 (Dispatcher가 준 커맨드 사용)
    try:
        p1 = subprocess.Popen(p1_cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        p2 = subprocess.Popen(p2_cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    except Exception as e:
        print(json.dumps({"error": f"Failed to start player process: {str(e)}"}))
        return

    p1_alive = True
    p2_alive = True
    p1_error = None
    p2_error = None
    
    game_logs = []

    # ------------------------------------------
    # [Phase 0] 초기 상태 기록 (Turn 0)
    # ------------------------------------------
    game_logs.append({
        "turn": 0,
        "p1": {"act": "START", "pos": list(p1_pos), "alive": True},
        "p2": {"act": "START", "pos": list(p2_pos), "alive": True},
        "coins": list(coins),
        "walls": walls,
        "board": [row[:] for row in board_state],
        "scores": dict(scores),
        "board_size": BOARD_SIZE
    })

    # ------------------------------------------
    # [Phase 1] 메인 게임 루프
    # ------------------------------------------
    try:
        for turn in range(1, MAX_TURNS + 1):
            # 데이터 준비
            common_data = {
                "turn": turn, "board_size": BOARD_SIZE, 
                "coins": coins, "scores": scores, "walls": walls, "board": board_state
            }
            state_p1 = {**common_data, "my_pos": p1_pos, "enemy_pos": p2_pos}
            state_p2 = {**common_data, "my_pos": p2_pos, "enemy_pos": p1_pos}

            act1 = "STAY"
            act2 = "STAY"

            # P1 통신
            if p1_alive:
                if _send_data(p1, state_p1):
                    resp, response_error = _get_action(p1)
                    if resp:
                        act1 = resp if resp in VALID_ACTIONS else "STAY"
                    else:
                        p1_alive = False
                        p1_error = response_error or _read_stderr(p1) or "No Response"
                else: 
                    p1_alive = False
                    p1_error = "Broken Pipe"

            # P2 통신
            if p2_alive:
                if _send_data(p2, state_p2):
                    resp, response_error = _get_action(p2)
                    if resp:
                        act2 = resp if resp in VALID_ACTIONS else "STAY"
                    else:
                        p2_alive = False
                        p2_error = response_error or _read_stderr(p2) or "No Response"
                else: 
                    p2_alive = False
                    p2_error = "Broken Pipe"

            # 이동 처리
            _move_player(p1_pos, act1, walls)
            _move_player(p2_pos, act2, walls)

            # 땅따먹기 (색칠)
            if board_state[p1_pos[1]][p1_pos[0]] != 1: board_state[p1_pos[1]][p1_pos[0]] = 1
            if board_state[p2_pos[1]][p2_pos[0]] != 2: board_state[p2_pos[1]][p2_pos[0]] = 2

            # 코인 획득 및 리스폰
            coins_to_remove = []
            for c in coins:
                if p1_pos == c:
                    p1_coins_count += 1
                    coins_to_remove.append(c)
                if p2_pos == c:
                    p2_coins_count += 1
                    if c not in coins_to_remove: coins_to_remove.append(c)
            
            for c in coins_to_remove:
                if c in coins: coins.remove(c)
            
            if len(coins) < 3:
                new_c = _spawn_coin(walls, coins, [p1_pos, p2_pos])
                if new_c: coins.append(new_c)

            # 현재 턴의 이동, 점령, 코인 획득을 모두 반영한 뒤 점수를 계산한다.
            scores = _calculate_scores(board_state, p1_coins_count, p2_coins_count)

            # 로그 저장
            game_logs.append({
                "turn": turn,
                "p1": {"act": act1, "pos": list(p1_pos), "alive": p1_alive},
                "p2": {"act": act2, "pos": list(p2_pos), "alive": p2_alive},
                "coins": list(coins),
                "walls": walls,
                "board": [row[:] for row in board_state],
                "scores": dict(scores),
                "board_size": BOARD_SIZE
            })

    except Exception as e:
        game_logs.append({"system_error": str(e)})

    finally:
        _stop_process(p1)
        _stop_process(p2)
        
        winner = "draw"
        if p1_error and not p2_error:
            winner = "p2"
        elif p2_error and not p1_error:
            winner = "p1"
        elif scores["p1"] > scores["p2"]:
            winner = "p1"
        elif scores["p2"] > scores["p1"]:
            winner = "p2"

        result = {
            "winner": winner,
            "final_scores": scores,
            "total_turns": len(game_logs) - 1,
            "logs": game_logs,
            "p1_error": p1_error,
            "p2_error": p2_error
        }
        print(json.dumps(result))

# ==========================================
# Helper Functions (Internal)
# ==========================================
def _generate_map_data():
    while True:  # ✔️ 유효한 맵이 나올 때까지 반복
        wall_count = int(BOARD_SIZE * BOARD_SIZE * WALL_RATIO)
        candidates = [
            (x, y)
            for y in range(BOARD_SIZE)
            for x in range(BOARD_SIZE)
            if (x, y) not in {(0, 0), (BOARD_SIZE - 1, BOARD_SIZE - 1)}
        ]
        walls = [list(point) for point in random.sample(candidates, wall_count)]

        if not is_reachable(walls):
            continue  # ❌ 갇혔으면 다시 생성

        reachable = _reachable_cells(walls, (0, 0))
        coin_candidates = list(reachable - {(0, 0), (BOARD_SIZE - 1, BOARD_SIZE - 1)})
        if len(coin_candidates) < 5:
            continue
        coins = [list(point) for point in random.sample(coin_candidates, 5)]

        return {"walls": walls, "coins": coins}


def _spawn_coin(walls, coins, players):
    start = tuple(players[0]) if players else (0, 0)
    reachable = _reachable_cells(walls, start)
    excluded = {tuple(point) for point in coins + players}
    candidates = list(reachable - excluded)
    return list(random.choice(candidates)) if candidates else None

def _send_data(process, data):
    try:
        process.stdin.write(json.dumps(data) + "\n")
        process.stdin.flush()
        return True
    except: return False

def _get_action(process):
    result_queue = queue.Queue(maxsize=1)

    def read_line():
        try:
            result_queue.put((process.stdout.readline(), None))
        except Exception as exc:
            result_queue.put((None, str(exc)))

    reader = threading.Thread(target=read_line, daemon=True)
    reader.start()

    try:
        line, error = result_queue.get(timeout=TURN_TIMEOUT_SECONDS)
    except queue.Empty:
        _stop_process(process)
        return None, f"Turn response timed out after {TURN_TIMEOUT_SECONDS:.3f}s"

    if error:
        return None, error
    if not line:
        return None, None
    return line.strip(), None

def _read_stderr(process):
    try:
        if process.poll() is None:
            return ""
        return process.stderr.read(1024) if process.stderr else ""
    except: return ""

def _stop_process(process):
    try:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=0.2)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=0.2)
    finally:
        for stream in (process.stdin, process.stdout, process.stderr):
            if stream and not stream.closed:
                stream.close()

def _calculate_scores(board_state, p1_coins_count, p2_coins_count):
    area_p1 = sum(row.count(1) for row in board_state)
    area_p2 = sum(row.count(2) for row in board_state)
    return {
        "p1": (p1_coins_count * COIN_SCORE) + area_p1,
        "p2": (p2_coins_count * COIN_SCORE) + area_p2,
    }

def _move_player(pos, action, walls):
    x, y = pos[0], pos[1]
    nx, ny = x, y
    if action == "MOVE_UP": ny -= 1
    elif action == "MOVE_DOWN": ny += 1
    elif action == "MOVE_LEFT": nx -= 1
    elif action == "MOVE_RIGHT": nx += 1
    
    if 0 <= nx < BOARD_SIZE and 0 <= ny < BOARD_SIZE:
        if [nx, ny] not in walls:
            pos[0], pos[1] = nx, ny

def is_reachable(walls):
    return (BOARD_SIZE - 1, BOARD_SIZE - 1) in _reachable_cells(walls, (0, 0))

def _reachable_cells(walls, start):
    wall_set = set(map(tuple, walls))
    visited = {start}
    q = deque([start])

    while q:
        x, y = q.popleft()

        for dx, dy in [(1,0), (-1,0), (0,1), (0,-1)]:
            nx, ny = x+dx, y+dy
            if 0 <= nx < BOARD_SIZE and 0 <= ny < BOARD_SIZE:
                if (nx, ny) not in wall_set and (nx, ny) not in visited:
                    visited.add((nx, ny))
                    q.append((nx, ny))
    return visited
