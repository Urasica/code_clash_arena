import subprocess
import os
import json
import random
import sys

# --- 설정 상수 ---
MAP_FILE = "data/map.json"        # 맵 데이터 공유 파일 경로
PLAYER1_CMD = ["python3", "players/p1.py"]
PLAYER2_CMD = ["python3", "players/p2.py"]

BOARD_SIZE = 15     # 맵 크기
MAX_TURNS = 50      # 총 턴 수
WALL_RATIO = 0.2    # 벽 생성 비율
COIN_SCORE = 5      # 코인 1개당 점수

# ==========================================
# [Mode 1] INIT: 맵 생성 및 저장
# ==========================================
def mode_init():
    # 1. 맵 데이터 생성
    game_map = generate_map_data()
    
    # 2. 파일로 저장 (Run 모드에서 사용하기 위해)
    # data 디렉토리가 없으면 생성
    os.makedirs(os.path.dirname(MAP_FILE), exist_ok=True)
    
    with open(MAP_FILE, "w") as f:
        json.dump(game_map, f)
    
    # 3. 프론트엔드/백엔드 전송용 JSON 출력
    print(json.dumps(game_map))

# ==========================================
# [Mode 2] RUN: 저장된 맵으로 게임 실행
# ==========================================
def mode_run():
    # 1. 맵 파일 로드
    if not os.path.exists(MAP_FILE):
        error_result = {
            "winner": "system",
            "error": "Map file not found. Please run 'init' mode first."
        }
        print(json.dumps(error_result))
        return

    with open(MAP_FILE, "r") as f:
        game_map = json.load(f)

    walls = game_map["walls"]
    coins = game_map["coins"]

    # 2. 플레이어 코드 확인
    if not os.path.exists("players/p1.py") or not os.path.exists("players/p2.py"):
        print(json.dumps({"error": "Player code files not found inside Docker."}))
        return

    # 3. 게임 상태 초기화
    # 바닥 색칠 정보 (0: 빈 땅, 1: P1, 2: P2)
    board_state = [[0] * BOARD_SIZE for _ in range(BOARD_SIZE)]
    
    p1_pos = [0, 0]
    p2_pos = [BOARD_SIZE-1, BOARD_SIZE-1]
    
    # 시작점 점령
    board_state[p1_pos[1]][p1_pos[0]] = 1
    board_state[p2_pos[1]][p2_pos[0]] = 2

    p1_coins_count = 0
    p2_coins_count = 0
    scores = {"p1": 0, "p2": 0}

    # 4. 프로세스 실행
    p1 = subprocess.Popen(PLAYER1_CMD, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    p2 = subprocess.Popen(PLAYER2_CMD, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

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
        "scores": dict(scores)
    })

    # ------------------------------------------
    # [Phase 1] 메인 게임 루프 (Turn 1 ~ MAX)
    # ------------------------------------------
    try:
        for turn in range(1, MAX_TURNS + 1):
            # 점수 계산
            area_p1 = sum(row.count(1) for row in board_state)
            area_p2 = sum(row.count(2) for row in board_state)
            scores["p1"] = (p1_coins_count * COIN_SCORE) + area_p1
            scores["p2"] = (p2_coins_count * COIN_SCORE) + area_p2

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
                if send_data(p1, state_p1):
                    resp = get_action(p1)
                    if resp: act1 = resp
                    else: p1_alive = False; p1_error = read_stderr(p1) or "No Response"
                else: p1_alive = False; p1_error = "Broken Pipe"

            # P2 통신
            if p2_alive:
                if send_data(p2, state_p2):
                    resp = get_action(p2)
                    if resp: act2 = resp
                    else: p2_alive = False; p2_error = read_stderr(p2)
                else: p2_alive = False

            # 이동 처리
            move_player(p1_pos, act1, walls)
            move_player(p2_pos, act2, walls)

            # 땅따먹기 (색칠)
            if board_state[p1_pos[1]][p1_pos[0]] != 1: board_state[p1_pos[1]][p1_pos[0]] = 1
            if board_state[p2_pos[1]][p2_pos[0]] != 2: board_state[p2_pos[1]][p2_pos[0]] = 2

            # 코인 획득 및 리스폰
            coins_to_remove = []
            for c in coins:
                if p1_pos == c:
                    p1_coins_count += 1
                    if c not in coins_to_remove: coins_to_remove.append(c)
                if p2_pos == c:
                    p2_coins_count += 1
                    if c not in coins_to_remove: coins_to_remove.append(c)
            
            for c in coins_to_remove:
                coins.remove(c)
                if len(coins) < 3:
                    new_c = spawn_coin(walls, coins, [p1_pos, p2_pos])
                    if new_c: coins.append(new_c)

            # 로그 저장
            game_logs.append({
                "turn": turn,
                "p1": {"act": act1, "pos": list(p1_pos), "alive": p1_alive},
                "p2": {"act": act2, "pos": list(p2_pos), "alive": p2_alive},
                "coins": list(coins),
                "walls": walls,
                "board": [row[:] for row in board_state],
                "scores": dict(scores)
            })

    except Exception as e:
        game_logs.append({"system_error": str(e)})

    finally:
        if p1.poll() is None: p1.terminate()
        if p2.poll() is None: p2.terminate()
        
        winner = "draw"
        if scores["p1"] > scores["p2"]: winner = "p1"
        elif scores["p2"] > scores["p1"]: winner = "p2"

        result = {
            "winner": winner,
            "final_scores": scores,
            "total_turns": len(game_logs) - 1, # 0턴 제외
            "logs": game_logs,
            "p1_error": p1_error,
            "p2_error": p2_error
        }
        print(json.dumps(result))

# ==========================================
# Helper Functions
# ==========================================
def generate_map_data():
    walls = []
    # 벽 생성
    for _ in range(int(BOARD_SIZE * BOARD_SIZE * WALL_RATIO)):
        w = [random.randint(0, BOARD_SIZE-1), random.randint(0, BOARD_SIZE-1)]
        # 시작점 근처 보호
        if w not in [[0,0], [BOARD_SIZE-1, BOARD_SIZE-1], [0,1], [1,0], [BOARD_SIZE-1, BOARD_SIZE-2], [BOARD_SIZE-2, BOARD_SIZE-1]]: 
            walls.append(w)
    # 코인 생성
    coins = []
    while len(coins) < 5:
        c = [random.randint(0, BOARD_SIZE-1), random.randint(0, BOARD_SIZE-1)]
        if c not in walls and c not in coins and c not in [[0,0], [BOARD_SIZE-1, BOARD_SIZE-1]]:
            coins.append(c)
    return {"walls": walls, "coins": coins}

def spawn_coin(walls, coins, players):
    for _ in range(100):
        c = [random.randint(0, BOARD_SIZE-1), random.randint(0, BOARD_SIZE-1)]
        if c not in walls and c not in coins and c not in players:
            return c
    return None

def send_data(process, data):
    try:
        process.stdin.write(json.dumps(data) + "\n")
        process.stdin.flush()
        return True
    except: return False

def get_action(process):
    try:
        line = process.stdout.readline()
        if not line: return None
        return line.strip()
    except: return None

def read_stderr(process):
    try: return process.stderr.read(1024) if process.stderr else ""
    except: return ""

def move_player(pos, action, walls):
    x, y = pos[0], pos[1]
    nx, ny = x, y
    if action == "MOVE_UP": ny -= 1
    elif action == "MOVE_DOWN": ny += 1
    elif action == "MOVE_LEFT": nx -= 1
    elif action == "MOVE_RIGHT": nx += 1
    
    if 0 <= nx < BOARD_SIZE and 0 <= ny < BOARD_SIZE:
        if [nx, ny] not in walls:
            pos[0], pos[1] = nx, ny

# ==========================================
# Main Entry Point
# ==========================================
if __name__ == "__main__":
    # 첫 번째 인자가 'init'이면 맵 생성 모드, 아니면 실행 모드
    if len(sys.argv) > 1 and sys.argv[1] == "init":
        mode_init()
    else:
        mode_run()