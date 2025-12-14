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
        // P2(AI)를 위한 "추적 알고리즘" 코드 문자열
        return "import sys\n" +
                "import json\n" +
                "\n" +
                "def main():\n" +
                "    while True:\n" +
                "        try:\n" +
                "            line = sys.stdin.readline()\n" +
                "            if not line: break\n" +
                "            \n" +
                "            state = json.loads(line)\n" +
                "            my_x, my_y = state['my_pos']\n" +
                "            coin_x, coin_y = state['coin_pos']\n" +
                "            \n" +
                "            action = \"STAY\"\n" +
                "            \n" +
                "            if coin_x != -1:\n" +
                "                if my_x < coin_x: action = \"MOVE_RIGHT\"\n" +
                "                elif my_x > coin_x: action = \"MOVE_LEFT\"\n" +
                "                elif my_y < coin_y: action = \"MOVE_DOWN\"\n" +
                "                elif my_y > coin_y: action = \"MOVE_UP\"\n" +
                "            \n" +
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