import subprocess
import os
import json
import random
import sys

PLAYER1_CMD = ["python3", "players/p1.py"]
PLAYER2_CMD = ["python3", "players/p2.py"]

BOARD_SIZE = 10
MAX_TURNS = 20

def run_game():
    if not os.path.exists("players/p1.py") or not os.path.exists("players/p2.py"):
        print(json.dumps({"error": "Code files not found"}))
        return

    # stderr 파이프 연결
    p1 = subprocess.Popen(PLAYER1_CMD, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    p2 = subprocess.Popen(PLAYER2_CMD, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    # 생존 여부 플래그
    p1_alive = True
    p2_alive = True

    p1_pos = [0, 0]
    p2_pos = [9, 9]
    coin_pos = [random.randint(0, 9), random.randint(0, 9)]
    scores = {"p1": 0, "p2": 0}
    
    game_logs = []
    p1_error = None
    p2_error = None

    try:
        for turn in range(1, MAX_TURNS + 1):
            # 1. 데이터 준비 (상대 좌표 변환)
            state_p1 = { "turn": turn, "board_size": BOARD_SIZE, "my_pos": p1_pos, "enemy_pos": p2_pos, "coin_pos": coin_pos, "scores": scores }
            state_p2 = { "turn": turn, "board_size": BOARD_SIZE, "my_pos": p2_pos, "enemy_pos": p1_pos, "coin_pos": coin_pos, "scores": scores }

            act1 = "STAY"
            act2 = "STAY"

            # 2. P1에게 데이터 전송 및 행동 수신
            if p1_alive:
                if send_data(p1, state_p1):
                    response = get_action(p1)
                    if response:
                        act1 = response
                    else:
                        p1_alive = False # 응답 없음 -> 사망 판정
                        p1_error = read_stderr(p1) or "No Response"
                else:
                    p1_alive = False # 전송 실패 -> 사망 판정
                    p1_error = "Broken Pipe"

            # 3. P2에게 데이터 전송 및 행동 수신
            if p2_alive:
                if send_data(p2, state_p2):
                    response = get_action(p2)
                    if response:
                        act2 = response
                    else:
                        p2_alive = False
                        p2_error = read_stderr(p2)
                else:
                    p2_alive = False

            # 4. 이동 처리 (죽은 플레이어는 STAY 상태로 유지됨)
            move_player(p1_pos, act1)
            move_player(p2_pos, act2)

            # 5. 코인 획득 및 재생성
            if p1_pos == coin_pos: scores["p1"] += 1; coin_pos = [-1, -1]
            if p2_pos == coin_pos: scores["p2"] += 1; coin_pos = [-1, -1]
            
            if coin_pos == [-1, -1]:
                coin_pos = [random.randint(0, 9), random.randint(0, 9)]

            # 6. 로그 저장
            game_logs.append({
                "turn": turn,
                "p1": {"act": act1, "pos": list(p1_pos), "alive": p1_alive}, # 생존 여부도 기록
                "p2": {"act": act2, "pos": list(p2_pos), "alive": p2_alive},
                "coin": list(coin_pos),
                "scores": dict(scores)
            })

    except Exception as e:
        game_logs.append({"system_error": str(e)})

    finally:
        # 프로세스 정리
        if p1.poll() is None: p1.terminate()
        if p2.poll() is None: p2.terminate()
        
        # 승자 판정
        winner = "draw"
        if scores["p1"] > scores["p2"]: winner = "p1"
        elif scores["p2"] > scores["p1"]: winner = "p2"

        result = {
            "winner": winner,
            "final_scores": scores,
            "total_turns": len(game_logs),
            "logs": game_logs,
            "p1_error": p1_error, # 에러가 있다면 프론트엔드에 전달
            "p2_error": p2_error
        }
        print(json.dumps(result))

# --- 헬퍼 함수 ---
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
        # 에러가 있을 때만 읽음 (블로킹 방지 위해 간단히 처리)
        return process.stderr.read(1024) if process.stderr else "Unknown"
    except:
        return ""

def move_player(pos, action):
    x, y = pos[0], pos[1]
    if action == "MOVE_UP": y = max(0, y - 1)
    elif action == "MOVE_DOWN": y = min(BOARD_SIZE - 1, y + 1)
    elif action == "MOVE_LEFT": x = max(0, x - 1)
    elif action == "MOVE_RIGHT": x = min(BOARD_SIZE - 1, x + 1)
    pos[0], pos[1] = x, y

if __name__ == "__main__":
    run_game()