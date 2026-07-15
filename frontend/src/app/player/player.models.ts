/** One resolved track — mirrors the backend's TrackInfo. */
export interface Track {
  id: string;
  title: string;
  duration: number; // seconds; 0 means live/unknown
  uploader: string;
  thumbnail?: string;
  webpageUrl: string;
}

/** Loop cycle, matching the Discord panel's 🔁 button: off → queue → track. */
export type LoopMode = 'off' | 'queue' | 'track';

/**
 * Authoritative session state broadcast by the server — mirrors backend SessionState.
 * Position is an anchor: `positionSec` at `anchorEpochMs`; `serverEpochMs` lets the
 * client estimate its clock offset. Every client extrapolates the same live position.
 */
export interface SessionState {
  tracks: Track[];
  index: number;
  playing: boolean;
  positionSec: number;
  anchorEpochMs: number;
  serverEpochMs: number;
  loop: LoopMode;
}

/** A command sent to the server over the WebSocket. */
export interface Command {
  type:
    | 'play'
    | 'pause'
    | 'stop'
    | 'seek'
    | 'next'
    | 'prev'
    | 'playAt'
    | 'cycleLoop'
    | 'shuffle'
    | 'remove'
    | 'move'
    | 'add';
  position?: number;
  index?: number;
  delta?: number;
  query?: string;
}

/** What the player is doing right now, for the panel's header + button state. */
export type PlaybackStatus = 'idle' | 'loading' | 'playing' | 'paused';
