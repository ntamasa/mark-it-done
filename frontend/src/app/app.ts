import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  template: `
    <div class="app-backdrop" aria-hidden="true"></div>
    <router-outlet></router-outlet>
  `,
  styleUrl: './app.scss',
  standalone: true,
})
export class App {}
