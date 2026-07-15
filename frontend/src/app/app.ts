import { Component } from '@angular/core';
import { PlayerComponent } from './player/player.component';

@Component({
  selector: 'app-root',
  imports: [PlayerComponent],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
