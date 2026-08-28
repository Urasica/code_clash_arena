import React from 'react';
import ReplayViewer from '../../../ReplayViewer';

const BattleReplayPanel = ({ gameData, isWaitingOpponent, mode }) => (
  <div className="glass-panel" style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', background: '#000', padding: '20px', minHeight: 0 }}>
    {gameData ? (
      <ReplayViewer gameData={gameData} />
    ) : (
      <div style={{ color: '#555', fontFamily: 'Orbitron', textAlign: 'center' }}>
        <div style={{ fontSize: '40px', marginBottom: '10px' }}>{isWaitingOpponent ? '⚔️' : '📡'}</div>
        {isWaitingOpponent
          ? '전투 준비 중... 상대방을 기다리는 중입니다.'
          : (mode === 'PvP' ? '상대와 연결되었습니다. 코드를 작성하세요!' : '맵 생성을 누르면 시작합니다...')}
      </div>
    )}
  </div>
);

export default BattleReplayPanel;
