package com.battle.code.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@NoArgsConstructor
public class GameService {
    public Map<String, Object> startMatch() throws IOException, InterruptedException {
        String matchId = UUID.randomUUID().toString();

        // 폴더 생성
        Path matchDir = Paths.get(System.getProperty("user.dir"), "temp", matchId).toAbsolutePath();
        Files.createDirectories(matchDir);

        // Docker 실행 (INIT 모드)
        // 볼륨 마운트: matchDir -> /app/data (여기에 map.json이 생김)
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", matchDir.toString().replace("\\", "/") + ":/app/data",
                "code-battle-engine",
                "python3", "referee.py", "init" // init 인자 전달
        );

        // 결과 읽기 (JSON)
        String output = runProcessAndGetOutput(pb);

        // 프론트엔드에 줄 정보 구성
        Map<String, Object> response = new HashMap<>();
        response.put("matchId", matchId);
        response.put("map", new ObjectMapper().readValue(output, Map.class));

        return response;
    }

    // 게임 실행 메인 메서드
    public String runMatch(String matchId, String userCode) throws IOException, InterruptedException {
        // 절대 경로로 변환
        Path matchDir = Paths.get(System.getProperty("user.dir"), "temp", matchId).toAbsolutePath();
        if (!Files.exists(matchDir)) {
            throw new RuntimeException("Match ID not found or expired.");
        }

        // 파일 생성
        Files.writeString(matchDir.resolve("p1.py"), userCode);
        Files.writeString(matchDir.resolve("p2.py"), getAiCode());

        // ProcessBuilder 실행
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", matchDir.toString().replace("\\", "/") + ":/app/data",    // map.json 위치
                "-v", matchDir.toString().replace("\\", "/") + ":/app/players", // 코드 위치
                "code-battle-engine",
                "python3", "referee.py", "run" // run 인자 전달
        );

        return runProcessAndGetOutput(pb);
    }

    private String runProcessAndGetOutput(ProcessBuilder pb) throws IOException, InterruptedException {
        pb.redirectErrorStream(true);
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) output.append(line);
        process.waitFor();
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