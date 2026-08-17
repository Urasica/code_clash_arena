import React, { useState, useEffect } from 'react';
import Lobby from './Lobby';
import GameArena from './GameArena';
import LoginPage from './LoginPage';
import { getSession, logout } from './features/auth/authApi';
import { consumeOAuthError } from './features/auth/oauthErrors';

function App() {
  const [view, setView] = useState('lobby'); // 'lobby', 'login', 'arena'
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [difficulty, setDifficulty] = useState('normal');
  const [userInfo, setUserInfo] = useState(null); // 유저 정보 저장 (닉네임 등)
  const [matchData, setMatchData] = useState(null); // PvP 매치 정보
  const [oauthError, setOauthError] = useState(null);

  useEffect(() => {
     const redirectError = consumeOAuthError();
     if (redirectError) {
         setOauthError(redirectError);
         setView('login');
     }

     const checkLoginStatus = async () => {
         try {
             const res = await getSession();

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
        const res = await getSession();
        setIsLoggedIn(true);
        setUserInfo(res.data);
        setView('lobby'); 
    } catch(e) {
        console.error("Login verification failed");
    }
  };

  const handleLogout = async () => {
    try {
      await logout();
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
            oauthError={oauthError}
            onOAuthStart={() => setOauthError(null)}
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
