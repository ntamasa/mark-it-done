import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';

@Component({
  imports: [RouterOutlet, ToastModule, ConfirmDialogModule],
  selector: 'app-root',
  template: `
    <div class="app-backdrop" aria-hidden="true"></div>
    <router-outlet></router-outlet>
    <p-toast position="bottom-right" styleClass="app-toast-container"></p-toast>
    <p-confirmdialog styleClass="app-confirm-dialog"></p-confirmdialog>
  `,
  styleUrl: './app.scss',
  standalone: true,
})
export class App {}
