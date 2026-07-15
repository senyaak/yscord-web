import { Component, effect, ElementRef, inject, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { LoopMode } from './player.models';
import { PlayerStore } from './player.store';

/**
 * The web reincarnation of the bot's Discord panel (`src/panel.js`): a "now
 * playing" block with a seekable progress bar, Up next / Volume / Loop fields,
 * the same control rows, and a full queue with per-track manage actions.
 *
 * The component owns the DOM side of playback — a single hidden <audio> element —
 * and keeps it in lockstep with the DOM-free PlayerStore via three small effects.
 */
@Component({
  selector: 'app-player',
  imports: [FormsModule],
  templateUrl: './player.component.html',
  styleUrl: './player.component.css',
})
export class PlayerComponent {
  // signalStore() returns a value whose type can't serve as a constructor DI token,
  // so this is a legitimate inject() case (no usable constructor param form exists).
  readonly store = inject(PlayerStore);
  private readonly audio = viewChild.required<ElementRef<HTMLAudioElement>>('audio');

  query = '';
  // In-effect tracking var — a plain field, not a signal, per the "signals sparingly" rule.
  private loadedId: string | null = null;

  constructor() {
    // 1) Point <audio> at the current track's stream whenever the track changes.
    effect(() => {
      const id = this.store.current()?.id ?? null;
      const el = this.audio().nativeElement;
      if (id === this.loadedId) return;
      this.loadedId = id;
      if (id) {
        el.src = `/api/stream/${id}`;
        el.load();
      } else {
        el.removeAttribute('src');
        el.load();
      }
    });

    // 2) Reflect play/pause intent onto the element.
    effect(() => {
      const playing = this.store.playing();
      const el = this.audio().nativeElement;
      if (!this.store.current()) return;
      if (playing) el.play().catch(() => {});
      else el.pause();
    });

    // 3) Reflect volume.
    effect(() => {
      this.audio().nativeElement.volume = this.store.volume();
    });
  }

  // ---- input box ----
  submit(): void {
    const q = this.query;
    this.query = '';
    this.store.add(q);
  }

  // ---- <audio> events → store ----
  onTime(): void {
    this.store.setElapsed(this.audio().nativeElement.currentTime);
  }
  onEnded(): void {
    const el = this.audio().nativeElement;
    if (this.store.loop() === 'track') {
      el.currentTime = 0;
      el.play().catch(() => {});
      return;
    }
    this.store.next();
  }
  onError(): void {
    if (this.store.current()) this.store.setError('Playback failed — check the backend log.');
  }

  // ---- transport ----
  seek(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.audio().nativeElement.currentTime = value;
    this.store.setElapsed(value);
  }
  prev(): void {
    // Standard behaviour: restart if we're >3s in or there's nothing before us.
    if (this.store.elapsed() > 3 || !this.store.hasPrev()) {
      this.audio().nativeElement.currentTime = 0;
      this.store.setElapsed(0);
      return;
    }
    this.store.prev();
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
