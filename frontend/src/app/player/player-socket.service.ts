import { Injectable, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { Command, SessionState } from './player.models';

/**
 * The client end of the shared session. One WebSocket to the server delivers the
 * authoritative state (broadcast to every client) and carries commands back. Auto-
 * reconnects with backoff so a dropped connection silently re-joins the session.
 */
@Injectable({ providedIn: 'root' })
export class PlayerSocketService {
  private ws?: WebSocket;
  private reconnectDelay = 500;

  /** Authoritative state pushed by the server. */
  readonly state$ = new Subject<SessionState>();
  /** Per-client errors (e.g. a yt-dlp failure on `add`). */
  readonly error$ = new Subject<string>();
  /** Whether the socket is currently open — drives the UI's connection dot. */
  readonly connected = signal(false);

  constructor() {
    this.connect();
  }

  send(command: Command): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(command));
    }
  }

  private connect(): void {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const ws = new WebSocket(`${proto}://${location.host}/ws/player`);
    this.ws = ws;

    ws.onopen = () => {
      this.connected.set(true);
      this.reconnectDelay = 500;
    };
    ws.onmessage = (event) => {
      const msg = JSON.parse(event.data);
      if (msg.error) this.error$.next(msg.error);
      else this.state$.next(msg as SessionState);
    };
    ws.onclose = () => {
      this.connected.set(false);
      setTimeout(() => this.connect(), this.reconnectDelay);
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, 5000);
    };
    ws.onerror = () => ws.close();
  }
}
