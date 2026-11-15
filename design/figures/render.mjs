// Renders the four application figures with the Astryx neutral theme (light, for print) and the theme's
// Lucide icons. Output: evidence/application-figures-2026-11-15/fig1..4.png plus figures.sha256.
// Usage (from design/): node figures/render.mjs
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {categories, iconSvg, themeVersion, lucideVersion} from '../flow-visuals.mjs';

const designDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(designDir, '..');
const cache = join(designDir, '.cache');
const outDir = join(repo, 'evidence', 'application-figures-2026-11-15');
const chrome = ['C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(existsSync);
mkdirSync(cache, {recursive: true});
mkdirSync(outDir, {recursive: true});

const nm = p => pathToFileURL(join(designDir, 'node_modules', p)).href;
const repoUrl = p => pathToFileURL(join(repo, p)).href;
const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const NODE = 96;          // node square, CSS px
const HANDLE = 12;

async function icon(name, size, color, width = 2) {
  return (await iconSvg(name, {size, width})).replace('stroke="currentColor"', `stroke="${color}"`);
}

/** n8n-style node: coloured square with the category icon, label and badges underneath. */
async function node(n) {
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
function edge(a, ai, b, tone, label, lx, ly) {
  const x1 = a.x + NODE / 2, y1 = a.y + NODE * (a.outs ?? [0.5])[ai];
  const x2 = b.x - NODE / 2 - HANDLE / 2 - 1, y2 = b.y + NODE / 2;
  const bend = Math.max(48, Math.abs(x2 - x1) / 2);
  const colour = {next: 'var(--color-icon-secondary)', yes: 'var(--color-success)', no: 'var(--color-icon-secondary)'}[tone];
  const dash = tone === 'no' ? ' stroke-dasharray="7 6"' : '';
  const path = `<path d="M${x1} ${y1} C${x1 + bend} ${y1} ${x2 - bend} ${y2} ${x2} ${y2}" stroke="${colour}" stroke-width="2.5" fill="none"${dash} marker-end="url(#arrow-${tone})"/>`;
  const pill = label ? `<span class="port ${tone}" style="left:${lx}px;top:${ly}px">${esc(label)}</span>` : '';
  return {path, pill};
}

function markers() {
  return ['next', 'yes', 'no'].map(t => {
    const c = t === 'yes' ? 'var(--color-success)' : 'var(--color-icon-secondary)';
    return `<marker id="arrow-${t}" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="4.4" markerHeight="4.4" orient="auto"><path d="M0 0 L10 5 L0 10 z" fill="${c}"/></marker>`;
  }).join('');
}

function page(w, h, body) {
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

async function figure1() {
  const W = 1320, H = 520, main = 250, up = 92;
  const n = {
    start: {x: 110, y: main, cat: 'trigger', shape: 'trigger', icon: 'zap', title: '주차 후 시작', sub: '시작 · 직접 실행'},
    cold: {x: 330, y: main, cat: 'logic', icon: 'split', title: '외기 5°C 이하', sub: '조건 · 외기 온도', outs: [0.3, 0.7]},
    hvac: {x: 550, y: up, cat: 'climate', icon: 'thermometer', title: '공조 목표 23°C', sub: '공조 · 운전석'},
    seat: {x: 770, y: main, cat: 'seat', icon: 'armchair', title: '운전석 등받이', sub: '시트 · 뒤로 제한 이동',
      badges: [{tone: 'warning', icon: 'hand', text: '실행마다 확인'}]},
    light: {x: 990, y: main, cat: 'light', icon: 'lightbulb', title: '조명 30%', sub: '앰비언트 밝기',
      badges: [{tone: 'info', icon: 'sparkles', text: '제안값'}]},
    end: {x: 1210, y: main, cat: 'end', shape: 'end', icon: 'flag', title: '완료', sub: '종료'},
  };
  const e = [edge(n.start, 0, n.cold, 'next'), edge(n.cold, 0, n.hvac, 'yes', '충족', 440, 196),
    edge(n.cold, 1, n.seat, 'no', '미충족', 560, 330), edge(n.hvac, 0, n.seat, 'next'),
    edge(n.seat, 0, n.light, 'next'), edge(n.light, 0, n.end, 'next')];
  const nodes = (await Promise.all(Object.values(n).map(node))).join('');
  const body = `<div class="canvas"></div>
  <div class="head"><div class="title">${await icon('wand-sparkles', 26, 'var(--color-icon-primary)')}AI 첫 초안 · 겨울 차박</div>
    <span class="chip">${await icon('info', 18, 'var(--color-icon-secondary)')}사용 시나리오 예시</span></div>
  <svg class="edges" width="${W}" height="${H}"><defs>${markers()}</defs>${e.map(x => x.path).join('')}</svg>
  ${nodes}${e.map(x => x.pill).join('')}
  <div class="legend"><span><svg width="34" height="6"><line x1="0" y1="3" x2="34" y2="3" stroke="var(--color-success)" stroke-width="3"/></svg>외기 조건 충족 시 공조만 실행</span>
    <span><svg width="34" height="6"><line x1="0" y1="3" x2="34" y2="3" stroke="var(--color-icon-secondary)" stroke-width="3" stroke-dasharray="7 6"/></svg>미충족이면 공조 건너뜀</span>
    <span>${await icon('hand', 18, 'var(--color-icon-secondary)')}시트는 실행할 때마다 확인</span>
    <span>${await icon('sparkles', 18, 'var(--color-icon-secondary)')}밝기는 바꿀 수 있는 제안값</span></div>`;
  return {name: 'fig1-winter-draft', w: W, h: H, html: page(W, H, body), scale: 2};
}

async function figure2() {
  const W = 1320, H = 630;
  const step = async (ic, title, sub) => `<div class="step">${await icon(ic, 30, 'var(--color-icon-primary)')}<div><b>${esc(title)}</b>${sub ? `<span>${esc(sub)}</span>` : ''}</div></div>`;
  const arrow = await icon('chevron-right', 30, 'var(--color-icon-secondary)', 2.5);
  const before = 212, after = 400;
  const nb = {
    seat: {x: 300, y: before, cat: 'seat', icon: 'armchair', title: '운전석 등받이', sub: '그대로', dim: true},
    light: {x: 520, y: before, cat: 'light', icon: 'lightbulb', title: '조명 30%', sub: '선택한 노드', selected: true},
    end: {x: 740, y: before, cat: 'end', shape: 'end', icon: 'flag', title: '완료', sub: '종료'},
  };
  const na = {
    seat: {x: 300, y: after, cat: 'seat', icon: 'armchair', title: '운전석 등받이', sub: '그대로', dim: true},
    light: {x: 520, y: after, cat: 'light', icon: 'lightbulb', title: '조명 30%', sub: '앰비언트 밝기'},
    wait: {x: 740, y: after, cat: 'timing', icon: 'hourglass', title: '10분 대기', sub: '시간 · 대기', badges: [{tone: 'success', icon: 'plus', text: '추가'}]},
    light20: {x: 960, y: after, cat: 'light', icon: 'lightbulb', title: '조명 20%', sub: '앰비언트 밝기', badges: [{tone: 'success', icon: 'plus', text: '추가'}]},
    end: {x: 1180, y: after, cat: 'end', shape: 'end', icon: 'flag', title: '완료', sub: '종료'},
  };
  const e = [edge(nb.seat, 0, nb.light, 'next'), edge(nb.light, 0, nb.end, 'next'), edge(na.seat, 0, na.light, 'next'),
    edge(na.light, 0, na.wait, 'next'), edge(na.wait, 0, na.light20, 'next'), edge(na.light20, 0, na.end, 'next')];
  const nodes = (await Promise.all([...Object.values(nb), ...Object.values(na)].map(node))).join('');
  const body = `<div class="canvas"></div>
  <style>.steps{position:absolute;left:28px;right:28px;top:76px;display:flex;align-items:center;gap:14px}
  .step{flex:1;display:flex;align-items:center;gap:14px;padding:14px 18px;border-radius:var(--radius-container);background:var(--color-background-surface);border:1px solid var(--color-border-emphasized);box-shadow:var(--shadow-low)}
  .step div{display:flex;flex-direction:column;gap:2px}.step b{font-size:23px}.step span{font-size:20px;color:var(--color-text-secondary)}
  .row{position:absolute;left:28px;display:flex;align-items:center;gap:8px;font-size:19px;font-weight:700}
  .row .chip{font-size:20px}.keep{position:absolute;right:28px;top:${before + 30}px}</style>
  <div class="head"><div class="title">${await icon('wand-sparkles', 26, 'var(--color-icon-primary)')}선택한 노드 주변만 고치는 AI 수정</div>
    <span class="chip">${await icon('info', 18, 'var(--color-icon-secondary)')}사용 시나리오 예시</span></div>
  <div class="steps">${await step('mouse-pointer-click', '조명 30% 노드 선택', '고칠 카드를 고릅니다')}${arrow}
    ${await step('mic', 'AI에게 수정 요청', '“10분 뒤 밝기를 20%로 낮춰줘”')}${arrow}
    ${await step('circle-check', '변경안 확인 후 적용', '수정 전·후를 비교합니다')}</div>
  <div class="row" style="top:${before + 34}px"><span class="chip">수정 전</span></div>
  <div class="row" style="top:${after + 34}px"><span class="chip" style="color:var(--color-on-success-muted);background:var(--color-success-muted);border-color:var(--color-success)">수정 후</span></div>
  <span class="chip keep">${await icon('shield-check', 18, 'var(--color-icon-secondary)')}공조·시트 단계는 바꾸지 않음</span>
  <svg class="edges" width="${W}" height="${H}"><defs>${markers()}</defs>${e.map(x => x.path).join('')}</svg>${nodes}`;
  return {name: 'fig2-light-edit', w: W, h: H, html: page(W, H, body), scale: 2};
}

/** Real emulator screenshots: original pixels inside a frame; nothing in the screen area is redrawn. */
async function screenFigure(name, src, crop, chipIcon, chipText, sideText, fontScale = 1) {
  const pad = 22, head = Math.round(60 * fontScale), W = crop.w + pad * 2, H = crop.h + head + pad * 2;
  const body = `<div class="canvas" style="background-image:none;background-color:var(--color-background-surface)"></div>
  <div class="head" style="top:${pad - 4}px;left:${pad}px;right:${pad}px;height:${head - 10}px">
    <span class="chip" style="font-size:${Math.round(17 * fontScale)}px">${await icon(chipIcon, Math.round(19 * fontScale), 'var(--color-icon-secondary)')}${esc(chipText)}</span>
    <span class="chip" style="font-size:${Math.round(17 * fontScale)}px;color:var(--color-on-info-muted);background:var(--color-info-muted);border-color:transparent">${esc(sideText)}</span></div>
  <div style="position:absolute;left:${pad}px;top:${head + pad}px;width:${crop.w}px;height:${crop.h}px;border-radius:12px;overflow:hidden;
    outline:1px solid var(--color-border-emphasized);background:url('${repoUrl(src)}') -${crop.x}px -${crop.y}px no-repeat"></div>`;
  return {name, w: W, h: H, html: page(W, H, body), scale: 1};
}

function shoot(fig) {
  const html = join(cache, `${fig.name}.html`);
  writeFileSync(html, fig.html);
  const png = join(outDir, `${fig.name}.png`);
  execFileSync(chrome, ['--headless=new', '--disable-gpu', '--hide-scrollbars', '--no-first-run', '--no-default-browser-check',
    `--user-data-dir=${join(cache, 'chrome-profile')}`, `--force-device-scale-factor=${fig.scale}`,
    `--window-size=${fig.w},${fig.h}`, '--virtual-time-budget=8000', `--screenshot=${png}`, pathToFileURL(html).href], {stdio: 'pipe'});
  return png;
}

const figures = [await figure1(), await figure2(),
  // 19:49 Astryx-UI release APK on the ForkyRelease AVD, "출근 예시로 시작" → 02 확인 (evidence/W30/release-commute-review.png).
  await screenFigure('fig3-commute-screen', 'evidence/W30/release-commute-review.png',
    {x: 920, y: 650, w: 1165, h: 265}, 'car', '제출 APK 실제 화면 · 공식 Pleos 에뮬레이터 v2.1.0 · 11월 15일', '실제 화면'),
  await screenFigure('fig4-driving-restriction', 'evidence/W04/app-driving-restriction.png',
    {x: 1400, y: 570, w: 590, h: 250}, 'car', '주행 중(D · 15 km/h)', '실제 화면', 1.25)];
const lines = [];
for (const f of figures) {
  const png = shoot(f);
  lines.push(`${createHash('sha256').update(readFileSync(png)).digest('hex')}  ${f.name}.png  (${f.w * f.scale}x${f.h * f.scale})`);
}
lines.push(`# @astryxdesign/theme-neutral ${themeVersion}, lucide-react ${lucideVersion}, renderer ${chrome}`);
writeFileSync(join(outDir, 'figures.sha256'), lines.join('\n') + '\n');
console.log(lines.join('\n'));
