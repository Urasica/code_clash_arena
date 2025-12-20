package com.battle.code.service;

import org.springframework.stereotype.Component;

@Component
public class AiCodeFactory {

    public String getAiCode(String language, String difficulty) {
        // 나중엔 언어별(Java/Cpp) AI도 지원 가능하도록 설계
        if (!"python".equals(language)) {
            return getPythonAi("easy"); // 기본값
        }
        return getPythonAi(difficulty);
    }

    private String getPythonAi(String difficulty) {
        String baseImports = "import sys\nimport json\nfrom collections import deque\nimport random\n";

        // 공통 메인 루프 (Runner 패턴 적용 전 AI용)
        String mainLoop =
                """
                        
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
                            main()""";

        switch (difficulty.toLowerCase()) {
            case "hard.py" -> {
                return baseImports +
                        "def get_next_move(my_pos, coins, walls, board_size):\n" +
                        "    q = deque([(my_pos[0], my_pos[1], [])])\n" +
                        "    visited = set([tuple(my_pos)])\n" +
                        "    wall_set = set(tuple(w) for w in walls)\n" +
                        "    while q:\n" +
                        "        x, y, path = q.popleft()\n" +
                        "        if [x, y] in coins: return path[0] if path else \"STAY\"\n" +
                        "        for dx, dy, act in [(0,-1,\"MOVE_UP\"), (0,1,\"MOVE_DOWN\"), (-1,0,\"MOVE_LEFT\"), (1,0,\"MOVE_RIGHT\")]:\n" +
                        "            nx, ny = x + dx, y + dy\n" +
                        "            if 0 <= nx < board_size and 0 <= ny < board_size:\n" +
                        "                if (nx, ny) not in visited and (nx, ny) not in wall_set:\n" +
                        "                    visited.add((nx, ny))\n" +
                        "                    q.append((nx, ny, path + [act]))\n" +
                        "    return \"STAY\"\n" +
                        mainLoop; // BFS (기존 똑똑한 코드)
            }
            case "normal" -> {
                return baseImports +
                        "def get_next_move(my_pos, coins, walls, board_size):\n" +
                        "    if not coins: return \"STAY\"\n" +
                        "    # 거리가 가장 가까운 코인 찾기 (Manhattan Distance)\n" +
                        "    target = min(coins, key=lambda c: abs(c[0]-my_pos[0]) + abs(c[1]-my_pos[1]))\n" +
                        "    if my_pos[0] < target[0]: return \"MOVE_RIGHT\"\n" +
                        "    if my_pos[0] > target[0]: return \"MOVE_LEFT\"\n" +
                        "    if my_pos[1] < target[1]: return \"MOVE_DOWN\"\n" +
                        "    if my_pos[1] > target[1]: return \"MOVE_UP\"\n" +
                        "    return \"STAY\"\n" +
                        mainLoop; // Greedy (눈앞의 코인만 쫓음, 벽 무시)
            } // Random (완전 랜덤)
            default -> {
                return baseImports +
                        "def get_next_move(my_pos, coins, walls, board_size):\n" +
                        "    return random.choice([\"MOVE_UP\", \"MOVE_DOWN\", \"MOVE_LEFT\", \"MOVE_RIGHT\"])\n" +
                        mainLoop;
            }
        }
    }
}