import React, { useState } from 'react';
import Lobby from './Lobby';
import GameArena from './GameArena';

function App() {
  const [view, setView] = useState('lobby'); // 'lobby' or 'arena'
  const [selectedDifficulty, setSelectedDifficulty] = useState('easy'); // 난이도 상태 추가

  // 로비에서 게임 시작 시 호출됨
  const handleGameStart = (difficulty) => {
    setSelectedDifficulty(difficulty);
    setView('arena');
  };

  return (
    <>
      {view === 'lobby' && (
        <Lobby onStartGame={handleGameStart} />
      )}
      {view === 'arena' && (
        // GameArena에 난이도를 props로 전달
        <GameArena 
          difficulty={selectedDifficulty} 
          onBack={() => setView('lobby')} 
        />
      )}
    </>
  );
}

export default App;