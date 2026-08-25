// Minimal Chrome DevTools Protocol capture helper; no npm dependency required.
const [port, targetUrl, email, password, outputPrefix] = process.argv.slice(2);
if (!port || !targetUrl || !email || !password || !outputPrefix) throw new Error("missing arguments");

const created = await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent("http://localhost/")}`, { method: "PUT" }).then(r => r.json());
const socket = new WebSocket(created.webSocketDebuggerUrl);
let nextId = 1;
const pending = new Map();
const waiters = new Map();
socket.addEventListener("message", event => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
        const { resolve, reject } = pending.get(message.id); pending.delete(message.id);
        return message.error ? reject(new Error(message.error.message)) : resolve(message.result);
    }
    const listeners = waiters.get(message.method) || [];
    waiters.delete(message.method); listeners.forEach(resolve => resolve(message.params));
});
await new Promise((resolve, reject) => { socket.addEventListener("open", resolve, { once: true }); socket.addEventListener("error", reject, { once: true }); });
function call(method, params = {}) {
    const id = nextId++;
    socket.send(JSON.stringify({ id, method, params }));
    return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
}
function event(method) { return new Promise(resolve => waiters.set(method, [...(waiters.get(method) || []), resolve])); }
async function navigate(url) { const loaded = event("Page.loadEventFired"); await call("Page.navigate", { url }); await loaded; }
async function pause(ms) { await new Promise(resolve => setTimeout(resolve, ms)); }
await call("Page.enable"); await call("Runtime.enable");
await navigate("http://localhost/");
const auth = await call("Runtime.evaluate", { awaitPromise: true, returnByValue: true, expression: `(async()=>{const c=await fetch('/api/auth/csrf',{credentials:'include'}).then(r=>r.json());return fetch('/api/auth/login',{method:'POST',credentials:'include',headers:{'Content-Type':'application/json','X-XSRF-TOKEN':c.data},body:JSON.stringify({email:${JSON.stringify(email)},password:${JSON.stringify(password)}})}).then(r=>r.json())})()` });
if (!auth.result.value?.success) throw new Error(`login failed: ${JSON.stringify(auth.result.value)}`);

for (const [name, width, height, mobile] of [["desktop",1440,1000,false],["tablet",768,1000,true],["mobile",390,844,true],["mobile-320",320,720,true]]) {
    await call("Emulation.setDeviceMetricsOverride", { width, height, deviceScaleFactor: 1, mobile });
    await navigate(targetUrl); await pause(1800);
    const metrics = await call("Page.getLayoutMetrics");
    const content = metrics.cssContentSize;
    const capture = await call("Page.captureScreenshot", { format: "png", captureBeyondViewport: true, clip: { x:0, y:0, width:Math.max(width, content.width), height:Math.max(height, content.height), scale:1 } });
    const { writeFile } = await import("node:fs/promises");
    await writeFile(`${outputPrefix}-${name}.png`, Buffer.from(capture.data, "base64"));
}
socket.close();
