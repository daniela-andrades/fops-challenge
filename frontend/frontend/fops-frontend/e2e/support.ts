import { APIRequestContext, expect, Page } from '@playwright/test';

/** Unique suffix so every run creates its own users, SKUs and orders against a shared backend. */
export const uniqueId = () => `${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;

export async function createUserAndItem(request: APIRequestContext, stock: number) {
  const id = uniqueId();
  const user = await (await request.post('/api/users', { data: { name: `E2E User ${id}`, email: `e2e-${id}@test.local` } })).json();
  const item = await (await request.post('/api/items', { data: { name: `E2E Item ${id}`, sku: `E2E-${id}`, stockOnHand: stock } })).json();
  return { user, item };
}

export async function expectToast(page: Page, text: string | RegExp) {
  await expect(page.locator('app-toast-outlet .toast').filter({ hasText: text }).last()).toBeVisible();
}
