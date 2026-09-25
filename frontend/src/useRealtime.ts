import { useEffect, useRef } from 'react';

export type RealtimeEvent = {
  type: 'CONNECTED' | 'SESSION_CHANGED' | 'ATTENDANCE_CHANGED' | 'QR_CHANGED';
  courseId?: string;
  offeringId?: string;
  sessionId?: string;
};

export function useRealtime(apiUrl: string, token: string, receive: (event: RealtimeEvent) => void) {
  const receiver = useRef(receive);
  receiver.current = receive;
  useEffect(() => {
    let socket: WebSocket | null = null;
    let reconnect: ReturnType<typeof setTimeout>;
    let stopped = false;
    const connect = () => {
      if (stopped) return;
      const url = apiUrl.replace(/^http/, 'ws').replace(/\/$/, '') + '/ws/realtime';
      socket = new WebSocket(url);
      socket.onopen = () => socket?.send(JSON.stringify({ token }));
      socket.onmessage = message => {
        try {
          receiver.current(JSON.parse(message.data) as RealtimeEvent);
        } catch {
        }
      };
      socket.onclose = () => {
        if (!stopped) reconnect = setTimeout(connect, 1500);
      };
    };
    connect();
    return () => {
      stopped = true;
      clearTimeout(reconnect);
      socket?.close();
    };
  }, [apiUrl, token]);
}
