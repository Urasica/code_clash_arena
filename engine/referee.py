import subprocess
import os
import json
import random
import sys

# 플레이어 코드 경로
PLAYER1_CMD = ["python3", "players/p1.py"]
PLAYER2_CMD = ["python3", "players/p2.py"]

# 게임 설정
BOARD_SIZE = 15     # 맵 크기
MAX_TURNS = 50      # 턴 수
WALL_RATIO = 0.2    # 벽 비율
COIN_SCORE = 5      # 코인 점수 (코인 1개 = 땅 5칸 가치)

def run_game():
    if not os.path.exists("players/p1.py") or not os.path.exists("players/p2.py"):
        print(json.dumps({"error": "Code files not found"}))
        return

    # 1. 맵 생성 (벽, 코인)
    game_map = generate_map()
    walls = game_map["walls"]
    coins = game_map["coins"]

    # 2. 바닥 색칠 정보 초기화 (0: 빈 땅, 1: P1 땅, 2: P2 땅)
    board_state = [[0] * BOARD_SIZE for _ in range(BOARD_SIZE)]

    # 플레이어 시작 위치
    p1_pos = [0, 0]
    p2_pos = [BOARD_SIZE-1, BOARD_SIZE-1]
    
    # 시작점 점령 처리
    board_state[p1_pos[1]][p1_pos[0]] = 1
    board_state[p2_pos[1]][p2_pos[0]] = 2

    # 획득한 코인 수
    p1_coins_count = 0
    p2_coins_count = 0
    
    # 점수판
    scores = {"p1": 0, "p2": 0}

    # 프로세스 실행 (에러 파이프 연결)
    p1 = subprocess.Popen(PLAYER1_CMD, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    p2 = subprocess.Popen(PLAYER2_CMD, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    p1_alive = True
    p2_alive = True
    
    game_logs = []
    p1_error = None
    p2_error = None

    try:
        for turn in range(1, MAX_TURNS + 1):
            # 3. 실시간 점수 계산 (코인 점수 + 땅 점수)
            area_p1 = sum(row.count(1) for row in board_state)
            area_p2 = sum(row.count(2) for row in board_state)
            
            scores["p1"] = (p1_coins_count * COIN_SCORE) + area_p1
            scores["p2"] = (p2_coins_count * COIN_SCORE) + area_p2

            # 4. 플레이어에게 보낼 데이터 준비
            # (board 정보를 보내주면 AI가 빈 땅을 찾아갈 수 있음)
            common_data = {
                "turn": turn, 
                "board_size": BOARD_SIZE, 
                "coins": coins, 
                "scores": scores,
                "walls": walls,
                "board": board_state # 현재 맵 색칠 현황
            }
            
            # 상대좌표 개념 적용
            state_p1 = {**common_data, "my_pos": p1_pos, "enemy_pos": p2_pos}
            state_p2 = {**common_data, "my_pos": p2_pos, "enemy_pos": p1_pos}

            act1 = "STAY"
            act2 = "STAY"

            # 5. P1 통신 및 행동 수신
            if p1_alive:
                if send_data(p1, state_p1):
                    response = get_action(p1)
                    if response: act1 = response
                    else:
                        p1_alive = False
                        p1_error = read_stderr(p1) or "No Response"
                else:
                    p1_alive = False
                    p1_error = "Broken Pipe"

            # 6. P2 통신 및 행동 수신
            if p2_alive:
                if send_data(p2, state_p2):
                    response = get_action(p2)
                    if response: act2 = response
                    else:
                        p2_alive = False
                        p2_error = read_stderr(p2)
                else:
                    p2_alive = False

            # 7. 이동 처리 (벽 충돌 체크 포함)
            move_player(p1_pos, act1, walls)
            move_player(p2_pos, act2, walls)

            # 8. [핵심] 이동 후 바닥 색칠하기 (땅따먹기)
            # P1이 밟은 땅 색칠
            if board_state[p1_pos[1]][p1_pos[0]] != 1:
                board_state[p1_pos[1]][p1_pos[0]] = 1
            
            # P2가 밟은 땅 색칠
            if board_state[p2_pos[1]][p2_pos[0]] != 2:
                board_state[p2_pos[1]][p2_pos[0]] = 2

            # 9. 코인 획득 처리
            # (동시에 같은 코인에 도착하면 둘 다 획득 인정)
            coins_to_remove = []
            for c in coins:
                # P1 획득
                if p1_pos == c:
                    p1_coins_count += 1
                    if c not in coins_to_remove: coins_to_remove.append(c)
                # P2 획득
                if p2_pos == c:
                    p2_coins_count += 1
                    if c not in coins_to_remove: coins_to_remove.append(c)
            
            # 먹은 코인 삭제 및 리스폰
            for c in coins_to_remove:
                coins.remove(c)
                # 코인이 3개 미만이면 새로 생성
                if len(coins) < 3:
                    new_coin = spawn_coin(walls, coins, [p1_pos, p2_pos])
                    if new_coin: coins.append(new_coin)

            # 10. 로그 저장 (board 상태 전체 저장 - 리플레이용)
            game_logs.append({
                "turn": turn,
                "p1": {"act": act1, "pos": list(p1_pos), "alive": p1_alive},
                "p2": {"act": act2, "pos": list(p2_pos), "alive": p2_alive},
                "coins": list(coins),   # 코인 리스트 복사
                "walls": walls,         # 벽 리스트
                "board": [row[:] for row in board_state], # [중요] 2차원 배열 깊은 복사
                "scores": dict(scores)
            })

    except Exception as e:
        game_logs.append({"system_error": str(e)})

    finally:
        # 프로세스 정리
        if p1.poll() is None: p1.terminate()
        if p2.poll() is None: p2.terminate()
        
        # 최종 승자 판정
        winner = "draw"
        if scores["p1"] > scores["p2"]: winner = "p1"
        elif scores["p2"] > scores["p1"]: winner = "p2"

        result = {
            "winner": winner,
            "final_scores": scores,
            "total_turns": len(game_logs),
            "logs": game_logs,
            "p1_error": p1_error,
            "p2_error": p2_error
        }
        print(json.dumps(result))

# --- 헬퍼 함수들 ---

def generate_map():
    walls = []
    # 맵의 20%를 벽으로 채움
    for _ in range(int(BOARD_SIZE * BOARD_SIZE * WALL_RATIO)):
        w = [random.randint(0, BOARD_SIZE-1), random.randint(0, BOARD_SIZE-1)]
        # 시작점 근처 보호
        if w not in [[0,0], [BOARD_SIZE-1, BOARD_SIZE-1], [0,1], [1,0], [BOARD_SIZE-1, BOARD_SIZE-2], [BOARD_SIZE-2, BOARD_SIZE-1]]: 
            walls.append(w)
    
    # 코인 5개 생성
    coins = []
    while len(coins) < 5:
        c = [random.randint(0, BOARD_SIZE-1), random.randint(0, BOARD_SIZE-1)]
        if c not in walls and c not in coins and c not in [[0,0], [BOARD_SIZE-1, BOARD_SIZE-1]]:
            coins.append(c)
            
    return {"walls": walls, "coins": coins}

def spawn_coin(walls, coins, players):
    for _ in range(100): # 최대 100번 시도
        c = [random.randint(0, BOARD_SIZE-1), random.randint(0, BOARD_SIZE-1)]
        if c not in walls and c not in coins and c not in players:
            return c
    return None

def send_data(process, data):
    try:
        process.stdin.write(json.dumps(data) + "\n")
        process.stdin.flush()
        return True
    except (BrokenPipeError, IOError):
        return False

def get_action(process):
    try:
        line = process.stdout.readline()
        if not line: return None
        return line.strip()
    except Exception:
        return None

def read_stderr(process):
    try:
        return process.stderr.read(1024) if process.stderr else ""
    except:
        return ""

def move_player(pos, action, walls):
    x, y = pos[0], pos[1]
    nx, ny = x, y
    
    if action == "MOVE_UP": ny -= 1
    elif action == "MOVE_DOWN": ny += 1
    elif action == "MOVE_LEFT": nx -= 1
    elif action == "MOVE_RIGHT": nx += 1
    
    # 맵 범위 및 벽 충돌 체크
    if 0 <= nx < BOARD_SIZE and 0 <= ny < BOARD_SIZE:
        if [nx, ny] not in walls:
            pos[0], pos[1] = nx, ny

if __name__ == "__main__":
    run_game()