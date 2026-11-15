// Renders the two README vision figures (kids rear-seat routine, pet routine) in the same Astryx node style
// as the application figures. Output: docs/vision/*.png plus figures.sha256.
// Usage (from design/): node figures/render-vision.mjs
import {createHash} from 'node:crypto';
import {readFileSync, writeFileSync} from 'node:fs';
import {join} from 'node:path';
import {themeVersion, lucideVersion} from '../flow-visuals.mjs';
import {repo, chrome, esc, icon, node, edge, markers, page, shoot} from './flow-figure.mjs';

const outDir = join(repo, 'docs', 'vision');
const W = 1320, H = 500, ROW = 226;
const XS = [110, 330, 550, 770, 990, 1210];

/** Three cards across the top: what the driver says, what the AI finds, what runs. */
async function steps(items) {
  const arrow = await icon('chevron-right', 30, 'var(--color-icon-secondary)', 2.5);
  const cards = [];
  for (const [ic, title, sub] of items) {
    cards.push(`<div class="step">${await icon(ic, 30, 'var(--color-icon-primary)')}<div><b>${esc(title)}</b><span>${esc(sub)}</span></div></div>`);
  }
  return `<div class="steps">${cards.join(arrow)}</div>`;
}

async function legend(items) {
  const parts = [];
  for (const [ic, text] of items) parts.push(`<span>${await icon(ic, 18, 'var(--color-icon-secondary)')}${esc(text)}</span>`);
  return `<div class="legend">${parts.join('')}</div>`;
}

async function visionFigure({name, titleIcon, title, stepItems, nodes, legendItems}) {
  const placed = nodes.map((n, i) => ({...n, x: XS[i], y: ROW}));
  const e = placed.slice(1).map((n, i) => edge(placed[i], 0, n, 'next'));
  const body = `<div class="canvas"></div>
  <style>.steps{position:absolute;left:28px;right:28px;top:76px;display:flex;align-items:center;gap:14px}
  .step{flex:1;display:flex;align-items:center;gap:14px;padding:14px 18px;border-radius:var(--radius-container);background:var(--color-background-surface);border:1px solid var(--color-border-emphasized);box-shadow:var(--shadow-low)}
  .step div{display:flex;flex-direction:column;gap:2px}.step b{font-size:23px}.step span{font-size:20px;color:var(--color-text-secondary)}</style>
  <div class="head"><div class="title">${await icon(titleIcon, 26, 'var(--color-icon-primary)')}${esc(title)}</div>
    <span class="chip">${await icon('telescope', 18, 'var(--color-icon-secondary)')}비전 시나리오</span></div>
  ${await steps(stepItems)}
  <svg class="edges" width="${W}" height="${H}"><defs>${markers()}</defs>${e.map(x => x.path).join('')}</svg>
  ${(await Promise.all(placed.map(node))).join('')}
  ${await legend(legendItems)}`;
  return {name, w: W, h: H, html: page(W, H, body), scale: 2};
}

const start = {cat: 'trigger', shape: 'trigger', icon: 'mic', title: '음성 호출', sub: '시작 · 글레오 AI'};
const end = {cat: 'end', shape: 'end', icon: 'flag', title: '완료', sub: '종료'};

const figures = [
  await visionFigure({
    name: 'vision-son-routine', titleIcon: 'baby', title: '아이를 위한 루틴 · 둘째아들',
    stepItems: [
      ['mic', '말로 부르기', '“둘째아들 차량 루틴 틀어줘~”'],
      ['search', '루틴 찾기', '저장한 ‘둘째아들’ 루틴'],
      ['circle-play', '바로 실행', '조명과 영상이 함께 바뀝니다'],
    ],
    nodes: [
      start,
      {cat: 'light', icon: 'lightbulb', title: '뒷좌석 조명', sub: '앰비언트 · 2열'},
      {cat: 'media', icon: 'monitor-play', title: '핑크퐁 재생', sub: '뒷좌석 디스플레이',
        badges: [{tone: 'info', icon: 'link', text: 'YouTube 연동'}]},
      {cat: 'timing', icon: 'timer', title: '30분 시청', sub: '시간 · 타이머'},
      {cat: 'media', icon: 'circle-stop', title: '영상 끄기', sub: '뒷좌석 디스플레이'},
      end,
    ],
    legendItems: [
      ['mic', '루틴 이름만 말하면 실행'],
      ['link', 'YouTube 같은 앱과 연동'],
      ['timer', '시청 시간도 루틴으로'],
    ],
  }),
  await visionFigure({
    name: 'vision-pet-routine', titleIcon: 'dog', title: '반려동물을 위한 루틴 · 뽀삐',
    stepItems: [
      ['mic', '말로 부르기', '“뽀삐 차에 둘거야”'],
      ['search', '루틴 찾기', '미리 만든 ‘강아지’ 루틴'],
      ['circle-play', '바로 실행', '온도·바람·창문을 맞춥니다'],
    ],
    nodes: [
      start,
      {cat: 'climate', icon: 'thermometer', title: '공조 22°C', sub: '실내 온도 유지'},
      {cat: 'seat', icon: 'fan', title: '뒷좌석 통풍', sub: '시트 · 2열'},
      {cat: 'climate', icon: 'wind', title: '창문 살짝 열기', sub: '환기 · 2열'},
      {cat: 'service', icon: 'smartphone', title: '보호자 알림', sub: '실내 온도 공유',
        badges: [{tone: 'success', icon: 'shield-check', text: '안전 감시'}]},
      end,
    ],
    legendItems: [
      ['search', '미리 만든 루틴을 찾아 실행'],
      ['wind', '창문은 환기할 만큼만'],
      ['smartphone', '실내 상태를 보호자에게 계속 공유'],
    ],
  }),
];

const lines = [];
for (const f of figures) {
  const png = shoot(f, outDir);
  lines.push(`${createHash('sha256').update(readFileSync(png)).digest('hex')}  ${f.name}.png  (${f.w * f.scale}x${f.h * f.scale})`);
}
lines.push(`# @astryxdesign/theme-neutral ${themeVersion}, lucide-react ${lucideVersion}, renderer ${chrome}`);
writeFileSync(join(outDir, 'figures.sha256'), lines.join('\n') + '\n');
console.log(lines.join('\n'));
