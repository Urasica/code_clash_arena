// src/CodeTemplates.js

export const TEMPLATES = {
  python: `import sys
import json
from collections import deque

# [규칙]
# 1. 상대보다 더 많은 점수를 얻으세요.
# 2. 이동하면 해당 타일이 내 땅이 됩니다 (+1점).
# 3. 코인을 먹으면 큰 점수를 얻습니다 (+5점).
# 4. 상대 땅을 밟으면 빼앗을 수 있습니다.

def get_next_move(my_pos, coins, walls, board_size):
    # ==========================================================
    #                  🔥 YOUR STRATEGY HERE 🔥
    # ==========================================================
    # 여기에 승리 전략을 구현하세요!
    # Tip: BFS를 사용하여 가장 가까운 코인이나 빈 땅을 찾으세요.
    
    # 예시: 무조건 첫 번째 코인을 향해 직진 (벽 충돌 위험 있음)
    if coins:
        target = coins[0]
        if my_pos[0] < target[0]: return "MOVE_RIGHT"
        if my_pos[0] > target[0]: return "MOVE_LEFT"
        if my_pos[1] < target[1]: return "MOVE_DOWN"
        if my_pos[1] > target[1]: return "MOVE_UP"

    return "STAY"
    # ==========================================================


def main():
    while True:
        try:
            line = sys.stdin.readline()
            if not line: break
            state = json.loads(line)
            action = get_next_move(state['my_pos'], state['coins'], state.get('walls', []), state['board_size'])
            print(action)
            sys.stdout.flush()
        except: break

if __name__ == "__main__":
    main()`,

  java: `import java.util.*;
import java.io.*;

// [Note] 현재 Java 실행 환경은 준비 중입니다.
public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        
        while (true) {
            try {
                String line = br.readLine();
                if (line == null) break;
                
                // JSON 파싱 로직 필요 (Gson/Jackson 등)
                // 현재는 문자열 그대로 처리 예시입니다.
                
                // ==========================================================
                //                  🔥 YOUR STRATEGY HERE 🔥
                // ==========================================================
                
                String action = "STAY";
                // logic...
                
                System.out.println(action);
                System.out.flush(); // 필수
            } catch (Exception e) {
                break;
            }
        }
    }
}`,

  c: `#include <stdio.h>
#include <string.h>

// [Note] 현재 C 실행 환경은 준비 중입니다.

int main() {
    char line[4096];
    
    while (fgets(line, sizeof(line), stdin) != NULL) {
        // JSON 파싱 로직 필요
        
        // ==========================================================
        //                  🔥 YOUR STRATEGY HERE 🔥
        // ==========================================================
        
        // 예시 행동
        const char* action = "STAY";
        
        printf("%s\\n", action);
        fflush(stdout); // 필수
    }
    return 0;
}`,

  cpp: `#include <iostream>
#include <string>
#include <vector>

using namespace std;

// [Note] 현재 C++ 실행 환경은 준비 중입니다.

int main() {
    string line;
    while (getline(cin, line)) {
        // JSON 파싱 로직 필요
        
        // ==========================================================
        //                  🔥 YOUR STRATEGY HERE 🔥
        // ==========================================================
        
        string action = "STAY";
        
        cout << action << endl;
        // endl이 flush를 포함하지만 명시적으로 해도 좋음
    }
    return 0;
}`,

  javascript: `const readline = require('readline');

// [Note] 현재 Node.js 실행 환경은 준비 중입니다.

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout
});

rl.on('line', (line) => {
  try {
    const state = JSON.parse(line);
    
    // ==========================================================
    //                  🔥 YOUR STRATEGY HERE 🔥
    // ==========================================================
    
    let action = "STAY";
    
    // logic...
    
    console.log(action);
  } catch (e) {
    process.exit(0);
  }
});`
};