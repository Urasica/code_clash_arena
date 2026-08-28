import React from 'react';

const BattleMissionPanel = ({ gameData, mode }) => (
  <div className="glass-panel" style={{ height: '200px', minHeight: '200px', overflowY: 'auto' }}>
    <h3 style={{ marginTop: 0, color: 'var(--primary)', fontSize: '16px' }}>📝 MISSION OBJECTIVES</h3>
    <ul style={{ fontSize: '13px', lineHeight: '1.6', paddingLeft: '20px', color: '#ccc' }}>
      <li><strong>맵 크기:</strong> 15x15 Grid</li>
      <li><strong>시간 제한:</strong> 10분</li>
      <li><strong>목표:</strong> {mode === 'PvP' ? '상대 플레이어' : 'AI'}보다 높은 점수를 획득하세요!</li>
      <li><span style={{ color: 'gold' }}>● Coin:</span> +5 점</li>
      <li><span style={{ color: '#00f0ff' }}>■ 영역:</span> 타일당 +1 점</li>
    </ul>
    {gameData?.winner && (
      <div style={{ marginTop: '10px', padding: '10px', border: '1px solid white', textAlign: 'center', background: gameData.winner === 'p1' ? 'var(--primary)' : 'var(--danger)', color: 'black', fontWeight: 'bold' }}>
        RESULT: {gameData.winner.toUpperCase()} WIN!
      </div>
    )}
  </div>
);

export default BattleMissionPanel;
