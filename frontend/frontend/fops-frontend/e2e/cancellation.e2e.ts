import { expect, test } from '@playwright/test';
import { createUserAndItem, expectToast } from './support';

test.describe('Order cancellation', () => {
  test('cancelling a partial order returns its stock, which goes to the oldest waiting order', async ({ page, request }) => {
    const { user, item } = await createUserAndItem(request, 5);
    const partial = await (await request.post('/api/orders', { data: { userId: user.id, itemId: item.id, requestedQuantity: 8 } })).json();
    const waiting = await (await request.post('/api/orders', { data: { userId: user.id, itemId: item.id, requestedQuantity: 3 } })).json();
    expect(partial.status).toBe('PARTIALLY_FULFILLED');
    expect(waiting.status).toBe('PENDING');

    await page.goto(`/orders/${partial.id}`);
    await page.getByRole('button', { name: 'Cancel order' }).click();
    const dialog = page.getByRole('alertdialog');
    await expect(dialog).toContainText('Cancelling will return 5 units to stock. They may be automatically allocated to other pending orders.');
    await dialog.getByRole('button', { name: 'Cancel order' }).click();

    await expectToast(page, `Order #${partial.id} cancelled · 5 units returned to stock`);
    await expect(page.locator('app-status-badge').first()).toHaveText('Cancelled');
    await expect(page.getByRole('button', { name: 'Cancel order' })).toHaveCount(0);

    const waitingAfter = await (await request.get(`/api/orders/${waiting.id}`)).json();
    expect(waitingAfter.status).toBe('COMPLETED');
    expect((await (await request.get(`/api/items/${item.id}`)).json()).stockOnHand).toBe(2);

    await page.goto('/orders');
    await page.locator('select[name=itemId]').selectOption({ label: item.name });
    await expect(page.locator('tbody tr').filter({ hasText: `#${partial.id}` })).toContainText('Cancelled');
  });

  test('a pending order with nothing allocated says no stock is returned', async ({ page, request }) => {
    const { user, item } = await createUserAndItem(request, 0);
    const pending = await (await request.post('/api/orders', { data: { userId: user.id, itemId: item.id, requestedQuantity: 2 } })).json();

    await page.goto(`/orders/${pending.id}`);
    await page.getByRole('button', { name: 'Cancel order' }).click();
    await expect(page.getByRole('alertdialog')).toContainText('Nothing has been allocated to this order yet, so no stock is returned.');
    await page.getByRole('alertdialog').getByRole('button', { name: 'Cancel order' }).click();

    await expectToast(page, `Order #${pending.id} cancelled`);
    await expect(page.locator('.cancelled-note')).toContainText('so no stock was returned');
  });

  test('completed orders cannot be cancelled', async ({ page, request }) => {
    const { user, item } = await createUserAndItem(request, 5);
    const completed = await (await request.post('/api/orders', { data: { userId: user.id, itemId: item.id, requestedQuantity: 2 } })).json();

    await page.goto(`/orders/${completed.id}`);
    await expect(page.locator('app-status-badge').first()).toHaveText('Completed');
    await expect(page.getByRole('button', { name: 'Cancel order' })).toHaveCount(0);

    const response = await request.post(`/api/orders/${completed.id}/cancel`);
    expect(response.status()).toBe(409);
  });
});
