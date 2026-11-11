// Prints an HTML page to PDF with Edge or Chrome, after waiting until Paged.js says it has finished the pages.
// Used by build.mjs. Needs Node 22 or newer (built-in fetch and WebSocket).
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";

export function findBrowser() {
  const candidates = [
    process.env.GUIDE_BROWSER,
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  ].filter(Boolean);
  const found = candidates.find((c) => fs.existsSync(c));
  if (!found) throw new Error("No Edge or Chrome found: set GUIDE_BROWSER to its path");
  return found;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export async function printToPdf(fileUrl, pdfPath, { timeoutMs = 240000 } = {}) {
  const port = 9300 + Math.floor(Math.random() * 500);
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), "guide-browser-"));
  const child = spawn(
    findBrowser(),
    [
      "--headless=new",
      "--disable-gpu",
      "--no-first-run",
      "--allow-file-access-from-files",
      `--remote-debugging-port=${port}`,
      `--user-data-dir=${profile}`,
      "about:blank",
    ],
    { stdio: "ignore" },
  );
  try {
    let target = null;
    for (let i = 0; i < 60 && !target; i++) {
      try {
        const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
        target = list.find((t) => t.type === "page");
      } catch (_e) {
        /* not ready yet */
      }
      if (!target) await sleep(500);
    }
    if (!target) throw new Error("the browser did not start");
    const ws = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      ws.onopen = resolve;
      ws.onerror = reject;
    });
    let id = 0;
    const pending = new Map();
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && pending.has(message.id)) {
        pending.get(message.id)(message);
        pending.delete(message.id);
      }
    };
    const call = (method, params = {}) =>
      new Promise((resolve, reject) => {
        const mine = ++id;
        pending.set(mine, (m) => (m.error ? reject(new Error(method + ": " + m.error.message)) : resolve(m.result)));
        ws.send(JSON.stringify({ id: mine, method, params }));
      });
    await call("Page.enable");
    await call("Page.navigate", { url: fileUrl });
    const end = Date.now() + timeoutMs;
    let done = false;
    while (Date.now() < end && !done) {
      await sleep(700);
      try {
        const r = await call("Runtime.evaluate", { expression: "window.__guideDone === true", returnByValue: true });
        done = r.result.value === true;
      } catch (_e) {
        /* the page is still loading */
      }
    }
    if (!done) throw new Error("Paged.js did not finish in time");
    await sleep(800);
    const pages = await call("Runtime.evaluate", { expression: "document.querySelectorAll('.pagedjs_page').length", returnByValue: true });
    const pdf = await call("Page.printToPDF", {
      printBackground: true,
      preferCSSPageSize: true,
      displayHeaderFooter: false,
      transferMode: "ReturnAsBase64",
    });
    fs.writeFileSync(pdfPath, Buffer.from(pdf.data, "base64"));
    ws.close();
    return pages.result.value;
  } finally {
    child.kill();
    await sleep(500);
    try {
      fs.rmSync(profile, { recursive: true, force: true });
    } catch (_e) {
      /* the browser may still hold a file */
    }
  }
}
