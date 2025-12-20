// src/Lobby.js
import React, { useState } from 'react';

const Lobby = ({ onStartGame }) => {
  const [difficulty, setDifficulty] = useState('normal'); 

  const handleStart = () => {
    // 부모 컴포넌트에 선택한 난이도 전달
    onStartGame(difficulty);
  };

  return (
    <div className="cyber-container" style={{ textAlign: 'center', marginTop: '50px' }}>
      <h1 className="cyber-title" style={{ fontSize: '4rem', marginBottom: '10px' }}>
        CODE BATTLE ARENA<br/>코드 배틀 아레나
      </h1>
      <p style={{ color: 'var(--text-dim)', marginBottom: '50px' }}>
        Algorithm Survival Platform: Prove your logic!
      </p>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '30px', maxWidth: '800px', margin: '0 auto' }}>
        {/* 모드 1: AI 매치 */}
        <div className="glass-panel" style={{ textAlign: 'left', borderTop: '4px solid var(--primary)' }}>
          <h2 style={{ color: 'var(--primary)' }}>🤖 AI Challenge</h2>
          <p style={{ fontSize: '14px', color: '#aaa', minHeight: '60px' }}>
             난이도별 알고리즘을 사용하는 봇과 <br/>
             1:1 대결을 펼치세요. <br/>
             최종적으로는 당신의 코드를 읽는<br/>
            AI를 이겨보세요!
          </p>
          <div style={{ marginTop: '20px' }}>
            <label style={{ display: 'block', marginBottom: '5px', fontSize: '12px' }}>DIFFICULTY</label>
            <select 
              className="cyber-input" 
              style={{ marginBottom: '15px' }}
              value={difficulty}
              onChange={(e) => setDifficulty(e.target.value)}
            >
              {/* value 값은 백엔드 파일명(easy, normal, hard)과 일치해야 함 */}
              <option value="easy">Easy (Random)</option>
              <option value="normal">Normal (Greedy)</option>
              <option value="hard">Hard (BFS/DFS)</option>
            </select>
            <button className="cyber-button" style={{ width: '100%' }} onClick={handleStart}>
              START MATCH
            </button>
          </div>
        </div>

        {/* 모드 2: 유저 매치 (준비중) */}
        <div className="glass-panel" style={{ textAlign: 'left', borderTop: '4px solid var(--secondary)', opacity: 0.7 }}>
          <h2 style={{ color: 'var(--secondary)' }}>⚔️ PvP Ranked</h2>
          <p style={{ fontSize: '14px', color: '#aaa', minHeight: '60px' }}>
            실시간으로 다른 플레이어와 매칭됩니다.<br/>
            (현재 서버 점검 중)
          </p>
          <div style={{ marginTop: '20px' }}>
             <button className="cyber-button secondary" style={{ width: '100%' }} disabled>
              LOCKED
            </button>
          </div>
        </div>
      </div>

      <div style={{ marginTop: '50px', fontSize: '12px', color: '#555' }}>
        SYSTEM STATUS: ONLINE <span style={{ color: 'var(--success)' }}>●</span>
      </div>
    </div>
  );
};

export default Lobby;