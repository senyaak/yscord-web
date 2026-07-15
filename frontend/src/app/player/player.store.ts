import { computed, inject } from '@angular/core';
import { patchState, signalStore, withComputed, withHooks, withMethods, withState } from '@ngrx/signals';
import { LoopMode, PlaybackStatus, SessionState, Track } from './player.models';
import { PlayerSocketService } from './player-socket.service';

interface PlayerState {
  // ---- mirrored from the server (authoritative) ----
  tracks: Track[];
  index: number;
  playing: boolean;
  loop: LoopMode;
  positionSec: number; // current track position at anchorEpochMs
  anchorEpochMs: number;
  clockOffsetMs: number; // serverEpoch - clientEpoch, to align clocks

  // ---- local, per-client ----
  volume: number; // not shared — each listener's own level
  buffering: boolean;
  elapsed: number; // the <audio> element's currentTime, for display
  pending: boolean; // an `add` is in flight
  error: string | null;
}

function initialVolume(): number {
  const stored = Number(localStorage.getItem('yscord.volume'));
  return Number.isFinite(stored) && stored > 0 ? stored : 0.5;
}

const initialState: PlayerState = {
  tracks: [],
  index: -1,
  playing: false,
  loop: 'off',
  positionSec: 0,
  anchorEpochMs: 0,
  clockOffsetMs: 0,
  volume: initialVolume(),
  buffering: false,
  elapsed: 0,
  pending: false,
  error: null,
};

/**
 * A thin mirror of the server's shared session. State arrives over the WebSocket
 * and is applied here; user actions send commands and wait for the broadcast —
 * the server is the single source of truth, so every client stays in sync. Only
 * volume is local. `targetPosition()` extrapolates the live position from the
 * server's anchor so the <audio> element can be seeked into sync.
 */
export const PlayerStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),

  withComputed((store, socket = inject(PlayerSocketService)) => ({
    current: computed<Track | null>(() => store.tracks()[store.index()] ?? null),
    hasQueue: computed(() => store.tracks().length > 0),
    hasPrev: computed(() => store.index() > 0),
    hasNext: computed(() => store.index() < store.tracks().length - 1),
    upcoming: computed(() => Math.max(0, store.tracks().length - 1 - store.index())),
    volumePercent: computed(() => Math.round(store.volume() * 100)),
    connected: computed(() => socket.connected()),
    status: computed<PlaybackStatus>(() => {
      if (store.index() < 0) return 'idle';
      if (store.buffering()) return 'loading';
      return store.playing() ? 'playing' : 'paused';
    }),
  })),

  withMethods((store, socket = inject(PlayerSocketService)) => ({
    /** Where the current track should be right now, extrapolated from the anchor. */
    targetPosition(): number {
      if (!store.playing()) return store.positionSec();
      const serverNow = Date.now() + store.clockOffsetMs();
      return store.positionSec() + (serverNow - store.anchorEpochMs()) / 1000;
    },

    // ---- commands (server is authoritative; we just send) ----
    add(query: string): void {
      const q = query.trim();
      if (!q) return;
      patchState(store, { pending: true, error: null });
      socket.send({ type: 'add', query: q });
    },
    togglePlay(): void {
      if (store.index() < 0) return;
      socket.send({ type: store.playing() ? 'pause' : 'play' });
    },
    next(): void {
      socket.send({ type: 'next' });
    },
    prev(): void {
      socket.send({ type: 'prev' });
    },
    stop(): void {
      socket.send({ type: 'stop' });
    },
    playAt(index: number): void {
      socket.send({ type: 'playAt', index });
    },
    seek(position: number): void {
      socket.send({ type: 'seek', position });
    },
    cycleLoop(): void {
      socket.send({ type: 'cycleLoop' });
    },
    shuffle(): void {
      socket.send({ type: 'shuffle' });
    },
    removeAt(index: number): void {
      socket.send({ type: 'remove', index });
    },
    move(index: number, delta: number): void {
      socket.send({ type: 'move', index, delta });
    },

    // ---- local volume (persisted per browser, never shared) ----
    setVolume(v: number): void {
      const volume = Math.max(0, Math.min(1, v));
      localStorage.setItem('yscord.volume', String(volume));
      patchState(store, { volume });
    },
    volumeDown(): void {
      this.setVolume(store.volume() - 0.1);
    },
    volumeUp(): void {
      this.setVolume(store.volume() + 0.1);
    },

    // ---- local view state, driven by the <audio> element ----
    setElapsed(sec: number): void {
      patchState(store, { elapsed: sec });
    },
    setBuffering(b: boolean): void {
      patchState(store, { buffering: b });
    },
    setError(msg: string): void {
      patchState(store, { error: msg, pending: false });
    },
    clearError(): void {
      patchState(store, { error: null });
    },

    /** Apply an authoritative snapshot from the server. */
    apply(state: SessionState): void {
      patchState(store, {
        tracks: state.tracks,
        index: state.index,
        playing: state.playing,
        loop: state.loop,
        positionSec: state.positionSec,
        anchorEpochMs: state.anchorEpochMs,
        clockOffsetMs: state.serverEpochMs - Date.now(),
        pending: false,
      });
    },
  })),

  withHooks({
    onInit(store, socket = inject(PlayerSocketService)) {
      socket.state$.subscribe((state) => store.apply(state));
      socket.error$.subscribe((msg) => store.setError(msg));
    },
  }),
);
