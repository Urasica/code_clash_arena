import React, { useState } from 'react';
import axios from 'axios';

// [수정] onBack prop 추가 (로비로 돌아가기 기능)
const LoginPage = ({ onLoginSuccess, onBack }) => {
  const [mode, setMode] = useState('login'); // 'login' or 'signup'
  const [formData, setFormData] = useState({ username: '', password: '', nickname: '' });

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleLocalAuth = async () => {
    try {
      const endpoint = mode === 'login' ? '/api/auth/login' : '/api/auth/signup';
      await axios.post(`http://localhost:8080${endpoint}`, formData, { withCredentials: true });
      
      if (mode === 'login') onLoginSuccess();
      else {
        alert("회원가입 성공! 로그인해주세요.");
        setMode('login');
      }
    } catch (err) {
      alert("Error: " + (err.response?.data || err.message));
    }
  };

  const handleGuestLogin = async () => {
    try {
      await axios.post('http://localhost:8080/api/auth/guest', {}, { withCredentials: true });
      onLoginSuccess();
    } catch (err) {
      alert("Guest Login Failed");
    }
  };

  const handleGoogleLogin = () => {
    window.location.href = "http://localhost:8080/oauth2/authorization/google";
  };

  return (
    <div className="cyber-container" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', position: 'relative' }}>
      
      {/* [추가] 뒤로가기 버튼 (좌측 상단) */}
      <div style={{ position: 'absolute', top: '20px', left: '20px' }}>
        <button 
          className="cyber-button secondary" 
          onClick={onBack}
          style={{ fontSize: '12px', padding: '5px 15px' }}
        >
          ◀ 로비로 돌아가기
        </button>
      </div>

      <div className="glass-panel" style={{ width: '400px', padding: '40px', textAlign: 'center' }}>
        <h1 className="cyber-title" style={{ fontSize: '2.5rem', marginBottom: '30px' }}>CODE BATTLE</h1>
        
        {/* Local Login Form */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
          <input 
            type="text" name="username" placeholder="아이디" className="cyber-input"
            onChange={handleChange} value={formData.username}
          />
          <input 
            type="password" name="password" placeholder="비밀번호" className="cyber-input"
            onChange={handleChange} value={formData.password}
          />
          {mode === 'signup' && (
            <input 
              type="text" name="nickname" placeholder="닉네임" className="cyber-input"
              onChange={handleChange} value={formData.nickname}
            />
          )}
          
          <button className="cyber-button" onClick={handleLocalAuth}>
            {mode === 'login' ? '로그인' : '회원가입'}
          </button>
        </div>

        <div style={{ margin: '20px 0', color: '#666', fontSize: '12px' }}>OR</div>

        {/* Social & Guest */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <button 
            className="cyber-button secondary" 
            style={{ background: '#fff', color: '#333', borderColor: '#ddd' }}
            onClick={handleGoogleLogin}
          >
            <span style={{ marginRight: '10px', fontWeight: 'bold' }}>G</span> Google 로그인
          </button>
          
          <button 
            className="cyber-button secondary" 
            onClick={handleGuestLogin}
          >
            👾 게스트로 플레이
          </button>
        </div>

        <div style={{ marginTop: '20px', fontSize: '12px' }}>
          {mode === 'login' ? (
            <span style={{ color: '#888', cursor: 'pointer' }} onClick={() => setMode('signup')}>계정 생성</span>
          ) : (
            <span style={{ color: '#888', cursor: 'pointer' }} onClick={() => setMode('login')}>로그인으로 돌아가기</span>
          )}
        </div>
      </div>
    </div>
  );
};

export default LoginPage;