// 倍々キャノン Service Worker
// ネットワーク優先で常に最新版を取りに行き、圏外のときだけキャッシュから起動する。
// ブラウザのHTTPキャッシュも使わずにサーバーへ新しいかを確かめる（変わっていなければ304で軽い）。
// version.json（最新版の確認）はキャッシュせず、いつもネットワークへ。
const CACHE = 'baibai-cannon-v4';
const ASSETS = ['./', './index.html', './manifest.webmanifest',
  './icons/icon-192.png', './icons/icon-512.png', './icons/apple-touch-icon.png', './icons/icon-maskable-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

const PAGE = new URL('./', self.location).pathname; // ゲームのページ（…/game/）

self.addEventListener('fetch', e => {
  const req = e.request, url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin) return;
  if (url.pathname.endsWith('/version.json')) return;
  const nav = req.mode === 'navigate';
  if (nav && url.pathname !== PAGE && url.pathname !== PAGE + 'index.html') return; // ゲーム以外のページはブラウザにまかせる
  // ページ本体は ?v=（更新のとき）が付いても1つの「./index.html」としてしまう。
  // リダイレクトはブラウザにまかせる（manual。たどった結果をページの読み込みに返すとエラーになるため）
  const key = nav ? './index.html' : req;
  const net = nav ? fetch(req.url, { cache: 'no-cache', credentials: 'same-origin', redirect: 'manual' }) : fetch(req, { cache: 'no-cache' });
  e.respondWith(
    net.then(res => {
      if (res.ok && !res.redirected) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(key, copy)); }
      return res;
    }).catch(() => caches.match(key).then(r => r || caches.match('./index.html')))
  );
});
