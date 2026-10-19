// Renders the pages listed in <dir>/manifest.json to PNG (24-bit, no transparency, as Google Play requires).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const dir = path.resolve(process.argv[2]);
  const out = path.resolve(process.argv[3] || path.join(dir, 'png'));
  const manifest = JSON.parse(fs.readFileSync(path.join(dir, 'manifest.json')));
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || undefined });
  for (const item of manifest) {
    const page = await browser.newPage({ viewport: { width: item.w, height: item.h }, deviceScaleFactor: 1 });
    await page.goto('file://' + path.join(dir, item.html));
    await page.evaluate(() => document.fonts.ready);
    const file = path.join(out, item.png);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    await page.screenshot({ path: file, omitBackground: false });
    await page.close();
  }
  await browser.close();
  console.log(`${manifest.length} images in ${out}`);
})();
