import { Component, effect, ElementRef, inject, OnDestroy, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LoopMode } from './player.models';
import { PlayerStore } from './player.store';

/**
 * The shared jukebox panel. The server owns the session; this component renders it
 * and keeps the local <audio> element in lockstep with the authoritative state —
 * loading the current track, seeking to the extrapolated live position, and
 * mirroring play/pause. Because browsers block autoplay without a gesture, a tab
 * that should be playing but was refused shows a "join" prompt (one click).
 */
@Component({
  selector: 'app-player',
  imports: [FormsModule],
  templateUrl: './player.component.html',
  styleUrl: './player.component.css',
})
export class PlayerComponent implements OnDestroy {
  readonly store = inject(PlayerStore);
  private readonly audio = viewChild.required<ElementRef<HTMLAudioElement>>('audio');

  // Start muted: browsers allow muted autoplay, so playback runs and stays in sync
  // from the first second — a click just unmutes (a gesture that permits sound).
  readonly muted = signal(true);
  query = '';

  // In-effect tracking var — plain field, per the "signals sparingly" rule.
  private loadedId: string | null = null;
  private readonly ticker: ReturnType<typeof setInterval>;
  private static readonly SYNC_THRESHOLD_SEC = 1.5;

  constructor() {
    // Drive the displayed position from the shared anchor (not the local <audio>),
    // so time advances identically in every tab regardless of local buffering/mute.
    this.ticker = setInterval(() => {
      this.store.setElapsed(this.store.targetPosition());
    }, 250);

    // Keep <audio> in sync with the authoritative session on every server update.
    effect(() => {
      const track = this.store.current();
      const playing = this.store.playing();
      this.store.anchorEpochMs(); // dep: re-sync on a new anchor (seek / track / resume)
      this.store.positionSec(); // dep
      const el = this.audio().nativeElement;
      const id = track?.id ?? null;

      if (id !== this.loadedId) {
        this.loadedId = id;
        if (id) {
          el.src = `/api/stream/${id}`;
          el.load();
        } else {
          el.removeAttribute('src');
          el.load();
        }
      }
      if (!id) return;

      const target = this.store.targetPosition();
      if (Math.abs(el.currentTime - target) > PlayerComponent.SYNC_THRESHOLD_SEC) {
        try {
          el.currentTime = target;
        } catch {
          // seek before metadata is ready — the next sync will catch it
        }
      }

      el.muted = this.muted(); // dep: re-applies when the user unmutes
      if (playing) el.play().catch(() => {});
      else el.pause();
    });

    // Local volume.
    effect(() => {
      this.audio().nativeElement.volume = this.store.volume();
    });
  }

  submit(): void {
    const q = this.query;
    this.query = '';
    this.store.add(q);
  }

  /** One click enables sound for this tab (a gesture that lifts the autoplay block). */
  unmute(): void {
    const el = this.audio().nativeElement;
    el.muted = false;
    this.muted.set(false);
    el.play().catch(() => {});
  }

  /**
   * Any interaction in this tab is a user gesture, so sound is allowed — drop the
   * mute. This means playback the user starts here is never silent; only a passive
   * tab (playing pushed by the server, no local click) stays muted until clicked.
   */
  onInteract(): void {
    if (this.muted()) this.muted.set(false);
  }

  ngOnDestroy(): void {
    clearInterval(this.ticker);
  }

  // ---- <audio> events → store ----
  onError(): void {
    if (this.store.current()) this.store.setError('Playback failed — check the backend log.');
  }

  // ---- transport ----
  seek(event: Event): void {
    this.store.seek(Number((event.target as HTMLInputElement).value));
  }

  // ---- display helpers ----
  clock(sec: number): string {
    const s = Math.max(0, Math.floor(sec || 0));
    return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
  }
  loopLabel(mode: LoopMode): string {
    return mode === 'track' ? '🔂 track' : mode === 'queue' ? '🔁 queue' : '➡️ off';
  }
}
