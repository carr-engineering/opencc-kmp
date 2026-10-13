// Standalone benchmark probe via Chrome's protocol and Node builtin APIs; no automation package.
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const [url, chromePath] = process.argv.slice(2);
if (!url || !chromePath) throw new Error('Usage: web_benchmark_probe.mjs URL CHROME_PATH');
const directory = await mkdtemp(join(tmpdir(), 'opencc-chrome-'));
const chrome = spawn(chromePath, ['--headless=new', '--no-first-run', '--no-default-browser-check',
  '--disable-background-networking', '--enable-precise-memory-info', '--remote-debugging-port=0',
  `--user-data-dir=${directory}`, 'about:blank'], { stdio: ['ignore', 'ignore', 'pipe'] });
let errorLog = '';
chrome.stderr.on('data', data => { errorLog += data; });
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let socket;
try {
  const deadline = Date.now() + 60000;
  let port;
  while (Date.now() < deadline) {
    try { port = (await readFile(join(directory, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; break; }
    catch { if (chrome.exitCode !== null) throw new Error(errorLog); await pause(50); }
  }
  if (!port) throw new Error('Chrome debug endpoint did not start: ' + errorLog);
  const version = await (await fetch(`http://127.0.0.1:${port}/json/version`)).json();
  socket = new WebSocket(version.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, {once:true}); socket.addEventListener('error', reject, {once:true}); });
  let nextId = 0;
  const pending = new Map();
  const exceptions = [];
  socket.addEventListener('message', event => {
    const message = JSON.parse(event.data);
    if (message.id) {
      const request = pending.get(message.id);
      if (request) { pending.delete(message.id); clearTimeout(request.timer); message.error ? request.reject(new Error(JSON.stringify(message.error))) : request.resolve(message.result); }
    } else if (message.method === 'Runtime.exceptionThrown') exceptions.push(message.params.exceptionDetails);
  });
  const command = (method, params = {}, sessionId) => new Promise((resolve, reject) => {
    const id = ++nextId;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`Timed out: ${method}`)); }, 30000);
    pending.set(id, {resolve, reject, timer});
    socket.send(JSON.stringify({id, method, params, ...(sessionId ? {sessionId} : {})}));
  });
  const {targetId} = await command('Target.createTarget', {url:'about:blank'});
  const {sessionId} = await command('Target.attachToTarget', {targetId, flatten:true});
  await command('Runtime.enable', {}, sessionId);
  await command('Performance.enable', {}, sessionId);
  await command('Page.navigate', {url}, sessionId);
  let fixture;
  const appDeadline = Date.now() + 60000;
  while (Date.now() < appDeadline) {
    const result = await command('Runtime.evaluate', {expression:'globalThis.openccBenchmarkReport || null', returnByValue:true}, sessionId);
    if (result.result.value) { fixture = JSON.parse(result.result.value); break; }
    if (exceptions.length) throw new Error(JSON.stringify(exceptions));
    await pause(25);
  }
  if (!fixture) throw new Error('App did not complete: ' + JSON.stringify(exceptions));
  if (fixture.status !== 'pass' || !fixture.output_verified || fixture.samples_ms.length !== 300 || fixture.warmup_calls !== 100) throw new Error(JSON.stringify(fixture));
  const {metrics} = await command('Performance.getMetrics', {}, sessionId);
  const metricsByName = Object.fromEntries(metrics.map(item => [item.name, item.value]));
  const timing = await command('Runtime.evaluate', {expression:'performance.now()', returnByValue:true}, sessionId);
  const identity = await command('Runtime.evaluate', {expression:'JSON.stringify({user_agent:navigator.userAgent,platform:navigator.platform,hardware_concurrency:navigator.hardwareConcurrency,cross_origin_isolated:globalThis.crossOriginIsolated})',returnByValue:true}, sessionId);
  const clock = await command('Runtime.evaluate', {expression:'(() => {let last=performance.now(),step=Infinity;for(let i=0;i<20000;i++){const next=performance.now();if(next>last)step=Math.min(step,next-last);last=next;}return Number.isFinite(step)?step:null;})()',returnByValue:true}, sessionId);
  console.log(JSON.stringify({fixture, browser: version.Browser, identity:JSON.parse(identity.result.value),metrics:metricsByName, observed_report_ms:timing.result.value,minimum_observed_clock_step_ms:clock.result.value}));
  await command('Browser.close');
} finally {
  socket?.close();
  if (chrome.exitCode === null) { chrome.kill(); await new Promise(resolve => chrome.once('exit', resolve)); }
  await rm(directory, {recursive:true, force:true, maxRetries:5, retryDelay:100});
}
