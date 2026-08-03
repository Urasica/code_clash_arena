import httpClient from '../../shared/api/httpClient';

export const startLandGrabMatch = () => httpClient.post('/api/match/land-grab/start', {});

export const compileLandGrabCode = (request) => (
  httpClient.post('/api/match/land-grab/compile', request)
);

export const runLandGrabMatch = (request) => (
  httpClient.post('/api/match/land-grab/run', request)
);
