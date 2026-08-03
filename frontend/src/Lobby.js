import React, { useState, useEffect, useRef } from 'react'; // useRef 추가
import { Client } from '@stomp/stompjs'; // STOMP 클라이언트
import SockJS from 'sockjs-client';      // SockJS (WebSocket 호환성)

const Lobby = ({ onStartGame, isLoggedIn, onRequestLogin, userInfo, onLogout }) => {
  const [selectedGame, setSelectedGame] = useState(null);
  const [difficulty, setDifficulty] = useState('normal');
  const [showLoginModal, setShowLoginModal] = useState(false);
  const [elapsed, setElapsed] = useState(0);

  const minutes = Math.floor(elapsed / 60);
  const seconds = elapsed % 60;

  // 매칭 상태
  const [isSearching, setIsSearching] = useState(false); // 매칭 중 여부
  const stompClient = useRef(null); // 소켓 클라이언트 객체 유지

  // 컴포넌트 언마운트 시 소켓 연결 해제 (Clean-up)
  useEffect(() => {
    return () => {
      if (stompClient.current) {
        stompClient.current.deactivate();
      }
    };
  }, []);

  useEffect(() => {
    if (!isSearching) {
      setElapsed(0);
      return;
    }

    const start = Date.now();

    const timer = setInterval(() => {
      setElapsed(Math.floor((Date.now() - start) / 1000));
    }, 1000);

    return () => clearInterval(timer);
  }, [isSearching]);


  // 게임 시작 핸들러
  const handleStart = () => {
    if (!isLoggedIn) {
      setShowLoginModal(true); // 브라우저 alert 대신 커스텀 모달 띄움
      return;
    }
    // 게임 시작
    onStartGame(difficulty);
  };

  // 로그인 모달 확인 버튼
  const handleLoginConfirm = () => {
    setShowLoginModal(false);
    onRequestLogin(); // 로그인 페이지로 이동
  };

  // ---------------------------------------------------------
  // [NEW] PvP 매칭 시작 (WebSocket 연결)
  // ---------------------------------------------------------
  const handlePvPStart = () => {
    if (!isLoggedIn) {
      setShowLoginModal(true);
      return;
    }

    setIsSearching(true); // UI를 '매칭 중' 상태로 변경

    // 1. 소켓 클라이언트 설정
    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws-stomp'), // 백엔드 주소
      debug: (str) => {
        console.log(str);
      },
      // 연결 성공 시 실행될 콜백
      onConnect: () => {
        console.log("✅ Connected to WebSocket");

        // 2. 내 전용 채널 구독 (매칭 성공 신호 받기 위함)
        // 주소: /topic/match/{userId}
        client.subscribe(`/topic/match/${userInfo.userId}`, (message) => {
          const matchData = JSON.parse(message.body);
          console.log("🎉 Match Found!", matchData);
          
          // 매칭 성공! -> 상태 초기화 후 게임 화면으로 이동
          setIsSearching(false);
          stompClient.current.deactivate(); // 소켓 끊고 이동
          
          // onStartGame에 매칭 정보를 넘겨줌 (App.js나 GameArena에서 처리 필요)
          onStartGame('pvp', matchData); 
        });

        // 3. 대기열 참가 요청 전송
        client.publish({
            destination: '/app/match/join',
            body: JSON.stringify({ gameType: 'land_grab' }),
        });
      },
      onStompError: (frame) => {
        console.error('Broker reported error: ' + frame.headers['message']);
        console.error('Additional details: ' + frame.body);
        setIsSearching(false);
      },
    });

    // 소켓 활성화
    client.activate();
    stompClient.current = client;
  };

  // ---------------------------------------------------------
  // [NEW] 매칭 취소
  // ---------------------------------------------------------
  const handlePvPCancel = () => {
    if (stompClient.current && stompClient.current.connected) {
        // 취소 메시지 전송
        stompClient.current.publish({
            destination: '/app/match/cancel',
            body: JSON.stringify({ gameType: 'land_grab' }),
        });
        // 연결 끊기
        stompClient.current.deactivate();
    }
    setIsSearching(false);
  };

  // 게임 카드 데이터 (한글화 적용)
  const GAMES = [
    {
      id: 'land_grab',
      title: 'LAND GRAB',
      desc: '알고리즘으로 영토를 점령하는\n전략 땅따먹기 배틀.',
      status: 'ONLINE',
      color: 'var(--primary)'
    },
    {
      id: 'snake',
      title: 'SNAKE SURVIVAL',
      desc: '최적의 경로를 계산하여 생존하는\n클래식 스네이크 게임.',
      status: '준비 중',
      color: '#ff9800',
      locked: true
    },
    {
      id: 'rts',
      title: 'UNIT COMMANDER',
      desc: '유닛을 직접 제어하여 전투하는\n실시간 전략 시뮬레이션.',
      status: '잠김',
      color: '#f44336',
      locked: true
    }
  ];

  return (
    <div className="cyber-container" style={{ textAlign: 'center', marginTop: '40px', position: 'relative' }}>
      
      {/* 1. Header & User Info */}
      <h1 className="cyber-title" style={{ fontSize: '3.5rem', marginBottom: '10px' }}>
        CODE CRASH ARENA<br/>
        코드 크래쉬 아레나
      </h1>
      <p style={{ color: 'var(--text-dim)', marginBottom: '40px', fontSize: '16px' }}>
        <b>알고리즘 서바이벌 플랫폼: {selectedGame ? '모드를 선택하세요' : '도전할 게임을 선택하세요'}</b>
      </p>

      {/* 우측 상단 유저 상태 */}
      <div style={{ position: 'absolute', top: '10px', right: '20px', fontSize: '14px', zIndex: 10 }}>
        {isLoggedIn ? (
            <div style={{ textAlign: 'right', display: 'flex', alignItems: 'center', gap: '15px' }}>
                <div>
                    <span style={{ color: 'var(--primary)', fontWeight: 'bold', display: 'block', textShadow: '0 0 5px var(--primary)' }}>
                        🟢 {userInfo?.nickname || 'USER'}
                    </span>
                </div>
                <button 
                    className="cyber-button secondary" 
                    style={{ fontSize: '12px', padding: '5px 12px', height: '34px', borderColor: '#444', color: '#aaa' }}
                    onClick={onLogout}
                >
                    로그아웃
                </button>
            </div>
        ) : (
            <button 
                className="cyber-button secondary" 
                style={{ fontSize: '12px', padding: '5px 15px' }}
                onClick={onRequestLogin}
            >
                로그인 / 게스트
            </button>
        )}
      </div>

      {/* 2. Main Content Area */}
      
      {/* STEP 1: 게임 선택 화면 */}
      {!selectedGame && (
        <div style={{ 
          display: 'grid', 
          // [수정] 박스 크기 키움 (minmax 250px -> 320px)
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', 
          gap: '40px', 
          maxWidth: '1200px', // 전체 폭도 조금 넓힘
          margin: '0 auto', 
          padding: '0 20px' 
        }}>
          {GAMES.map((game) => (
            <div 
              key={game.id}
              className="glass-panel" 
              style={{ 
                textAlign: 'left', 
                borderTop: `4px solid ${game.color}`,
                opacity: game.locked ? 0.6 : 1,
                cursor: game.locked ? 'not-allowed' : 'pointer',
                transition: 'transform 0.2s, box-shadow 0.2s',
                minHeight: '220px', // [수정] 카드 높이 확보
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-between',
                padding: '30px'    // [수정] 내부 여백 확대
              }}
              onClick={() => !game.locked && setSelectedGame(game.id)}
              onMouseEnter={(e) => { if(!game.locked) e.currentTarget.style.transform = 'translateY(-10px)'; }}
              onMouseLeave={(e) => { if(!game.locked) e.currentTarget.style.transform = 'translateY(0)'; }}
            >
              <div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
                  <h2 style={{ color: game.color, margin: 0, fontSize: '1.8rem', fontFamily: 'Orbitron' }}>{game.title}</h2>
                  {game.locked && <span style={{ fontSize: '20px' }}>🔒</span>}
                </div>
                <p style={{ fontSize: '15px', color: '#ccc', lineHeight: '1.6', whiteSpace: 'pre-line' }}>
                  {game.desc}
                </p>
              </div>
              <div style={{ marginTop: '20px', fontSize: '13px', fontWeight: 'bold', color: game.locked ? '#666' : 'white', display: 'flex', alignItems: 'center', gap: '5px' }}>
                 STATUS: <span style={{ color: game.locked ? '#666' : 'var(--success)' }}>{game.status}</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* STEP 2: 모드(난이도) 선택 화면 */}
      {selectedGame === 'land_grab' && (
        <div style={{ maxWidth: '900px', margin: '0 auto', animation: 'fadeIn 0.5s' }}>
          
          {/* 뒤로가기 버튼 */}
          <div style={{ textAlign: 'left', marginBottom: '20px', paddingLeft: '20px' }}>
            <button 
              className="cyber-button secondary" 
              onClick={() => setSelectedGame(null)}
              style={{ padding: '8px 20px', fontSize: '14px' }}
            >
              ◀ 게임 목록으로
            </button>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '30px' }}>
            {/* 모드 1: AI 매치 */}
            <div className="glass-panel" style={{ textAlign: 'left', borderTop: '4px solid var(--primary)', position: 'relative', padding: '30px' }}>
              <div style={{ position: 'absolute', top: '-10px', left: '20px', background: 'var(--primary)', color: 'black', padding: '2px 8px', fontSize: '12px', fontWeight: 'bold' }}>
                추천 모드
              </div>
               <div style={{ position: 'absolute', top: '15px', right: '15px', fontSize: '24px'}}>🤖</div>
              <h2 style={{ color: 'var(--primary)', marginTop: '10px' }}>AI 챌린지</h2>
              <p style={{ fontSize: '14px', color: '#aaa', minHeight: '60px', lineHeight: '1.6' }}>
                알고리즘 봇과 1:1 대결을 펼칩니다.<br/>
                PvP 진입 전 자신의 논리를 테스트해보세요.
              </p>
              
              <div style={{ marginTop: '20px', background: 'rgba(0,0,0,0.3)', padding: '20px', borderRadius: '4px' }}>
                <label style={{ display: 'block', marginBottom: '8px', fontSize: '12px', color: 'var(--primary)' }}>
                  난이도 선택
                </label>
                <select 
                  className="cyber-input" 
                  style={{ marginBottom: '15px', width: '100%' }}
                  value={difficulty}
                  onChange={(e) => setDifficulty(e.target.value)}
                >
                  <option value="easy">Easy (무작위 행동)</option>
                  <option value="normal">Normal (greedy 알고리즘)</option>
                  <option value="hard">Hard (BFS 최단경로)</option>
                </select>
                <button className="cyber-button" style={{ width: '100%', height: '45px', fontSize: '16px' }} onClick={handleStart}>
                  전투 시작
                </button>
              </div>
            </div>

            {/* 모드 2: PvP (Locked) */}
            <div className="glass-panel" style={{ textAlign: 'left', borderTop: '4px solid var(--secondary)', position: 'relative', padding: '30px' }}>
               <div style={{ position: 'absolute', top: '15px', right: '15px', fontSize: '24px' }}>⚔️</div>
              <h2 style={{ color: 'var(--secondary)', marginTop: '10px' }}>PvP 랭킹전</h2>
              <p style={{ fontSize: '14px', color: '#aaa', minHeight: '60px', lineHeight: '1.6' }}>
                실시간으로 다른 플레이어와 경쟁합니다.<br/>
                승리하여 포인트를 획득하고 랭킹을 올리세요.
              </p>
              
              <div style={{ marginTop: '20px', background: 'rgba(0,0,0,0.3)', padding: '20px', borderRadius: '4px' }}>
                <div style={{ marginBottom: '15px', fontSize: '12px', color: '#888' }}>
                    CURRENT SEASON: <span style={{ color: 'white' }}>ALPHA</span>
                </div>
                
                {/* 매칭 중일 때와 아닐 때 버튼 변경 */}
                {!isSearching ? (
                    <button 
                        className="cyber-button secondary" 
                        style={{ width: '100%', height: '45px', fontSize: '16px' }} 
                        onClick={handlePvPStart}
                    >
                        매칭 시작
                    </button>
                ) : (
                    <button 
                        className="cyber-button" 
                        style={{ width: '100%', height: '45px', fontSize: '16px', background: 'var(--secondary)', borderColor: '#666', color: '#fff' }} 
                        onClick={handlePvPCancel}
                    >
                        매칭 취소 (검색 중...)
                    </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 3. Footer */}
      <div style={{ marginTop: '80px', fontSize: '12px', color: '#555' }}>
        SYSTEM STATUS: <span style={{ color: 'var(--success)' }}>ONLINE ●</span> | SERVER: ASIA-SEOUL-1
      </div>

      {/* 4. 로그인 모달 */}
      {showLoginModal && (
        <div style={{
          position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
          background: 'rgba(0, 0, 0, 0.85)', backdropFilter: 'blur(8px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000
        }}>
          <div className="glass-panel" style={{ 
            width: '420px', padding: '40px', textAlign: 'center', 
            border: '1px solid var(--primary)', boxShadow: '0 0 40px rgba(0, 255, 255, 0.15)' 
          }}>
            <div style={{ fontSize: '60px', marginBottom: '20px' }}>🛡️</div>
            <h2 style={{ color: 'white', fontFamily: 'Orbitron', marginBottom: '15px', letterSpacing: '2px' }}>
              ACCESS DENIED
            </h2>
            <p style={{ color: '#ccc', fontSize: '15px', marginBottom: '35px', lineHeight: '1.6' }}>
              아레나에 입장하려면 로그인이 필요합니다.<br/>
              전적 기록을 위해 로그인하거나<br/>
              게스트 모드로 플레이하세요!
            </p>
            
            <div style={{ display: 'flex', gap: '15px', justifyContent: 'center' }}>
              <button 
                className="cyber-button secondary" 
                style={{ flex: 1, borderColor: '#666', color: '#aaa' }}
                onClick={() => setShowLoginModal(false)}
              >
                취소
              </button>
              <button 
                className="cyber-button" 
                style={{ flex: 1 }}
                onClick={handleLoginConfirm}
              >
                로그인 / 게스트
              </button>
            </div>
          </div>
        </div>
      )}

      {isSearching && (
        <div style={{
            position: 'fixed', top: 0, left: 0, width: '100%', height: '100%',
            background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(3px)',
            display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
            zIndex: 2000
        }}>
            <div className="glass-panel" style={{ padding: '40px', width: '300px', textAlign: 'center', border: '1px solid var(--secondary)', boxShadow: '0 0 20px var(--secondary)' }}>
                <div className="spinner" style={{ 
                    width: '50px', height: '50px', border: '5px solid #333', 
                    borderTop: '5px solid var(--secondary)', borderRadius: '50%', 
                    margin: '0 auto 20px', animation: 'spin 1s linear infinite' 
                }}></div>
                <h2 style={{ color: 'white', marginBottom: '10px' }}>SEARCHING...</h2>
                <p style={{ color: '#aaa', fontSize: '14px' }}>상대 할 플레이어를 찾고 있습니다.</p>
                <div style={{ marginTop: '20px', fontSize: '20px', fontFamily: 'monospace' }}>
                  {minutes.toString().padStart(2, '0')}:
                  {seconds.toString().padStart(2, '0')}
                </div>

                <button 
                    style={{ 
                        marginTop: '30px', background: 'transparent', border: 'none', 
                        color: '#666', textDecoration: 'underline', cursor: 'pointer' 
                    }}
                    onClick={handlePvPCancel}
                >
                    취소하기
                </button>
            </div>
            
            {/* CSS Animation injection */}
            <style>{`
                @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }
            `}</style>
        </div>
      )}
    </div>
  );
};

export default Lobby;
