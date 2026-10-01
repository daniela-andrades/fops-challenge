import { expect, Page, test } from '@playwright/test';
import { createUserAndItem, expectToast, uniqueId } from './support';

const searchUsers = (page: Page, text: string) => page.getByPlaceholder('Search by name or email').fill(text);
const searchItems = (page: Page, text: string) => page.getByPlaceholder('Search by name or SKU').fill(text);

test.describe('Catalog maintenance', () => {
  test('renames a user and an item inline', async ({ page, request }) => {
    const { user, item } = await createUserAndItem(request, 3);
    const id = uniqueId();

    await page.goto('/catalog');
    await searchUsers(page, user.email);
    await searchItems(page, item.sku);
    const userRow = page.locator('.users tbody tr').filter({ hasText: user.email });
    await userRow.getByRole('button', { name: 'Edit' }).click();
    await page.locator('.users tr.editing').getByLabel('Name').fill(`Renamed ${id}`);
    await page.locator('.users tr.editing').getByRole('button', { name: 'Save' }).click();
    await expectToast(page, `User Renamed ${id} updated`);

    const itemRow = page.locator('.items tbody tr').filter({ hasText: item.sku });
    await itemRow.getByRole('button', { name: 'Edit' }).click();
    await page.locator('.items tr.editing').getByLabel('SKU').fill(`ren-${id}`);
    await page.locator('.items tr.editing').getByRole('button', { name: 'Save' }).click();
    await expectToast(page, `Item REN-${id.toUpperCase()} updated`);
    await searchItems(page, `REN-${id}`);
    await expect(page.locator('.items tbody tr').filter({ hasText: `REN-${id.toUpperCase()}` }).locator('.num')).toHaveText('3');
  });

  test('refuses to delete a user with orders but deletes one without', async ({ page, request }) => {
    const { user, item } = await createUserAndItem(request, 0);
    await request.post('/api/orders', { data: { userId: user.id, itemId: item.id, requestedQuantity: 1 } });
    const idle = await (await request.post('/api/users', { data: { name: `Idle ${uniqueId()}`, email: `idle-${uniqueId()}@test.local` } })).json();

    await page.goto('/catalog');
    await searchUsers(page, user.email);
    await page.locator('.users tbody tr').filter({ hasText: user.email }).getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Delete user' }).click();
    await expectToast(page, `User ${user.id} has orders and cannot be deleted`);

    await searchUsers(page, idle.email);
    await page.locator('.users tbody tr').filter({ hasText: idle.email }).getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Delete user' }).click();
    await expectToast(page, `User ${idle.name} deleted`);
    await expect(page.locator('.users tbody tr').filter({ hasText: idle.email })).toHaveCount(0);
  });

  test('cancelling the confirmation keeps the item', async ({ page, request }) => {
    const { item } = await createUserAndItem(request, 0);

    await page.goto('/catalog');
    await searchItems(page, item.sku);
    await page.locator('.items tbody tr').filter({ hasText: item.sku }).getByRole('button', { name: 'Delete' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Cancel' }).click();

    await expect(page.getByRole('alertdialog')).toHaveCount(0);
    await expect(page.locator('.items tbody tr').filter({ hasText: item.sku })).toHaveCount(1);
  });
});

test.describe('Movement corrections', () => {
  test('deletes an incoming movement registered by mistake and restores the stock', async ({ page, request }) => {
    const { item } = await createUserAndItem(request, 5);
    const mistake = await (await request.post('/api/inventory/incoming', { data: { itemId: item.id, quantity: 40, reason: 'Typo' } })).json();

    await page.goto(`/inventory/movements/${mistake.id}`);
    await page.getByRole('button', { name: 'Edit' }).click();
    await page.getByLabel('Reason').fill('Duplicated delivery note');
    await page.getByRole('button', { name: 'Save' }).click();
    await expectToast(page, 'Reason updated');
    await expect(page.locator('.facts')).toContainText('Duplicated delivery note');

    await page.getByRole('button', { name: 'Delete movement' }).click();
    await expect(page.getByRole('alertdialog')).toContainText(`40 units of ${item.sku} will be removed from stock`);
    await page.getByRole('alertdialog').getByRole('button', { name: 'Delete movement' }).click();

    await expectToast(page, `Movement #${mistake.id} deleted`);
    await expect(page).toHaveURL(/\/inventory$/);
    const stock = await (await request.get(`/api/items/${item.id}`)).json();
    expect(stock.stockOnHand).toBe(5);
  });

  test('an incoming movement already allocated to an order cannot be deleted', async ({ page, request }) => {
    const { user, item } = await createUserAndItem(request, 0);
    await request.post('/api/orders', { data: { userId: user.id, itemId: item.id, requestedQuantity: 2 } });
    const delivery = await (await request.post('/api/inventory/incoming', { data: { itemId: item.id, quantity: 5 } })).json();

    await page.goto(`/inventory/movements/${delivery.id}`);

    await expect(page.getByText('This stock was already allocated to orders')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Delete movement' })).toHaveCount(0);
  });
});
