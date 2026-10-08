import { Component } from '@angular/core';
import { AuthService } from './auth.service';

/** Top-right corner: "Sign in with Google", or the signed-in user and sign out. */
@Component({
  selector: 'app-user-menu',
  templateUrl: './user-menu.component.html',
  styleUrl: './user-menu.component.css',
})
export class UserMenuComponent {
  constructor(protected auth: AuthService) {}
}
