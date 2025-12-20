import sys
import json
import random
from collections import deque

def find_nearest_reachable_target(start, target_type, coins, walls, board_size, board):
    """
    BFS로 탐색하며 가장 먼저 발견한 '도달 가능한' 목표물의 첫 번째 이동 방향을 반환
    target_type: 'COIN' or 'EMPTY'
    """
    q = deque([(start[0], start[1], [])]) # x, y, path
    visited = set([tuple(start)])
    wall_set = set(tuple(w) for w in walls)
    coin_set = set(tuple(c) for c in coins)

    while q:
        x, y, path = q.popleft()

        # 1. 목표 발견 체크
        if target_type == 'COIN':
            if (x, y) in coin_set:
                return path[0] if path else None
        elif target_type == 'EMPTY':
            # 보드 위에서 빈 땅(0)이고, 벽이 아닌 곳
            if board[y][x] == 0 and (x, y) not in wall_set:
                return path[0] if path else None

        # 2. 상하좌우 탐색
        for dx, dy, act in [(0,-1,"MOVE_UP"), (0,1,"MOVE_DOWN"), (-1,0,"MOVE_LEFT"), (1,0,"MOVE_RIGHT")]:
            nx, ny = x + dx, y + dy

            if 0 <= nx < board_size and 0 <= ny < board_size:
                if (nx, ny) not in visited and (nx, ny) not in wall_set:
                    visited.add((nx, ny))
                    q.append((nx, ny, path + [act]))
    return None

def get_next_move(my_pos, coins, walls, board_size, board):
    # 1순위: 가장 가까운 '도달 가능한' 코인 찾기
    if coins:
        move = find_nearest_reachable_target(my_pos, 'COIN', coins, walls, board_size, board)
        if move: return move

    # 2순위: 코인이 없거나 막혔다면, 가장 가까운 '도달 가능한' 빈 땅 찾기
    move = find_nearest_reachable_target(my_pos, 'EMPTY', coins, walls, board_size, board)
    if move: return move

    # 3순위: 다 막혔다면 랜덤 이동 (갇힘 방지)
    valid_moves = []
    wall_set = set(tuple(w) for w in walls)
    cx, cy = my_pos
    for dx, dy, act in [(0,-1,"MOVE_UP"), (0,1,"MOVE_DOWN"), (-1,0,"MOVE_LEFT"), (1,0,"MOVE_RIGHT")]:
        nx, ny = cx + dx, cy + dy
        if 0 <= nx < board_size and 0 <= ny < board_size:
            if (nx, ny) not in wall_set:
                valid_moves.append(act)

    return random.choice(valid_moves) if valid_moves else "STAY"

def main():
    while True:
        try:
            line = sys.stdin.readline()
            if not line: break
            state = json.loads(line)
            action = get_next_move(
                state['my_pos'],
                state['coins'],
                state.get('walls', []),
                state['board_size'],
                state.get('board', [])
            )
            print(action)
            sys.stdout.flush()
        except: break

if __name__ == "__main__":
    main()