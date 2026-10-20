import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { service } from './helpers';

test('navegación y guard según los cinco roles', async ({ page }) => {
  await page.goto('/health');
  for (const role of ['ADMIN', 'DESPACHADOR', 'CLIENTE', 'AUDITOR', 'NONE']) {
    await page.evaluate(role => sessionStorage.setItem('ci-role', role), role);
    await page.goto('/admin/mq');
    if (role === 'ADMIN') await expect(page.getByRole('heading', { name: 'Administración de RabbitMQ' })).toBeVisible();
    else await expect(page).toHaveURL(/\/forbidden$/);
    if (role === 'NONE') {
      await page.goto('/shipments');
      await expect(page).toHaveURL(/\/forbidden$/);
    }
  }
});

test('tabla y formulario de colas operan contra RabbitMQ real', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto('/admin/mq');
  await page.getByRole('tab', { name: 'Colas', exact: true }).click();
  await expect(page.getByText('q.rutaexpress.command', { exact: true })).toBeVisible();
  const name = `q.browser.${randomUUID()}`;
  await page.getByLabel('Nombre', { exact: true }).fill('amq.invalida');
  await page.getByRole('button', { name: 'Crear cola', exact: true }).click();
  await expect(page.getByRole('alert').filter({ hasText: 'reservado' })).toBeVisible();
  await page.getByLabel('Nombre', { exact: true }).fill(name);
  await page.getByRole('button', { name: 'Crear cola', exact: true }).click();
  const row = page.getByRole('row').filter({ hasText: name });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Eliminar', exact: true }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: /eliminar/i }).click();
  await expect(row).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('crear, aceptar y visualizar confirmación asíncrona desde el navegador', async ({ page, request }) => {
  const svc = await service(request);
  await page.goto('/shipments/new');
  const form = page.locator('form');
  await form.locator('select').first().selectOption({ label: `${svc.code} · Servicio CI` });
  await page.getByLabel('Peso (kg)', { exact: true }).fill('1.5');
  await page.getByLabel('Volumen (m³)', { exact: true }).fill('0.01');
  const recipient = `Navegador ${randomUUID()}`;
  await page.getByLabel('Destinatario', { exact: true }).fill(recipient);
  await page.getByLabel('Dirección de origen', { exact: true }).fill('Origen navegador');
  await page.getByLabel('Dirección de destino', { exact: true }).fill('Destino navegador');
  await page.getByRole('button', { name: 'Crear envío', exact: true }).click();
  await expect(page).toHaveURL(/\/shipments$/);
  const row = page.getByRole('row').filter({ hasText: recipient });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: 'Aceptar', exact: true }).click();
  await expect(row.getByText('RESERVADA', { exact: true })).toBeVisible({ timeout: 60_000 });
  await expect(row.getByRole('button', { name: 'Enviar a bodega', exact: true })).toBeEnabled();
});

test('404 y foco de formulario funcionan en escritorio y móvil', async ({ page }) => {
  await page.goto('/ruta-que-no-existe');
  await expect(page.getByText('404', { exact: true })).toBeVisible();
  await page.goto('/shipments/new');
  await page.getByRole('button', { name: 'Crear envío', exact: true }).click();
  await expect(page.getByText('Campo obligatorio').first()).toBeVisible();
  await page.keyboard.press('Tab');
  await expect(page.locator(':focus')).toHaveCount(1);
});
