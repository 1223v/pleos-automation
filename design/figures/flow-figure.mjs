// Shared drawing helpers for the Astryx flow figures: node, edge, page shell and the headless-Chrome
// screenshot. Used by figures/render.mjs (application figures) and figures/render-vision.mjs (README vision).
import {execFileSync} from 'node:child_process';
import {existsSync, mkdirSync, writeFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {categories, iconSvg} from '../flow-visuals.mjs';

export const designDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export const repo = resolve(designDir, '..');
const cache = join(designDir, '.cache');
export const chrome = ['C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(existsSync);
mkdirSync(cache, {recursive: true});

const nm = p => pathToFileURL(join(designDir, 'node_modules', p)).href;
export const repoUrl = p => pathToFileURL(join(repo, p)).href;
export const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

export const NODE = 96;          // node square, CSS px
export const HANDLE = 12;

export async function icon(name, size, color, width = 2) {
  return (await iconSvg(name, {size, width})).replace('stroke="currentColor"', `stroke="${color}"`);
}

/** n8n-style node: coloured square with the category icon, label and badges underneath. */
export async function node(n) {
  const cat = categories[n.cat];
  const shape = n.shape === 'trigger' ? `border-radius:${NODE / 2}px var(--radius-container) var(--radius-container) ${NODE / 2}px`
    : n.shape === 'end' ? 'border-radius:50%' : 'border-radius:var(--radius-container)';
  const handles = [];
  if (n.shape !== 'trigger') handles.push(`<i class="h" style="left:${-HANDLE / 2}px;top:${NODE / 2 - HANDLE / 2}px"></i>`);
  for (const o of n.outs ?? (n.shape === 'end' ? [] : [0.5])) handles.push(`<i class="h out" style="left:${NODE - HANDLE / 2}px;top:${NODE * o - HANDLE / 2}px"></i>`);
  const badges = [];
  for (const b of n.badges ?? []) badges.push(`<span class="badge ${b.tone}">${await icon(b.icon, 16, `var(--color-on-${b.tone}-muted)`, 2.4)}${esc(b.text)}</span>`);
  return `<div class="node${n.dim ? ' dim' : ''}" style="left:${n.x - NODE / 2}px;top:${n.y}px">
    <div class="sq${n.selected ? ' sel' : ''}" style="${shape};background:var(--color-background-${cat.hue})">
      ${await icon(n.icon, 44, `var(--color-icon-${cat.hue})`)}${handles.join('')}</div>
    <div class="lbl"><b>${esc(n.title)}</b><span>${esc(n.sub)}</span>${badges.join('')}</div></div>`;
}

/** Bezier from an output handle to an input handle, with an arrowhead and an optional port label. */
export function edge(a, ai, b, tone, label, lx, ly) {
  const x1 = a.x + NODE / 2, y1 = a.y + NODE * (a.outs ?? [0.5])[ai];
  const x2 = b.x - NODE / 2 - HANDLE / 2 - 1, y2 = b.y + NODE / 2;
  const bend = Math.max(48, Math.abs(x2 - x1) / 2);
  const colour = {next: 'var(--color-icon-secondary)', yes: 'var(--color-success)', no: 'var(--color-icon-secondary)'}[tone];
  const dash = tone === 'no' ? ' stroke-dasharray="7 6"' : '';
  const path = `<path d="M${x1} ${y1} C${x1 + bend} ${y1} ${x2 - bend} ${y2} ${x2} ${y2}" stroke="${colour}" stroke-width="2.5" fill="none"${dash} marker-end="url(#arrow-${tone})"/>`;
  const pill = label ? `<span class="port ${tone}" style="left:${lx}px;top:${ly}px">${esc(label)}</span>` : '';
  return {path, pill};
}

export function markers() {
  return ['next', 'yes', 'no'].map(t => {
    const c = t === 'yes' ? 'var(--color-success)' : 'var(--color-icon-secondary)';
    return `<marker id="arrow-${t}" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="4.4" markerHeight="4.4" orient="auto"><path d="M0 0 L10 5 L0 10 z" fill="${c}"/></marker>`;
  }).join('');
}

export function page(w, h, body) {
  return `<!doctype html><html lang="ko" data-theme="light"><head><meta charset="utf-8">
<link rel="stylesheet" href="${nm('@astryxdesign/core/src/reset.css')}">
<link rel="stylesheet" href="${nm('@astryxdesign/core/dist/astryx.css')}">
<link rel="stylesheet" href="${nm('@astryxdesign/theme-neutral/fonts.css')}">
<link rel="stylesheet" href="${nm('@astryxdesign/theme-neutral/dist/theme.css')}">
<style>
html,body{margin:0;width:${w}px;height:${h}px;background:#fff;overflow:hidden}
.root{position:relative;width:${w}px;height:${h}px;font-family:Figtree,"Noto Sans KR","Malgun Gothic",sans-serif;color:var(--color-text-primary);-webkit-font-smoothing:antialiased}
.canvas{position:absolute;inset:0;border-radius:20px;border:1px solid var(--color-border-emphasized);
  background-color:var(--color-background-body);background-image:radial-gradient(var(--color-border-emphasized) 1.4px,transparent 1.6px);background-size:24px 24px;background-position:12px 12px}
.head{position:absolute;left:28px;right:28px;top:20px;display:flex;align-items:center;justify-content:space-between}
.title{display:flex;align-items:center;gap:10px;font-size:25px;font-weight:700}
.chip{display:inline-flex;align-items:center;gap:8px;padding:6px 14px;border-radius:999px;font-size:19px;font-weight:600;
  background:var(--color-background-surface);border:1px solid var(--color-border-emphasized);color:var(--color-text-secondary)}
.node{position:absolute;width:${NODE}px;display:flex;flex-direction:column;align-items:center}
.node.dim{opacity:.42}
.sq{position:relative;width:${NODE}px;height:${NODE}px;display:grid;place-items:center;border:1.5px solid var(--color-border);box-shadow:var(--shadow-low)}
.sq.sel{box-shadow:0 0 0 3px var(--color-background-surface),0 0 0 6px var(--color-accent),var(--shadow-low)}
.h{position:absolute;width:${HANDLE}px;height:${HANDLE}px;border-radius:50%;background:var(--color-background-surface);border:2.5px solid var(--color-icon-secondary);box-sizing:border-box}
.lbl{margin-top:10px;width:214px;display:flex;flex-direction:column;align-items:center;text-align:center;gap:3px}
.lbl b{font-size:24px;font-weight:700;line-height:1.25;letter-spacing:-.01em}
.lbl span{font-size:19px;line-height:1.3;color:var(--color-text-secondary)}
.lbl .badge{margin-top:5px}
.badge{display:inline-flex;align-items:center;gap:6px;padding:4px 11px;border-radius:999px;font-size:18px!important;font-weight:700}
.badge svg{flex:none}
.badge.warning{background:var(--color-warning-muted);color:var(--color-on-warning-muted)!important}
.badge.info{background:var(--color-info-muted);color:var(--color-on-info-muted)!important}
.badge.success{background:var(--color-success-muted);color:var(--color-on-success-muted)!important}
.port{position:absolute;transform:translate(-50%,-50%);padding:3px 12px;border-radius:999px;font-size:19px;font-weight:700;border:1.5px solid}
.port.yes{background:var(--color-success-muted);color:var(--color-on-success-muted);border-color:var(--color-success)}
.port.no{background:var(--color-background-surface);color:var(--color-text-secondary);border-color:var(--color-border-emphasized)}
.legend{position:absolute;left:28px;bottom:18px;display:flex;gap:26px;align-items:center;font-size:19px;color:var(--color-text-secondary);font-weight:600}
.legend span{display:inline-flex;align-items:center;gap:8px}
svg.edges{position:absolute;inset:0;overflow:visible}
</style></head><body><div class="root" data-astryx-theme="neutral">${body}</div></body></html>`;
}

/** Renders fig.html with headless Chrome into <outDir>/<fig.name>.png and returns the PNG path. */
export function shoot(fig, outDir) {
  mkdirSync(outDir, {recursive: true});
  const html = join(cache, `${fig.name}.html`);
  writeFileSync(html, fig.html);
  const png = join(outDir, `${fig.name}.png`);
  execFileSync(chrome, ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--no-first-run', '--no-default-browser-check',
    `--user-data-dir=${join(cache, 'chrome-profile')}`, `--force-device-scale-factor=${fig.scale}`,
    `--window-size=${fig.w},${fig.h}`, '--virtual-time-budget=8000', `--screenshot=${png}`, pathToFileURL(html).href], {stdio: 'pipe'});
  return png;
}
