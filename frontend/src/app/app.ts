import { Component } from '@angular/core';
import { UserMenuComponent } from './auth/user-menu.component';
import { PlayerComponent } from './player/player.component';

@Component({
  selector: 'app-root',
  imports: [PlayerComponent, UserMenuComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
