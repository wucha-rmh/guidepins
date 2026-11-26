import { build } from 'vite'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gzipSync } from 'node:zlib'

// 查看器与 Worker 各自打成一个脚本，再内联到模板；本地打开时不依赖模块加载或网络。
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
async function bundle(entry, define = {}) {
  const result = await build({
    root, configFile: false, logLevel: 'warn', define,
    build: { write: false, minify: true, target: 'es2022',
      lib: { entry, formats: ['iife'], name: 'GuidePinsOffline' },
    },
  })
  const outputs = Array.isArray(result) ? result : [result]
  return outputs.flatMap(output => output.output).filter(item => item.type === 'chunk').map(item => item.code).join('\n')
}
const worker = await bundle(resolve(root, 'node_modules/maplibre-gl/dist/maplibre-gl-worker.mjs'))
const viewer = await bundle(resolve(root, 'offline/viewer.ts'), {
  __OFFLINE_WORKER__: JSON.stringify(worker), 'import.meta.url': JSON.stringify('file:///guidepins-offline.html'),
})
const viewerCode = gzipSync(viewer, { level: 9 }).toString('base64')
const css = await readFile(resolve(root, 'node_modules/maplibre-gl/dist/maplibre-gl.css'), 'utf8')
const skin = await readFile(resolve(root, 'offline/viewer.css'), 'utf8')
const licenses = await readFile(resolve(root, 'node_modules/maplibre-gl/LICENSE.txt'), 'utf8')
const output = resolve(root, '../backed/target/offline-assets/offline')
await mkdir(output, { recursive: true })
await writeFile(resolve(output, 'viewer.html'), `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline' blob:; style-src 'unsafe-inline'; worker-src blob: data:; child-src blob: data:; img-src data: blob:; font-src data:; connect-src blob:">
<title>拾点地图 · 南京离线地图</title><style>${css}\n${skin}</style></head>
<body><header><strong>拾点地图</strong><h1 id="title">正在打开离线地图</h1><span id="time"></span></header>
<main><aside><p id="status" role="status">正在读取本地内容…</p><p class="hint">南京全市简图 · 地点周边约 2 公里含详细底图。虚线框表示详细瓦片覆盖区域，范围外保留全市概况。</p>
<div class="tools"><button id="all">查看全市</button><button id="places">查看全部地点</button></div>
<h2>沿途地点</h2><ol id="pins"></ol><h2>攻略与出处</h2><div id="guides"></div></aside>
<div id="map" aria-label="南京离线地图"></div></main>
<script id="payload" type="application/octet-stream">__GUIDEPINS_PAYLOAD__</script>
<script id="viewer-code" type="application/octet-stream">${viewerCode}</script>
<script>
(async function(){
  if(!globalThis.DecompressionStream)throw Error('请使用较新版本的 Edge 或 Chrome 打开此文件。');
  const holder=document.getElementById('viewer-code');
  const bytes=Uint8Array.from(atob(holder.textContent.trim()),c=>c.charCodeAt(0));holder.remove();
  const code=await new Response(new Blob([bytes]).stream().pipeThrough(new DecompressionStream('gzip'))).arrayBuffer();
  const url=URL.createObjectURL(new Blob([code],{type:'text/javascript'}));
  const script=document.createElement('script');script.src=url;
  script.onload=()=>URL.revokeObjectURL(url);script.onerror=()=>{URL.revokeObjectURL(url);document.getElementById('status').textContent='查看器加载失败，请重新下载文件。';};document.body.append(script);
})().catch(error=>{document.getElementById('title').textContent='离线地图打开失败';document.getElementById('status').textContent=error.message;});
</script>
<!-- MapLibre GL JS license:\n${licenses.replace(/-->/g, '-- >')} -->
</body></html>`)
console.log('已生成南京单文件离线查看器模板：backed/target/offline-assets/offline/viewer.html')
