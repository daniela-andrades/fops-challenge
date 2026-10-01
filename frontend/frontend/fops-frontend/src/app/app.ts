import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ConfirmDialog } from './shared/components/confirm-dialog';
import { ToastOutlet } from './shared/components/toast-outlet';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ToastOutlet, ConfirmDialog],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
}
