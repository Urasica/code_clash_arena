// src/App.js
import React, { useState } from 'react';
import Lobby from './Lobby';
import GameArena from './GameArena';

function App() {
  const [view, setView] = useState('lobby'); // 'lobby' or 'arena'

  return (
    <>
      {view === 'lobby' && (
        <Lobby onStartGame={() => setView('arena')} />
      )}
      {view === 'arena' && (
        <GameArena onBack={() => setView('lobby')} />
      )}
    </>
  );
}

export default App;