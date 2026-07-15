import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Track } from './player.models';

/**
 * Talks to the Spring backend's yt-dlp proxy. Resolving is the only network call
 * the app makes explicitly; audio bytes are pulled by the native <audio> element
 * straight from `/api/stream/{id}`.
 */
@Injectable({ providedIn: 'root' })
export class PlayerService {
  constructor(private http: HttpClient) {}

  /** Resolve a YouTube link OR free-text search into one track's metadata. */
  resolve(query: string): Observable<Track> {
    return this.http.get<Track>('/api/resolve', { params: { q: query } });
  }

  /** Stream URL the <audio> element points at for a given video id. */
  streamUrl(id: string): string {
    return `/api/stream/${id}`;
  }
}
