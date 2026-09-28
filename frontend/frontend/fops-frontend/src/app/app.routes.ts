import { Routes } from '@angular/router';
import { DashboardPage } from './features/dashboard/dashboard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardPage, title: 'Dashboard · Fusion Operations' },
  {
    path: 'orders',
    loadComponent: () => import('./features/orders/order-list').then((m) => m.OrderListPage),
    title: 'Orders · Fusion Operations'
  },
  {
    path: 'orders/:id',
    loadComponent: () => import('./features/orders/order-detail').then((m) => m.OrderDetailPage),
    title: 'Order detail · Fusion Operations'
  },
  {
    path: 'inventory',
    loadComponent: () => import('./features/inventory/movement-list').then((m) => m.MovementListPage),
    title: 'Inventory · Fusion Operations'
  },
  {
    path: 'inventory/movements/:id',
    loadComponent: () => import('./features/inventory/movement-detail').then((m) => m.MovementDetailPage),
    title: 'Movement detail · Fusion Operations'
  },
  {
    path: 'catalog',
    loadComponent: () => import('./features/catalog/catalog').then((m) => m.CatalogPage),
    title: 'Catalog · Fusion Operations'
  },
  { path: '**', redirectTo: 'dashboard' }
];
