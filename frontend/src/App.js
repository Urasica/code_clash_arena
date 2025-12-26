import React, { useState, useEffect } from 'react';
import axios from 'axios'; // axios import 확인
import Lobby from './Lobby';
import GameArena from './GameArena';
import LoginPage from './LoginPage';

function App() {
  const [view, setView] = useState('lobby'); // 'lobby', 'login', 'arena'
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [difficulty, setDifficulty] = useState('normal');
  const [userInfo, setUserInfo] = useState(null); // 유저 정보 저장 (닉네임 등)

  // [핵심] 앱 로드 시 세션(쿠키) 확인 로직
  useEffect(() => {
     const checkLoginStatus = async () => {
         try {
             // 쿠키(HttpOnly)는 JS로 못 읽으므로, 백엔드에 "나 누구야?"라고 물어봄
             const res = await axios.get('http://localhost:8080/api/auth/me', {
                 withCredentials: true // [필수] 쿠키를 실어 보내야 함
             });

             if (res.status === 200 && res.data.userId) {
                 console.log("Session Restored:", res.data);
                 setIsLoggedIn(true);
                 setUserInfo(res.data);
             }
         } catch (err) {
             // 401 Unauthorized 등 에러 -> 로그인 안 된 상태 (조용히 무시)
             console.log("Not logged in");
             setIsLoggedIn(false);
             setUserInfo(null);
         }
     };

     checkLoginStatus();
  }, []); // 빈 배열: 최초 1회 실행

  const handleLoginSuccess = async () => {
    // 로그인 직후 정보 갱신을 위해 다시 호출 (또는 로그인 API 응답을 바로 써도 됨)
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
      // 성공하든 실패하든 클라이언트 상태는 초기화
      setIsLoggedIn(false);
      setUserInfo(null);
      setView('lobby'); 
    }
  };

  const handleStartGameRequest = (selectedDifficulty) => {
    setDifficulty(selectedDifficulty);
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
          onBack={() => setView('lobby')} 
        />
      )}
    </>
  );
}

export default App;