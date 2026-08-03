import React, { useState, useEffect } from 'react';
import axios from 'axios';
import Lobby from './Lobby';
import GameArena from './GameArena';
import LoginPage from './LoginPage';

function App() {
  const [view, setView] = useState('lobby'); // 'lobby', 'login', 'arena'
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [difficulty, setDifficulty] = useState('normal');
  const [userInfo, setUserInfo] = useState(null); // 유저 정보 저장 (닉네임 등)
  const [matchData, setMatchData] = useState(null); // PvP 매치 정보

  useEffect(() => {
     const checkLoginStatus = async () => {
         try {
             const res = await axios.get('http://localhost:8080/api/auth/me', {
                 withCredentials: true 
             });

             if (res.status === 200 && res.data.userId) {
                 console.log("Session Restored:", res.data);
                 setIsLoggedIn(true);
                 setUserInfo(res.data);
             }
         } catch (err) {
             console.log("Not logged in");
             setIsLoggedIn(false);
             setUserInfo(null);
         }
     };

     checkLoginStatus();
  }, []); 

  const handleLoginSuccess = async () => {
    try {
        const res = await axios.get('http://localhost:8080/api/auth/me', { withCredentials: true });
        setIsLoggedIn(true);
        setUserInfo(res.data);
        setView('lobby'); 
    } catch(e) {
        console.error("Login verification failed");
    }
  };

  const handleLogout = async () => {
    try {
      await axios.post('http://localhost:8080/api/auth/logout', {}, { withCredentials: true });
    } catch (err) {
      console.error("Logout request failed", err);
    } finally {
      setIsLoggedIn(false);
      setUserInfo(null);
      setView('lobby'); 
    }
  };

  const handleStartGameRequest = (selectedDifficulty, pvpData = null) => {
    setDifficulty(selectedDifficulty);
    
    if (pvpData) {
        setMatchData(pvpData); 
        setDifficulty('pvp'); 
    } else {
        setMatchData(null);
    }
    
    setView('arena');
  };

  return (
    <>
      {view === 'lobby' && (
        <Lobby 
            isLoggedIn={isLoggedIn}
            userInfo={userInfo}
            onStartGame={handleStartGameRequest}
            onRequestLogin={() => setView('login')}
            onLogout={handleLogout}
        />
      )}
      
      {view === 'login' && (
        <LoginPage 
            onLoginSuccess={handleLoginSuccess} 
            onBack={() => setView('lobby')} 
        />
      )}

      {view === 'arena' && (
        <GameArena 
          difficulty={difficulty} 
          matchData={matchData} 
          onBack={() => setView('lobby')} 
        />
      )}
    </>
  );
}

export default App;
