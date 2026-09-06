#!/usr/bin/env node
// Optional offline browser smoke test; use an existing Playwright installation/browser.
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

(async () => {
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.PW_CHROMIUM_EXECUTABLE ? { executablePath: process.env.PW_CHROMIUM_EXECUTABLE } : {}),
  });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1080 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('request', request => {
      assert(!/^https?:/.test(request.url()), 'Audit must not access the network');
    });
    const root = path.resolve(__dirname, '..');
    await page.goto(pathToFileURL(path.join(root, 'target/task-ui/audit/task-audit.html')).href);
    assert.equal(await page.locator('tbody tr').count(), 158);
    assert.equal(await page.locator('tr[data-playing=true]').count(), 32);
    await page.locator('#pause-all').click();
    assert.equal(await page.locator('tr[data-playing=true]').count(), 0);
    await page.locator('#animated-only').check();
    assert.equal(await page.locator('tbody tr:visible').count(), 32);
    await page.locator('#search').fill('COLLECT_ALL_COPPER_VARIANTS');
    assert.equal(await page.locator('tbody tr:visible').count(), 1);
    const copper = page.locator('#COLLECT_ALL_COPPER_VARIANTS');
    assert.equal(await copper.locator('.frame-select').count(), 32);
    await copper.locator('.frame-select').last().click();
    assert.equal(await copper.getAttribute('data-current'), '31');
    assert.equal(await copper.locator('.hero').getAttribute('src'),
      await copper.locator('.frame-select img').last().getAttribute('src'));
    await copper.locator('[data-action=next]').click();
    assert.equal(await copper.getAttribute('data-current'), '0');
    await copper.locator('[data-action=previous]').click();
    assert.equal(await copper.getAttribute('data-current'), '31');
    await page.locator('#collapse-all').click();
    assert.equal(await page.locator('details[open]').count(), 0);
    await page.locator('#expand-all').click();
    assert.equal(await page.locator('details[open]').count(), 32);
    await page.locator('#play-all').click();
    assert.equal(await page.locator('tr[data-playing=true]').count(), 32);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.waitForFunction(() => document.querySelectorAll('tr[data-playing=true]').length === 0);
    await page.locator('#search').fill('sapling');
    await page.evaluate(() => window.scrollTo(0, 0));
    assert(await page.locator('tbody tr:visible').count() > 0);
    await page.screenshot({ path: path.join(root, 'target/task-ui/audit/browser-saplings.png') });
    const missing = await page.locator('img').evaluateAll(images => images.filter(image => !image.complete || image.naturalWidth === 0).length);
    assert.equal(missing, 0);
    assert.deepEqual(errors, []);
    console.log('Browser audit passed: 158 rows, 32 animations, search/filter, pause/play, all 32 copper frames, stepping, reduced motion, no external requests/errors.');
  } finally {
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
