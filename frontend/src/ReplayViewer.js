import React, { useEffect, useRef, useState } from 'react';

const BOARD_SIZE = 10;
const CELL_SIZE = 40; // 격자 한 칸 크기 (픽셀)

const ReplayViewer = ({ gameData }) => {
  const canvasRef = useRef(null);
  const [turn, setTurn] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);

  // 게임 로그 (없으면 빈 배열)
  const logs = gameData?.logs || [];
  const maxTurn = logs.length;

  // 1. 그리기 로직 (Canvas)
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    // 화면 초기화
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // 격자 그리기
    ctx.strokeStyle = '#ddd';
    for (let i = 0; i <= BOARD_SIZE; i++) {
      ctx.beginPath();
      ctx.moveTo(i * CELL_SIZE, 0);
      ctx.lineTo(i * CELL_SIZE, BOARD_SIZE * CELL_SIZE);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(0, i * CELL_SIZE);
      ctx.lineTo(BOARD_SIZE * CELL_SIZE, i * CELL_SIZE);
      ctx.stroke();
    }

    // 현재 턴의 데이터 가져오기
    if (logs.length > 0 && turn < logs.length) {
      const currentLog = logs[turn];
      const p1 = currentLog.p1.pos;
      const p2 = currentLog.p2.pos;
      const coin = currentLog.coin;

      // 코인 그리기 (노란색 원)
      if (coin[0] !== -1) {
        drawCircle(ctx, coin[0], coin[1], 'gold');
      }

      // P1 그리기 (파란색 사각형)
      drawRect(ctx, p1[0], p1[1], 'blue');

      // P2 그리기 (빨간색 사각형)
      drawRect(ctx, p2[0], p2[1], 'red');
    }
  }, [turn, logs]);

  // 2. 재생 로직 (Interval)
  useEffect(() => {
    let interval;
    if (isPlaying && turn < maxTurn - 1) {
      interval = setInterval(() => {
        setTurn((prev) => prev + 1);
      }, 500); // 0.5초마다 다음 턴
    } else {
      setIsPlaying(false);
    }
    return () => clearInterval(interval);
  }, [isPlaying, turn, maxTurn]);

  // 도형 그리기 헬퍼 함수
  const drawRect = (ctx, x, y, color) => {
    ctx.fillStyle = color;
    ctx.fillRect(x * CELL_SIZE + 5, y * CELL_SIZE + 5, CELL_SIZE - 10, CELL_SIZE - 10);
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
      <canvas 
        ref={canvasRef} 
        width={BOARD_SIZE * CELL_SIZE} 
        height={BOARD_SIZE * CELL_SIZE} 
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