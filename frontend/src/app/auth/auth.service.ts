import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';

export interface Me {
  visitorId: string | null;
  user: { name: string | null; email: string | null; picture: string | null } | null;
}

/**
 * Who is this browser. Login itself is a full-page round trip through Google
 * handled by the server (BFF): the SPA never sees OAuth tokens, only the
 * session cookie the server sets.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  /** null until /api/me answered. A signal because the header re-renders on it. */
  readonly me = signal<Me | null>(null);

  constructor(private http: HttpClient) {
    this.refresh();
  }

  login(): void {
    // A navigation, not an XHR: the browser must actually go to Google.
    window.location.href = '/oauth2/authorization/google';
  }

  logout(): void {
    // POST with the XSRF token, which Angular's HttpClient adds by itself from
    // the XSRF-TOKEN cookie. The visitor cookie stays: still the same browser.
    this.http.post('/logout', null).subscribe(() => this.refresh());
  }

  private refresh(): void {
    this.http.get<Me>('/api/me').subscribe(me => this.me.set(me));
  }
}
