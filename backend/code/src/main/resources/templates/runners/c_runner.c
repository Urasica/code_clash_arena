#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

// --- Data Structures ---
typedef struct {
    int x;
    int y;
} Point;

// --- Parsing Helpers (Minimal JSON Parser) ---

// 1. 키에 해당하는 값의 시작 위치 찾기
char* find_value_start(char* json, const char* key) {
    static char search[64];
    sprintf(search, "\"%s\":", key);
    char* pos = strstr(json, search);
    if (pos) return pos + strlen(search);
    return NULL;
}

// 2. 포인트 리스트 파싱: "[[1,2], [3,4]]" -> Point array
int parse_points(char* str, Point* out_arr, int max_count) {
    if (!str) return 0;

    // 리스트의 시작 '[' 찾기
    char* curr = strchr(str, '[');
    if (!curr) return 0;
    curr++; // 첫 '[' 건너뛰기

    int count = 0;
    while (*curr != '\0' && count < max_count) {
        // 리스트 종료 ']' 체크
        // 주의: 다음 키의 값으로 넘어가지 않도록, 현재 리스트 범위 내인지 확인 필요하지만
        // 여기서는 간단히 ']' 만나면 종료 (중첩 리스트 구조이므로 ']]'가 됨)
        if (*curr == ']' && *(curr+1) != ',') break;

        if (*curr == '[') {
            // 포인트 시작
            sscanf(curr + 1, "%d, %d", &out_arr[count].x, &out_arr[count].y);
            count++;

            // 현재 포인트 끝(']') 찾기
            char* end = strchr(curr, ']');
            if (!end) break;
            curr = end + 1;
        } else {
            curr++;
        }
    }
    return count;
}

// 3. 단일 포인트 파싱: "[x, y]"
Point parse_single_point(char* str) {
    Point p = {0, 0};
    if (!str) return p;
    char* start = strchr(str, '[');
    if (start) {
        sscanf(start + 1, "%d, %d", &p.x, &p.y);
    }
    return p;
}

// --- [User Code Injection] ---
// 사용자가 구현해야 할 함수 시그니처:
// const char* strategy(Point my_pos, Point* coins, int coins_len, Point* walls, int walls_len, int board_size)
%USER_CODE%
// -----------------------------

int main() {
    // 맵 데이터가 클 수 있으므로 넉넉한 버퍼 할당
    char line[1024 * 64];

    // 파싱용 배열 미리 할당 (재사용)
    Point coins[100];
    Point walls[256];

    // 출력 버퍼링 끄기 (중요: 라인 단위 전송)
    setvbuf(stdout, NULL, _IOLBF, 0);

    // 표준 입력(stdin)으로부터 한 줄씩 읽기
    while (fgets(line, sizeof(line), stdin) != NULL) {
        if (strlen(line) < 10) continue; // 빈 줄 무시

        // 1. 데이터 파싱
        int board_size = 15;
        char* bs_str = find_value_start(line, "board_size");
        if (bs_str) board_size = atoi(bs_str);

        char* mp_str = find_value_start(line, "my_pos");
        Point my_pos = parse_single_point(mp_str);

        char* c_str = find_value_start(line, "coins");
        int coins_len = parse_points(c_str, coins, 100);

        char* w_str = find_value_start(line, "walls");
        int walls_len = parse_points(w_str, walls, 256);

        // 2. 사용자 전략 실행
        // (함수 프로토타입이 없을 수 있으므로 경고 무시되거나, 유저 코드에 정의됨)
        const char* action = strategy(my_pos, coins, coins_len, walls, walls_len, board_size);

        // 3. 결과 출력
        printf("%s\n", action);
        fflush(stdout);
    }
    return 0;
}