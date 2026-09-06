const assert = require('node:assert/strict');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
(async () => {
  const browser = await chromium.launch({headless:true, executablePath:process.env.PW_CHROMIUM_EXECUTABLE});
  try {
    const page = await browser.newPage({viewport:{width:1280,height:1000}});
    const errors = [];
    page.on('pageerror', error => errors.push(String(error)));
    await page.route('http**/*', route => {errors.push('External request'); return route.abort();});
    await page.goto('file://' + path.resolve('target/task-ui/board-preview.html'));
    assert.equal(await page.locator('.grid .task').count(), 64);
    assert.equal(await page.locator('.bonus-grid .task').count(), 3);
    assert.equal(await page.locator('.grid .task img').first().evaluate(img => img.complete && img.naturalWidth > 0), true);
    await page.locator('.grid .task').first().hover();
    assert.match(await page.locator('#detail').innerText(), /望远镜/);
    await page.keyboard.press('Tab');
    assert.equal(await page.locator('#board').isVisible(), false);
    await page.keyboard.press('Tab');
    assert.equal(await page.locator('#board').isVisible(), true);
    await page.screenshot({path:'target/task-ui/board-preview.png', fullPage:true});
    for (const width of [1920, 1366, 768, 360]) {
      await page.setViewportSize({width, height:1080});
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
      const columns = await page.locator('.grid').evaluate(grid => getComputedStyle(grid).gridTemplateColumns.split(' ').length);
      assert.equal(columns, 8);
    }
    assert.deepEqual(errors, []);
    console.log('Board preview passed: 64 + 3 cells, icon loading, hover, Tab toggle, 4 widths, offline.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
