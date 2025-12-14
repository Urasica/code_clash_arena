import sys
import json
import random

def main():
    while True:
        try:
            # 1. JSON 문자열 읽기
            line = sys.stdin.readline()
            if not line: break
            
            # 2. 파싱 (Data Parsing)
            game_state = json.loads(line)
            
            my_x, my_y = game_state['my_pos']
            coin_x, coin_y = game_state['coin_pos']
            
            # 3. 간단한 알고리즘: 코인 쪽으로 이동 (X축 우선)
            action = "STAY"
            if my_x < coin_x:
                action = "MOVE_RIGHT"
            elif my_x > coin_x:
                action = "MOVE_LEFT"
            elif my_y < coin_y:
                action = "MOVE_DOWN"
            elif my_y > coin_y:
                action = "MOVE_UP"
            
            # 4. 출력
            print(action)
            sys.stdout.flush()
            
        except Exception:
            break

if __name__ == "__main__":
    main()