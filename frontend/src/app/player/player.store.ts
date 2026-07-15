import { computed, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';
import { firstValueFrom } from 'rxjs';
import { LoopMode, PlaybackStatus, Track } from './player.models';
import { PlayerService } from './player.service';

interface PlayerState {
  tracks: Track[];
  index: number; // -1 when nothing is loaded
  playing: boolean; // user intent: should audio be running
  buffering: boolean; // audio is waiting on the network
  volume: number; // 0..1
  loop: LoopMode;
  elapsed: number; // seconds into the current track
  resolving: boolean; // a /resolve request is in flight
  error: string | null;
}

const initialState: PlayerState = {
  tracks: [],
  index: -1,
  playing: false,
  buffering: false,
  volume: 0.5,
  loop: 'off',
  elapsed: 0,
  resolving: false,
  error: null,
};

const LOOP_CYCLE: Record<LoopMode, LoopMode> = { off: 'queue', queue: 'track', track: 'off' };

function errorText(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    // Backend returns RFC-9457 ProblemDetail with a `detail` field.
    return e.error?.detail ?? e.message;
  }
  return e instanceof Error ? e.message : 'Unknown error';
}

/**
 * The whole player lives here: queue, cursor, transport state, volume and loop
 * are all signals. This is exactly the case where signals earn their keep — every
 * field drives the panel template. The store is intentionally DOM-free: it holds
 * intent and state, while the component syncs the native <audio> element to it.
 */
export const PlayerStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),

  withComputed((store) => ({
    current: computed<Track | null>(() => store.tracks()[store.index()] ?? null),
    hasQueue: computed(() => store.tracks().length > 0),
    hasPrev: computed(() => store.index() > 0),
    hasNext: computed(() => store.index() < store.tracks().length - 1),
    upcoming: computed(() => Math.max(0, store.tracks().length - 1 - store.index())),
    volumePercent: computed(() => Math.round(store.volume() * 100)),
    status: computed<PlaybackStatus>(() => {
      if (store.index() < 0) return 'idle';
      if (store.buffering()) return 'loading';
      return store.playing() ? 'playing' : 'paused';
    }),
  })),

  withMethods((store, api = inject(PlayerService)) => ({
    /** Resolve a link/search and append it; auto-start if the queue was empty. */
    async add(query: string): Promise<void> {
      const q = query.trim();
      if (!q) return;
      patchState(store, { resolving: true, error: null });
      try {
        const track = await firstValueFrom(api.resolve(q));
        const wasEmpty = store.tracks().length === 0;
        patchState(store, (s) => ({ tracks: [...s.tracks, track] }));
        if (wasEmpty) patchState(store, { index: 0, elapsed: 0, playing: true });
      } catch (e) {
        patchState(store, { error: errorText(e) });
      } finally {
        patchState(store, { resolving: false });
      }
    },

    playAt(i: number): void {
      if (i < 0 || i >= store.tracks().length) return;
      patchState(store, { index: i, elapsed: 0, playing: true });
    },

    togglePlay(): void {
      if (store.index() < 0) return;
      patchState(store, { playing: !store.playing() });
    },

    /** Advance for skip AND natural end-of-track; wraps/stops per loop mode. */
    next(): void {
      const { index, tracks, loop } = { index: store.index(), tracks: store.tracks(), loop: store.loop() };
      if (index < tracks.length - 1) patchState(store, { index: index + 1, elapsed: 0, playing: true });
      else if (loop === 'queue') patchState(store, { index: 0, elapsed: 0, playing: true });
      else patchState(store, { playing: false, elapsed: 0 });
    },

    prev(): void {
      if (store.index() > 0) patchState(store, { index: store.index() - 1, elapsed: 0, playing: true });
      else patchState(store, { elapsed: 0 });
    },

    stop(): void {
      patchState(store, { playing: false, elapsed: 0, index: -1 });
    },

    cycleLoop(): void {
      patchState(store, { loop: LOOP_CYCLE[store.loop()] });
    },

    shuffle(): void {
      const tracks = [...store.tracks()];
      const current = store.current();
      // Fisher–Yates on everything except the currently playing track.
      for (let i = tracks.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [tracks[i], tracks[j]] = [tracks[j], tracks[i]];
      }
      const newIndex = current ? tracks.findIndex((t) => t === current) : -1;
      patchState(store, { tracks, index: newIndex });
    },

    setVolume(v: number): void {
      patchState(store, { volume: Math.max(0, Math.min(1, v)) });
    },
    volumeDown(): void {
      patchState(store, { volume: Math.max(0, store.volume() - 0.1) });
    },
    volumeUp(): void {
      patchState(store, { volume: Math.min(1, store.volume() + 0.1) });
    },

    // ---- queue manager (mirrors the Discord "🗂️ Queue" actions) ----
    removeAt(i: number): void {
      const tracks = store.tracks().filter((_, idx) => idx !== i);
      let index = store.index();
      if (i < index) index -= 1;
      else if (i === index) index = tracks.length ? Math.min(index, tracks.length - 1) : -1;
      patchState(store, { tracks, index, elapsed: i === store.index() ? 0 : store.elapsed() });
    },
    move(i: number, delta: number): void {
      const j = i + delta;
      const tracks = [...store.tracks()];
      if (j < 0 || j >= tracks.length) return;
      [tracks[i], tracks[j]] = [tracks[j], tracks[i]];
      let index = store.index();
      if (index === i) index = j;
      else if (index === j) index = i;
      patchState(store, { tracks, index });
    },

    // ---- synced from the <audio> element by the component ----
    setElapsed(sec: number): void {
      patchState(store, { elapsed: sec });
    },
    setBuffering(b: boolean): void {
      patchState(store, { buffering: b });
    },
    setError(msg: string): void {
      patchState(store, { error: msg });
    },
    clearError(): void {
      patchState(store, { error: null });
    },
  })),
);
