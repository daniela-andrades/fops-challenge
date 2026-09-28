import { expect, test } from '@playwright/test';
import { createUserAndItem, expectToast, uniqueId } from './support';

test.describe('Order fulfillment', () => {
  test('creates an order from the dashboard, completes it with incoming stock and traces it both ways', async ({ page }) => {
    const id = uniqueId();
    await page.goto('/dashboard');

    await page.getByPlaceholder('Name', { exact: true }).fill(`Ana ${id}`);
    await page.getByPlaceholder('Email', { exact: true }).fill(`ana-${id}@test.local`);
    await page.getByRole('button', { name: 'Save user' }).click();
    await expectToast(page, `User Ana ${id} created`);

    await page.getByPlaceholder('Item name').fill(`Laptop ${id}`);
    await page.getByPlaceholder('SKU', { exact: true }).fill(`lap-${id}`);
    await page.getByPlaceholder('Initial stock').fill('4');
    await page.getByRole('button', { name: 'Save item' }).click();
    await expectToast(page, `Item LAP-${id.toUpperCase()} created with 4 units`);

    await page.locator('select[name=orderUserId]').selectOption({ label: `Ana ${id} · ana-${id}@test.local` });
    await page.locator('select[name=orderItemId]').selectOption({ label: `Laptop ${id} (4 in stock)` });
    await page.getByPlaceholder('Requested quantity').fill('10');
    await page.getByRole('button', { name: 'Create order' }).click();
    await expectToast(page, /Order #\d+ created · 40% fulfilled \(partially fulfilled\)/);

    await page.locator('select[name=inventoryItemId]').selectOption({ label: `Laptop ${id} · LAP-${id.toUpperCase()}` });
    await page.getByPlaceholder('Quantity', { exact: true }).fill('8');
    await page.getByPlaceholder('Reason (optional)').fill('Supplier A');
    await page.getByRole('button', { name: 'Register stock' }).click();
    await expectToast(page, `8 units of LAP-${id.toUpperCase()} received · allocated to 1 order(s), 1 completed`);

    await page.getByRole('link', { name: 'Orders', exact: true }).click();
    await page.locator('tbody tr').filter({ hasText: `Laptop ${id}` }).first().click();

    await expect(page.getByRole('heading', { level: 1 })).toHaveText(`LAP-${id.toUpperCase()} × 10`);
    await expect(page.locator('app-status-badge').first()).toHaveText('Completed');
    const history = page.locator('section.panel').filter({ hasText: 'Fulfillment history' });
    await expect(history.locator('tbody tr')).toHaveCount(2);
    await expect(history.locator('tbody tr').first()).toContainText('Stock on hand');
    await expect(history.locator('tbody tr').last()).toContainText('Completed the order');
    await expect(page.locator('section.panel').filter({ hasText: 'Completion email' })).toContainText(/Sent|Queued|Retrying/);

    await history.locator('tbody tr').last().getByRole('link', { name: /^IN #/ }).click();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Incoming stock');
    await expect(page.getByText('6 of 8 units went to orders waiting on arrival · 2 added to stock on hand.')).toBeVisible();
  });

  test('filters orders by status', async ({ page, request }) => {
    const { user, item } = await createUserAndItem(request, 0);
    await request.post('/api/orders', { data: { userId: user.id, itemId: item.id, requestedQuantity: 3 } });

    await page.goto('/orders');
    await page.locator('select[name=itemId]').selectOption({ label: item.name });
    await page.locator('select[name=status]').selectOption({ label: 'Pending' });
    await expect(page.locator('tbody tr')).toHaveCount(1);

    await page.locator('select[name=status]').selectOption({ label: 'Completed' });
    await expect(page.getByText('No orders match these filters.')).toBeVisible();
  });
});

test.describe('Validation feedback', () => {
  test('shows the API error when the SKU already exists', async ({ page, request }) => {
    const { item } = await createUserAndItem(request, 0);

    await page.goto('/dashboard');
    await page.getByPlaceholder('Item name').fill('Duplicate');
    await page.getByPlaceholder('SKU', { exact: true }).fill(item.sku.toLowerCase());
    await page.getByRole('button', { name: 'Save item' }).click();

    await expectToast(page, `An item with SKU already exists: ${item.sku}`);
  });

  test('deep links to an unknown order show a not found state', async ({ page }) => {
    await page.goto('/orders/999999');

    await expect(page.getByText('Order not found.')).toBeVisible();
    await expectToast(page, 'Order 999999 not found');
  });
});
