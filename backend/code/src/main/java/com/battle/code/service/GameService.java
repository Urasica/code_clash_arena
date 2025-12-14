package com.battle.code.service;

import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Service
@NoArgsConstructor
public class GameService {

    // 게임 실행 메인 메서드
    public String runMatch(String userCode) throws IOException, InterruptedException {
        String matchId = UUID.randomUUID().toString();
    
        // 절대 경로로 변환
        Path tempDir = Paths.get(System.getProperty("user.dir"), "temp", matchId).toAbsolutePath();
        Files.createDirectories(tempDir);

        // 파일 생성
        Files.writeString(tempDir.resolve("p1.py"), userCode);
        Files.writeString(tempDir.resolve("p2.py"), getAiCode());

        // 윈도우 경로 호환성 처리
        String hostPathForDocker = tempDir.toString().replace("\\", "/");

        // ProcessBuilder 실행
        ProcessBuilder pb = new ProcessBuilder(
            "docker", "run", "--rm",
            "-v", hostPathForDocker + ":/app/players", // 변환된 경로 사용
            "code-battle-engine", // 이미지 이름
            "python3", "referee.py"
        );
        
        pb.redirectErrorStream(true); // 에러 출력을 표준 출력과 합침
        Process process = pb.start();

        // 결과 읽기 (Python 심판이 print한 JSON 로그 수신)
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Game Engine Failed: " + output.toString());
        }

        // 뒷정리 (임시 파일 삭제)
        //deleteDirectory(hostPath);

        return output.toString();
    }

    private String getAiCode() {
        return "import sys\n" +
                "import json\n" +
                "from collections import deque\n" +
                "\n" +
                "def get_next_move(my_pos, coins, walls, board_size):\n" +
                "    # BFS로 최단 경로 찾기 (가장 가까운 코인 찾기)\n" +
                "    # 큐: (x, y, path)\n" +
                "    q = deque([ (my_pos[0], my_pos[1], []) ])\n" +
                "    visited = set([tuple(my_pos)])\n" +
                "    wall_set = set(tuple(w) for w in walls)\n" +
                "    \n" +
                "    # 코인 위치를 검색하기 쉽게 튜플 집합으로 변환 (옵션이지만 성능상 좋음)\n" +
                "    # 하지만 리스트 안에 리스트가 있는 coins 구조상, 그냥 리스트 비교가 편함\n" +
                "    \n" +
                "    while q:\n" +
                "        x, y, path = q.popleft()\n" +
                "        \n" +
                "        # [수정 포인트] 현재 위치가 코인 리스트 중 하나인가?\n" +
                "        if [x, y] in coins:\n" +
                "            return path[0] if path else \"STAY\"\n" +
                "        \n" +
                "        # 상하좌우 탐색\n" +
                "        for dx, dy, act in [(0,-1,\"MOVE_UP\"), (0,1,\"MOVE_DOWN\"), (-1,0,\"MOVE_LEFT\"), (1,0,\"MOVE_RIGHT\")]:\n" +
                "            nx, ny = x + dx, y + dy\n" +
                "            \n" +
                "            if 0 <= nx < board_size and 0 <= ny < board_size:\n" +
                "                if (nx, ny) not in visited and (nx, ny) not in wall_set:\n" +
                "                    visited.add((nx, ny))\n" +
                "                    q.append((nx, ny, path + [act]))\n" +
                "                    \n" +
                "    return \"STAY\"\n" +
                "\n" +
                "def main():\n" +
                "    while True:\n" +
                "        try:\n" +
                "            line = sys.stdin.readline()\n" +
                "            if not line: break\n" +
                "            state = json.loads(line)\n" +
                "            \n" +
                "            # [수정 포인트] coin_pos 대신 coins 리스트를 전달\n" +
                "            action = get_next_move(state['my_pos'], state['coins'], state.get('walls', []), state['board_size'])\n" +
                "            print(action)\n" +
                "            sys.stdout.flush()\n" +
                "        except:\n" +
                "            break\n" +
                "\n" +
                "if __name__ == \"__main__\":\n" +
                "    main()";
    }

//    private void deleteDirectory(hostPath) {
//
//    }
}