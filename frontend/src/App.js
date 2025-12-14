import React, { useState } from 'react';
import axios from 'axios';
import ReplayViewer from './ReplayViewer';

function App() {
  const [userCode, setUserCode] = useState(`import sys
import random
while True:
    line = sys.stdin.readline()
    if not line: break
    # 랜덤으로 움직이는 봇
    actions = ["MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT"]
    print(random.choice(actions))
    sys.stdout.flush()`);
    
  const [gameData, setGameData] = useState(null);
  const [loading, setLoading] = useState(false);

  const runGame = async () => {
    setLoading(true);
    try {
      // [수정] GET -> POST 변경
      // params 대신 요청 Body 객체로 전달
      const response = await axios.post('http://localhost:8080/test/match', {
        userCode: userCode
      });
      
      setGameData(response.data);
    } catch (error) {
      alert("Error: " + error.message);
    }
    setLoading(false);
  };

  return (
    <div style={{ padding: '20px', display: 'flex', gap: '20px' }}>
      {/* 왼쪽: 코드 입력창 */}
      <div style={{ width: '40%' }}>
        <h2>📝 Code Editor</h2>
        <textarea 
          value={userCode} 
          onChange={(e) => setUserCode(e.target.value)}
          style={{ width: '100%', height: '400px', fontFamily: 'monospace' }}
        />
        <br />
        <button 
          onClick={runGame} 
          disabled={loading}
          style={{ marginTop: '10px', padding: '10px 20px', fontSize: '16px' }}
        >
          {loading ? 'Running...' : '⚔️ Run Battle'}
        </button>
      </div>

      {/* 오른쪽: 리플레이 뷰어 */}
      <div style={{ width: '60%' }}>
        <h2>📺 Replay</h2>
        {gameData ? (
          <ReplayViewer gameData={gameData} />
        ) : (
          <p>Press "Run Battle" to start simulation.</p>
        )}
      </div>
    </div>
  );
}

export default App;