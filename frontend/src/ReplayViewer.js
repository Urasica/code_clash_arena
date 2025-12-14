import React, { useEffect, useRef, useState } from 'react';

const CELL_SIZE = 40; 

const ReplayViewer = ({ gameData }) => {
  const canvasRef = useRef(null);
  const [turn, setTurn] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);

  // 게임 로그
  const logs = gameData?.logs || [];
  const maxTurn = logs.length;

  // [핵심 수정] 로그 데이터에서 맵 크기를 읽어옴 (없으면 기본값 15)
  const boardSize = logs.length > 0 && logs[0].board_size ? logs[0].board_size : 15;

  // 1. 그리기 로직
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    // 캔버스 크기 동적 설정 (이 부분이 중요!)
    canvas.width = boardSize * CELL_SIZE;
    canvas.height = boardSize * CELL_SIZE;

    // 화면 초기화
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // 격자 그리기
    ctx.strokeStyle = '#ddd';
    for (let i = 0; i <= boardSize; i++) { // boardSize 변수 사용
      ctx.beginPath();
      ctx.moveTo(i * CELL_SIZE, 0);
      ctx.lineTo(i * CELL_SIZE, boardSize * CELL_SIZE);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(0, i * CELL_SIZE);
      ctx.lineTo(boardSize * CELL_SIZE, i * CELL_SIZE);
      ctx.stroke();
    }

    // 데이터 그리기
    if (logs.length > 0 && turn < logs.length) {
      const currentLog = logs[turn];
      const p1 = currentLog.p1.pos;
      const p2 = currentLog.p2.pos;
      const coins = currentLog.coins || [];
      const walls = currentLog.walls || [];
      const board = currentLog.board || [];

      // 바닥 색칠하기
      // [수정] boardSize 변수 사용
      for (let y = 0; y < boardSize; y++) {
        for (let x = 0; x < boardSize; x++) {
          // 데이터가 아직 로딩 덜 됐을 때 에러 방지 (? 사용)
          const owner = board[y]?.[x]; 
          if (owner === 1) { 
            drawRect(ctx, x, y, 'rgba(0, 0, 255, 0.2)'); 
          } else if (owner === 2) { 
            drawRect(ctx, x, y, 'rgba(255, 0, 0, 0.2)'); 
          }
        }
      }

      // 벽 그리기
      walls.forEach(wall => drawRect(ctx, wall[0], wall[1], '#555'));

      // 코인 그리기
      coins.forEach(coin => drawCircle(ctx, coin[0], coin[1], 'gold'));

      // P1, P2 그리기
      drawRect(ctx, p1[0], p1[1], 'blue');
      drawRect(ctx, p2[0], p2[1], 'red');
    }
  }, [turn, logs, boardSize]); // 의존성 배열에 boardSize 추가

  // 2. 재생 로직
  useEffect(() => {
    let interval;
    if (isPlaying && turn < maxTurn - 1) {
      interval = setInterval(() => {
        setTurn((prev) => prev + 1);
      }, 500); 
    } else {
      setIsPlaying(false);
    }
    return () => clearInterval(interval);
  }, [isPlaying, turn, maxTurn]);

  // 헬퍼 함수
  const drawRect = (ctx, x, y, color) => {
    ctx.fillStyle = color;
    ctx.fillRect(x * CELL_SIZE + 2, y * CELL_SIZE + 2, CELL_SIZE - 4, CELL_SIZE - 4);
  };
  const drawCircle = (ctx, x, y, color) => {
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(x * CELL_SIZE + CELL_SIZE / 2, y * CELL_SIZE + CELL_SIZE / 2, CELL_SIZE / 3, 0, 2 * Math.PI);
    ctx.fill();
  };

  return (
    <div style={{ textAlign: 'center', marginTop: '20px' }}>
      <h3>Turn: {turn + 1} / {maxTurn}</h3>
      {/* 캔버스 크기는 useEffect에서 설정하므로 style에는 border만 둠 */}
      <canvas 
        ref={canvasRef} 
        style={{ border: '2px solid black' }}
      />
      <div style={{ marginTop: '10px' }}>
        <button onClick={() => setTurn(0)}>⏮ Reset</button>
        <button onClick={() => setIsPlaying(!isPlaying)}>
          {isPlaying ? '⏸ Pause' : '▶ Play'}
        </button>
        <button onClick={() => setTurn(turn + 1)} disabled={turn >= maxTurn - 1}>⏩ Next</button>
      </div>
    </div>
  );
};

export default ReplayViewer;