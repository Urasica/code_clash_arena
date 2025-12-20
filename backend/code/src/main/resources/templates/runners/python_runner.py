import sys
import json
import random
from collections import deque

# --- [User Code Injection] ---
# 사용자 코드는 'strategy(my_pos, coins, walls, board_size)' 함수를 구현해야 함
%USER_CODE%
# -----------------------------

def main():
    while True:
        try:
            line = sys.stdin.readline()
            if not line: break

            state = json.loads(line)

            # 사용자 함수 호출
            if 'strategy' in globals():
                action = strategy(state['my_pos'], state['coins'], state.get('walls', []), state['board_size'])
            else:
                action = "STAY" # 함수명이 틀렸을 경우 안전장치

            print(action)
            sys.stdout.flush()
        except Exception:
            break

if __name__ == "__main__":
    main()