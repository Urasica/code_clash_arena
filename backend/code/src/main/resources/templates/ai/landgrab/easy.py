import sys, json, random
# [공통 main 루프 포함 or runner 패턴 사용] - 여기선 편의상 독립 실행형으로 작성
def main():
    while True:
        try:
            line = sys.stdin.readline()
            if not line: break
            # Random Logic
            print(random.choice(["MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT"]))
            sys.stdout.flush()
        except: break
if __name__ == "__main__": main()