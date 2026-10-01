import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ConfirmService } from '../../core/confirm.service';
import { ToastService } from '../../core/toast.service';
import { Item, User } from '../../core/models';
import { Paginator } from '../../shared/components/paginator';
import { DEFAULT_PAGE_SIZE, matchesSearch, pageOf } from '../../shared/pagination';

/**
 * Master data maintenance: edit and delete users and items.
 * Deletions the backend refuses (records in use) surface as error toasts through the HTTP interceptor.
 */
@Component({
  selector: 'app-catalog',
  imports: [FormsModule, RouterLink, Paginator],
  template: `
    <section class="page">
      <header class="page-header">
        <div>
          <p class="eyebrow">Master data</p>
          <h1>Catalog</h1>
        </div>
        <span class="muted">New users and items are created from the <a routerLink="/dashboard">dashboard</a>.</span>
      </header>

      <div class="grid two">
        <section class="panel users">
          <h2>Users</h2>
          <input type="search" class="search" [ngModel]="userSearch()" (ngModelChange)="userSearch.set($event); userPage.set(1)"
                 name="userSearch" placeholder="Search by name or email" aria-label="Search users" />
          <div class="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Email</th><th></th></tr></thead>
              <tbody>
                @for (user of pagedUsers(); track user.id) {
                  @if (editingUserId() === user.id) {
                    <tr class="editing">
                      <td><input [(ngModel)]="userDraft.name" name="userName" aria-label="Name" /></td>
                      <td><input type="email" [(ngModel)]="userDraft.email" name="userEmail" aria-label="Email" /></td>
                      <td class="row-actions">
                        <button type="button" class="inline" (click)="saveUser(user.id)" [disabled]="busy()">Save</button>
                        <button type="button" class="inline secondary" (click)="editingUserId.set(null)">Cancel</button>
                      </td>
                    </tr>
                  } @else {
                    <tr>
                      <td>{{ user.name }}</td>
                      <td>{{ user.email }}</td>
                      <td class="row-actions">
                        <button type="button" class="inline secondary" (click)="editUser(user)">Edit</button>
                        <button type="button" class="inline danger-soft" (click)="deleteUser(user)" [disabled]="busy()">Delete</button>
                      </td>
                    </tr>
                  }
                } @empty {
                  <tr><td colspan="3" class="empty">{{ userSearch() ? 'No users match this search.' : 'No users yet.' }}</td></tr>
                }
              </tbody>
            </table>
          </div>
          <app-paginator [total]="filteredUsers().length" [(page)]="userPage" [(pageSize)]="userPageSize" />
        </section>

        <section class="panel items">
          <h2>Items</h2>
          <p class="muted hint">Stock is not editable here: it only changes through inventory movements.</p>
          <input type="search" class="search" [ngModel]="itemSearch()" (ngModelChange)="itemSearch.set($event); itemPage.set(1)"
                 name="itemSearch" placeholder="Search by name or SKU" aria-label="Search items" />
          <div class="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>SKU</th><th class="num">Stock</th><th></th></tr></thead>
              <tbody>
                @for (item of pagedItems(); track item.id) {
                  @if (editingItemId() === item.id) {
                    <tr class="editing">
                      <td><input [(ngModel)]="itemDraft.name" name="itemName" aria-label="Item name" /></td>
                      <td><input [(ngModel)]="itemDraft.sku" name="itemSku" aria-label="SKU" /></td>
                      <td class="num">{{ item.stockOnHand }}</td>
                      <td class="row-actions">
                        <button type="button" class="inline" (click)="saveItem(item.id)" [disabled]="busy()">Save</button>
                        <button type="button" class="inline secondary" (click)="editingItemId.set(null)">Cancel</button>
                      </td>
                    </tr>
                  } @else {
                    <tr>
                      <td>{{ item.name }}</td>
                      <td>{{ item.sku }}</td>
                      <td class="num">{{ item.stockOnHand }}</td>
                      <td class="row-actions">
                        <button type="button" class="inline secondary" (click)="editItem(item)">Edit</button>
                        <button type="button" class="inline danger-soft" (click)="deleteItem(item)" [disabled]="busy()">Delete</button>
                      </td>
                    </tr>
                  }
                } @empty {
                  <tr><td colspan="4" class="empty">{{ itemSearch() ? 'No items match this search.' : 'No items yet.' }}</td></tr>
                }
              </tbody>
            </table>
          </div>
          <app-paginator [total]="filteredItems().length" [(page)]="itemPage" [(pageSize)]="itemPageSize" />
        </section>
      </div>
    </section>
  `,
  styles: `
    .hint { margin: -4px 0 4px; font-size: .88rem; }
    .search { margin-bottom: 4px; }
  `
})
export class CatalogPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly confirm = inject(ConfirmService);
  private readonly toasts = inject(ToastService);

  readonly users = signal<User[]>([]);
  readonly items = signal<Item[]>([]);
  readonly busy = signal(false);
  readonly editingUserId = signal<number | null>(null);
  readonly editingItemId = signal<number | null>(null);

  readonly userSearch = signal('');
  readonly userPage = signal(1);
  readonly userPageSize = signal(DEFAULT_PAGE_SIZE);
  readonly filteredUsers = computed(() => this.users().filter((user) => matchesSearch(this.userSearch(), user.name, user.email)));
  readonly pagedUsers = computed(() => pageOf(this.filteredUsers(), this.userPage(), this.userPageSize()));

  readonly itemSearch = signal('');
  readonly itemPage = signal(1);
  readonly itemPageSize = signal(DEFAULT_PAGE_SIZE);
  readonly filteredItems = computed(() => this.items().filter((item) => matchesSearch(this.itemSearch(), item.name, item.sku)));
  readonly pagedItems = computed(() => pageOf(this.filteredItems(), this.itemPage(), this.itemPageSize()));

  userDraft = { name: '', email: '' };
  itemDraft = { name: '', sku: '' };

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.getUsers().subscribe((users) => this.users.set(users));
    this.api.getItems().subscribe((items) => this.items.set(items));
  }

  editUser(user: User): void {
    this.userDraft = { name: user.name, email: user.email };
    this.editingUserId.set(user.id);
  }

  saveUser(id: number): void {
    this.run(this.api.updateUser(id, this.userDraft), (user) => {
      this.editingUserId.set(null);
      this.toasts.success(`User ${user.name} updated`);
    });
  }

  async deleteUser(user: User): Promise<void> {
    const confirmed = await this.confirm.confirm({
      title: `Delete ${user.name}?`,
      message: 'Users who have placed orders cannot be deleted, because their orders and emails depend on them.',
      confirmLabel: 'Delete user',
      danger: true
    });
    if (confirmed) {
      this.run(this.api.deleteUser(user.id), () => this.toasts.success(`User ${user.name} deleted`));
    }
  }

  editItem(item: Item): void {
    this.itemDraft = { name: item.name, sku: item.sku };
    this.editingItemId.set(item.id);
  }

  saveItem(id: number): void {
    this.run(this.api.updateItem(id, this.itemDraft), (item) => {
      this.editingItemId.set(null);
      this.toasts.success(`Item ${item.sku} updated`);
    });
  }

  async deleteItem(item: Item): Promise<void> {
    const confirmed = await this.confirm.confirm({
      title: `Delete ${item.name} (${item.sku})?`,
      message: 'Items with orders or inventory movements cannot be deleted, so their history is never lost.',
      confirmLabel: 'Delete item',
      danger: true
    });
    if (confirmed) {
      this.run(this.api.deleteItem(item.id), () => this.toasts.success(`Item ${item.sku} deleted`));
    }
  }

  private run<T>(request: Observable<T>, onSuccess: (result: T) => void): void {
    this.busy.set(true);
    request.subscribe({
      next: (result) => {
        onSuccess(result);
        this.busy.set(false);
        this.load();
      },
      error: () => this.busy.set(false)
    });
  }
}
