// src/CodeTemplates.js

export const TEMPLATES = {
  // ----------------------------------------------------------------------
  // Python 3.8
  // ----------------------------------------------------------------------
  python: `from collections import deque
import random

# [Land Grab Rules]
# 1. Map Size: 15x15
# 2. Return: "MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT", "STAY"
# 3. Goal: Acquire more territory and coins than the opponent.

def strategy(my_pos, coins, walls, board_size):
    """
    Args:
        my_pos (list): [x, y]
        coins (list): [[x, y], [x, y], ...]
        walls (list): [[x, y], ...]
        board_size (int): 15
    """
    # ==========================================================
    #                  🔥 YOUR STRATEGY HERE 🔥
    # ==========================================================
    
    # Example: Simple Greedy (Move towards the first coin)
    if coins:
        target = coins[0]
        if my_pos[0] < target[0]: return "MOVE_RIGHT"
        if my_pos[0] > target[0]: return "MOVE_LEFT"
        if my_pos[1] < target[1]: return "MOVE_DOWN"
        if my_pos[1] > target[1]: return "MOVE_UP"
    
    # If no coins or stuck, move randomly
    return random.choice(["MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT"])
    # ==========================================================
`,

  // ----------------------------------------------------------------------
  // Java 17
  // ----------------------------------------------------------------------
  java: `// [Note] java.util.*, java.io.* packages are already imported.
// The code below is injected inside the 'Main' class.

public static String strategy(int[] myPos, List<int[]> coins, List<int[]> walls, int boardSize) {
    /*
     * myPos: {x, y}
     * coins: List of {x, y} arrays
     * walls: List of {x, y} arrays
     */
    
    // ==========================================================
    //                  🔥 YOUR STRATEGY HERE 🔥
    // ==========================================================
    
    // Example: Simple Greedy
    if (!coins.isEmpty()) {
        int[] target = coins.get(0);
        
        if (myPos[0] < target[0]) return "MOVE_RIGHT";
        if (myPos[0] > target[0]) return "MOVE_LEFT";
        if (myPos[1] < target[1]) return "MOVE_DOWN";
        if (myPos[1] > target[1]) return "MOVE_UP";
    }

    return "STAY";
    // ==========================================================
}
`,

  // ----------------------------------------------------------------------
  // C++ 17
  // ----------------------------------------------------------------------
  cpp: `// [Note] <iostream>, <vector>, <string>, <algorithm> are imported.
// struct Point { int x, y; }; is defined.

string strategy(Point my_pos, vector<Point> coins, vector<Point> walls, int board_size) {
    // ==========================================================
    //                  🔥 YOUR STRATEGY HERE 🔥
    // ==========================================================
    
    // Example: Simple Logic
    if (!coins.empty()) {
        Point target = coins[0];
        
        if (my_pos.x < target.x) return "MOVE_RIGHT";
        if (my_pos.x > target.x) return "MOVE_LEFT";
        if (my_pos.y < target.y) return "MOVE_DOWN";
        if (my_pos.y > target.y) return "MOVE_UP";
    }

    return "STAY";
    // ==========================================================
}
`,

  // ----------------------------------------------------------------------
  // C 11
  // ----------------------------------------------------------------------
  c: `// [Note] <stdio.h>, <stdlib.h>, <string.h> are imported.
// typedef struct { int x, y; } Point; is defined.

const char* strategy(Point my_pos, Point* coins, int coins_len, Point* walls, int walls_len, int board_size) {
    // ==========================================================
    //                  🔥 YOUR STRATEGY HERE 🔥
    // ==========================================================
    
    // Example: Greedy (Go to first coin)
    if (coins_len > 0) {
        Point target = coins[0];
        
        if (my_pos.x < target.x) return "MOVE_RIGHT";
        if (my_pos.x > target.x) return "MOVE_LEFT";
        if (my_pos.y < target.y) return "MOVE_DOWN";
        if (my_pos.y > target.y) return "MOVE_UP";
    }

    return "STAY";
    // ==========================================================
}
`,

  // ----------------------------------------------------------------------
  // JavaScript (Node.js)
  // ----------------------------------------------------------------------
  javascript: `/**
 * @param {Object} myPos - {x, y}
 * @param {Array} coins - [{x,y}, ...]
 * @param {Array} walls - [{x,y}, ...]
 * @param {Number} boardSize - 15
 */
function strategy(myPos, coins, walls, boardSize) {
    // ==========================================================
    //                  🔥 YOUR STRATEGY HERE 🔥
    // ==========================================================
    
    // Example: Simple Greedy
    if (coins.length > 0) {
        const target = coins[0]; // Node.js environments usually pass objects
        
        // Note: Check if backend sends arrays [x,y] or objects {x,y}
        // In this runner, we assume standard JSON objects for JS convenience
        // If arrays: target[0], target[1]
        
        // Assuming Array format based on common JSON:
        const tx = target[0];
        const ty = target[1];
        const mx = myPos[0];
        const my = myPos[1];
        
        if (mx < tx) return "MOVE_RIGHT";
        if (mx > tx) return "MOVE_LEFT";
        if (my < ty) return "MOVE_DOWN";
        if (my > ty) return "MOVE_UP";
    }

    return "STAY";
    // ==========================================================
}
`
};