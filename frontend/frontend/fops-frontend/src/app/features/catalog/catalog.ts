import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { ConfirmService } from '../../core/confirm.service';
import { ToastService } from '../../core/toast.service';
import { Item, User } from '../../core/models';

/**
 * Master data maintenance: edit and delete users and items.
 * Deletions the backend refuses (records in use) surface as error toasts through the HTTP interceptor.
 */
@Component({
  selector: 'app-catalog',
  imports: [FormsModule, RouterLink],
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
          <div class="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>Email</th><th></th></tr></thead>
              <tbody>
                @for (user of users(); track user.id) {
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
                  <tr><td colspan="3" class="empty">No users yet.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>

        <section class="panel items">
          <h2>Items</h2>
          <p class="muted hint">Stock is not editable here: it only changes through inventory movements.</p>
          <div class="table-wrap">
            <table>
              <thead><tr><th>Name</th><th>SKU</th><th class="num">Stock</th><th></th></tr></thead>
              <tbody>
                @for (item of items(); track item.id) {
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
                  <tr><td colspan="4" class="empty">No items yet.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </section>
  `,
  styles: `.hint { margin: -4px 0 4px; font-size: .88rem; }`
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
