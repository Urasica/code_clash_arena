#include <iostream>
#include <string>
#include <vector>
#include <sstream>
#include <regex>

using namespace std;

struct Point { int x, y; };

// --- Helpers ---
vector<Point> parsePoints(string json, string key) {
    vector<Point> res;
    size_t start = json.find("\"" + key + "\"");
    if (start == string::npos) return res;

    size_t bracketOpen = json.find("[", start);
    size_t bracketClose = json.find("]]", bracketOpen); // 2차원 배열 끝
    if (bracketClose == string::npos) bracketClose = json.find("]", bracketOpen);

    string arrStr = json.substr(bracketOpen, bracketClose - bracketOpen + 1);

    regex re("\\[(\\d+),\\s*(\\d+)\\]");
    sregex_iterator next(arrStr.begin(), arrStr.end(), re);
    sregex_iterator end;
    while (next != end) {
        smatch match = *next;
        res.push_back({stoi(match[1]), stoi(match[2])});
        next++;
    }
    return res;
}

int parseSize(string json) {
    regex re("\"board_size\":\\s*(\\d+)");
    smatch match;
    if (regex_search(json, match, re)) return stoi(match[1]);
    return 15;
}

// --- [User Code Injection] ---
// string strategy(Point my_pos, vector<Point> coins, vector<Point> walls, int board_size)
%USER_CODE%
// -----------------------------

int main() {
    string line;
    while (getline(cin, line)) {
        if (line.empty()) break;

        try {
            // Parsing
            int boardSize = parseSize(line);
            vector<Point> coins = parsePoints(line, "coins");
            vector<Point> walls = parsePoints(line, "walls");

            // my_pos는 단일 좌표지만 파서 재활용을 위해 리스트로 파싱 후 첫번째 사용
            vector<Point> myPosList = parsePoints(line, "my_pos");
            Point myPos = myPosList.empty() ? Point{0,0} : myPosList[0];

            string action = strategy(myPos, coins, walls, boardSize);
            cout << action << endl; // endl flushes buffer
        } catch (...) {
            cout << "STAY" << endl;
        }
    }
    return 0;
}