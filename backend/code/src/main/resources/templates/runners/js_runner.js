const readline = require('readline');

// --- [User Code Injection] ---
// function strategy(myPos, coins, walls, boardSize) { ... }
%USER_CODE%
// -----------------------------

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

rl.on('line', (line) => {
  if (!line) return;
  try {
    const state = JSON.parse(line);
    const myPos = state.my_pos;
    const coins = state.coins;
    const walls = state.walls || [];
    const boardSize = state.board_size;

    let action = "STAY";

    // 사용자 함수 호출 확인
    if (typeof strategy === 'function') {
        action = strategy(myPos, coins, walls, boardSize);
    }

    console.log(action);
  } catch (e) {
    console.log("STAY");
  }
});