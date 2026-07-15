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

/** What the player is doing right now, for the panel's header + button state. */
export type PlaybackStatus = 'idle' | 'loading' | 'playing' | 'paused';
