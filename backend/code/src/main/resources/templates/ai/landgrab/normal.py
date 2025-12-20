import sys
import json
import random

def get_next_move(my_pos, coins, walls, board_size):
    if not coins:
        # 코인이 없으면 빈 땅을 찾아 랜덤 이동 (제자리 방지)
        return random.choice(["MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT"])

    # 1. 가장 가까운 코인 찾기 (Manhattan Distance)
    target = min(coins, key=lambda c: abs(c[0]-my_pos[0]) + abs(c[1]-my_pos[1]))

    x, y = my_pos
    tx, ty = target

    # 2. 이동 후보 방향 선정 (우선순위 결정)
    # 거리가 더 먼 축을 먼저 줄이려고 시도함 (대각선 이동 효과)
    moves_x = []
    if x < tx: moves_x.append("MOVE_RIGHT")
    elif x > tx: moves_x.append("MOVE_LEFT")

    moves_y = []
    if y < ty: moves_y.append("MOVE_DOWN")
    elif y > ty: moves_y.append("MOVE_UP")

    # X거리와 Y거리 비교하여 우선순위 정렬
    dist_x = abs(tx - x)
    dist_y = abs(ty - y)

    preferred_moves = []
    if dist_x > dist_y:
        preferred_moves = moves_x + moves_y
    else:
        preferred_moves = moves_y + moves_x

    # 3. 유효성 검사 함수
    def is_valid(nx, ny):
        if 0 <= nx < board_size and 0 <= ny < board_size:
            if [nx, ny] not in walls:
                return True
        return False

    # 4. [1순위] 희망 방향으로 이동 시도
    for action in preferred_moves:
        nx, ny = x, y
        if action == "MOVE_RIGHT": nx += 1
        elif action == "MOVE_LEFT": nx -= 1
        elif action == "MOVE_DOWN": ny += 1
        elif action == "MOVE_UP": ny -= 1

        if is_valid(nx, ny):
            return action

    # 5. [2순위] 막혔을 때 '옆으로' 비켜가기 (진동 방지 핵심)
    # 가려던 방향이 막혔다면, 뒤로 가는 것(반대 방향)보다는 옆으로 가는 것이 낫다.
    secondary_moves = ["MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT"]
    random.shuffle(secondary_moves) # 약간의 무작위성을 줘서 갇힘 방지

    for action in secondary_moves:
        # 이미 시도해본 방향(preferred)은 제외할 수도 있지만,
        # 간단하게 유효한 곳이면 어디든 간다.
        nx, ny = x, y
        if action == "MOVE_RIGHT": nx += 1
        elif action == "MOVE_LEFT": nx -= 1
        elif action == "MOVE_DOWN": ny += 1
        elif action == "MOVE_UP": ny -= 1

        if is_valid(nx, ny):
            return action

    return "STAY"

def main():
    while True:
        try:
            line = sys.stdin.readline()
            if not line: break
            state = json.loads(line)
            action = get_next_move(state['my_pos'], state['coins'], state.get('walls', []), state['board_size'])
            print(action)
            sys.stdout.flush()
        except:
            break

if __name__ == "__main__":
    main()