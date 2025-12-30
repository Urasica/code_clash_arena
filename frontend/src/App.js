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
     // 1. [New] 구글 로그인 리다이렉트 처리 (URL 파라미터 확인)
     const params = new URLSearchParams(window.location.search);
     const userIdParam = params.get("userId");
     const tokenParam = params.get("accessToken");

     if (userIdParam) {
         // URL에 userId가 있으면 저장 (구글 로그인 직후)
         localStorage.setItem("userId", userIdParam);
         localStorage.setItem("token", tokenParam);
         console.log("🔑 [Google Login] User ID saved:", userIdParam);
         
         // URL 파라미터 제거 (깔끔하게)
         window.history.replaceState({}, document.title, "/");
     }

     // 2. 세션(쿠키) 확인 로직
     const checkLoginStatus = async () => {
         try {
             const res = await axios.get('http://localhost:8080/api/auth/me', {
                 withCredentials: true 
             });

             if (res.status === 200 && res.data.userId) {
                 console.log("Session Restored:", res.data);
                 setIsLoggedIn(true);
                 setUserInfo(res.data);
                 
                 // [보완] 세션 확인 시에도 localStorage 동기화 (새로고침 대비)
                 if (!localStorage.getItem("userId")) {
                     localStorage.setItem("userId", res.data.userId);
                 }
             }
         } catch (err) {
             console.log("Not logged in");
             setIsLoggedIn(false);
             setUserInfo(null);
             // 로그아웃 상태면 스토리지도 정리
             localStorage.removeItem("token");
             localStorage.removeItem("userId");
         }
     };

     checkLoginStatus();
  }, []); 

  const handleLoginSuccess = async () => {
    try {
        const res = await axios.get('http://localhost:8080/api/auth/me', { withCredentials: true });
        setIsLoggedIn(true);
        setUserInfo(res.data);
        
        // [보완] 일반 로그인 성공 시에도 저장
        if (res.data.userId) {
            localStorage.setItem("userId", res.data.userId);
        }
        
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
      localStorage.removeItem("token");
      localStorage.removeItem("userId"); // [추가] 로그아웃 시 삭제
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