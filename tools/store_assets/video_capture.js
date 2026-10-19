// Seeks every CSS animation of an HTML page frame by frame and pipes the screenshots into ffmpeg (H.264, no audio).
// Usage: node video_capture.js <page.html> <out.mp4> <seconds> <fps> [--frames=a,b,c  (writes PNG stills instead)]
const { chromium } = require('playwright');
const { spawn } = require('child_process');
const path = require('path');
(async () => {
  const [html, out, seconds, fps] = process.argv.slice(2, 6);
  const stills = (process.argv.find(a => a.startsWith('--frames=')) || '').slice(9);
  const browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || undefined });
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  await page.goto('file://' + path.resolve(html));
  await page.evaluate(() => document.fonts.ready);
  const seek = ms => page.evaluate(t => document.getAnimations().forEach(a => { a.pause(); a.currentTime = t; }), ms);
  if (stills) {
    for (const s of stills.split(',')) {
      await seek(parseFloat(s) * 1000);
      await page.screenshot({ path: `${out}-${s}.png` });
    }
    await browser.close();
    return;
  }
  const ff = spawn('ffmpeg', ['-y', '-v', 'error', '-f', 'image2pipe', '-framerate', fps, '-i', '-', '-c:v', 'libx264',
    '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', fps, out], { stdio: ['pipe', 'inherit', 'inherit'] });
  const frames = Math.round(parseFloat(seconds) * parseFloat(fps));
  for (let i = 0; i < frames; i++) {
    await seek(i * 1000 / parseFloat(fps));
    const buf = await page.screenshot({ type: 'jpeg', quality: 95 });
    if (!ff.stdin.write(buf)) await new Promise(r => ff.stdin.once('drain', r));
  }
  ff.stdin.end();
  await new Promise((res, rej) => ff.on('close', c => (c === 0 ? res() : rej(new Error('ffmpeg ' + c)))));
  await browser.close();
  console.log(`${frames} frames -> ${out}`);
})();
