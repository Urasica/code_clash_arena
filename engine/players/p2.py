import sys
import json

def main():
    while True:
        try:
            line = sys.stdin.readline()
            if not line: break
            
            state = json.loads(line)
            my_x, my_y = state['my_pos']
            coin_x, coin_y = state['coin_pos']
            
            action = "STAY"
            
            # 코인이 없으면(-1,-1) 가만히 있는다
            if coin_x != -1:
                if my_x < coin_x: action = "MOVE_RIGHT"
                elif my_x > coin_x: action = "MOVE_LEFT"
                elif my_y < coin_y: action = "MOVE_DOWN"
                elif my_y > coin_y: action = "MOVE_UP"
            
            print(action)
            sys.stdout.flush()
            
        except:
            break

if __name__ == "__main__":
    main()