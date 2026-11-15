// Shared visual language for routine flows: application figures and Android resources use this file.
// Colours come from the installed Astryx neutral theme (light-dark() pairs in theme.css), icons from the
// theme's own icon library (lucide-react, ISC), so the document and the app cannot drift apart.
import {readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';

const modules = join(dirname(fileURLToPath(import.meta.url)), 'node_modules');
const themeDir = join(modules, '@astryxdesign', 'theme-neutral');
const lucidePackage = join(modules, 'lucide-react');
const lucideDir = join(lucidePackage, 'dist', 'esm', 'icons');

/** Every `--token: light-dark(#light, #dark)` of the neutral theme; the first declaration wins. */
export function neutralTokens() {
  const css = readFileSync(join(themeDir, 'dist', 'theme.css'), 'utf8');
  const tokens = {};
  for (const m of css.matchAll(/(--[a-z0-9-]+)\s*:\s*light-dark\(\s*(#[0-9a-fA-F]{3,8})\s*,\s*(#[0-9a-fA-F]{3,8})\s*\)/g)) {
    if (!(m[1] in tokens)) tokens[m[1]] = {light: m[2].toLowerCase(), dark: m[3].toLowerCase()};
  }
  for (const m of css.matchAll(/(--color-warning|--color-on-warning)\s*:\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    if (!(m[1] in tokens)) tokens[m[1]] = {light: m[2].toLowerCase(), dark: m[2].toLowerCase()};
  }
  return tokens;
}

export const themeVersion = JSON.parse(readFileSync(join(themeDir, 'package.json'), 'utf8')).version;
export const lucideVersion = JSON.parse(readFileSync(join(lucidePackage, 'package.json'), 'utf8')).version;

/** Lucide icon node list, e.g. [['path', {d: ...}], ['circle', {...}]]. */
export async function iconNodes(name) {
  // Some names are aliases (`export {default} from './eighth-notes.mjs'`); follow them to the data module.
  let file = `${name}.mjs`;
  for (let hop = 0; hop < 4; hop++) {
    const alias = readFileSync(join(lucideDir, file), 'utf8').match(/export \{ default \} from '\.\/([a-z0-9-]+\.mjs)'/);
    if (!alias) break;
    file = alias[1];
  }
  const mod = await import(pathToFileURL(join(lucideDir, file)).href);
  return mod.__iconData.node;
}

export async function iconSvg(name, {size = 24, stroke = 'currentColor', width = 2} = {}) {
  const body = (await iconNodes(name)).map(([tag, attrs]) =>
    `<${tag} ${Object.entries(attrs).filter(([k]) => k !== 'key').map(([k, v]) => `${k}="${v}"`).join(' ')}/>`).join('');
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="${stroke}" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
}

/**
 * Node categories. `hue` names an Astryx palette family (background-/icon-/text-<hue> tokens).
 * Korean names are what drivers read; they never show raw enum names.
 */
export const categories = {
  trigger:  {hue: 'orange', label: '시작'},
  logic:    {hue: 'yellow', label: '흐름 제어'},
  timing:   {hue: 'gray',   label: '시간'},
  climate:  {hue: 'blue',   label: '공조'},
  light:    {hue: 'purple', label: '조명'},
  seat:     {hue: 'teal',   label: '시트'},
  route:    {hue: 'green',  label: '길 안내'},
  media:    {hue: 'pink',   label: '음원·미디어'},
  service:  {hue: 'cyan',   label: '앱·서비스'},
  end:      {hue: 'gray',   label: '종료'},
};

/** Icon per node kind / action / trigger. Keys mirror android/core Workflow.kt enums. */
export const icons = {
  kind: {START: 'zap', IF: 'split', WAIT: 'hourglass', TIMER: 'timer', REPEAT: 'repeat', PARALLEL: 'git-fork', JOIN: 'git-merge', END: 'flag'},
  action: {
    NAVIGATE: 'navigation', CANCEL_NAVIGATION: 'navigation-off', PLACE_SEARCH: 'map-pin', WHAT3WORDS_RESOLVE: 'map-pinned',
    SET_HVAC_TARGET: 'thermometer', SET_AMBIENT_BRIGHTNESS: 'lightbulb', SET_AMBIENT_COLOR: 'palette',
    SET_SEAT_HEAT: 'flame', MOVE_SEAT_LIMITED: 'armchair',
    PLAY_LOCAL_AUDIO: 'music', STOP_LOCAL_AUDIO: 'volume-x', MEDIA_CONTROL: 'circle-play',
    OPEN_APP: 'app-window', CALENDAR_READ: 'calendar', HA_CALL: 'house',
  },
  trigger: {MANUAL: 'hand', VOICE: 'mic', SEAT_OCCUPIED: 'armchair', TIME: 'clock', CONDITION_ENTER: 'activity'},
  ui: ['plus', 'sparkles', 'mouse-pointer-click', 'info', 'triangle-alert', 'circle-x', 'circle-check',
    'shield-check', 'undo-2', 'redo-2', 'maximize', 'car', 'list-checks', 'history',
    'settings-2', 'blocks', 'square-pen', 'trash-2', 'copy', 'save', 'flask-conical', 'wand-sparkles', 'circle-stop', 'search'],
  /** App-only controls: tabs, steps, header and list actions. */
  app: ['eye', 'play', 'power', 'chevrons-up', 'workflow', 'lock', 'gauge', 'arrow-right', 'link', 'list-plus', 'pencil', 'chevron-down'],
};

export const actionCategory = {
  NAVIGATE: 'route', CANCEL_NAVIGATION: 'route', PLACE_SEARCH: 'route', WHAT3WORDS_RESOLVE: 'route',
  SET_HVAC_TARGET: 'climate', SET_AMBIENT_BRIGHTNESS: 'light', SET_AMBIENT_COLOR: 'light',
  SET_SEAT_HEAT: 'seat', MOVE_SEAT_LIMITED: 'seat',
  PLAY_LOCAL_AUDIO: 'media', STOP_LOCAL_AUDIO: 'media', MEDIA_CONTROL: 'media',
  OPEN_APP: 'service', CALENDAR_READ: 'service', HA_CALL: 'service',
};
export const kindCategory = {START: 'trigger', IF: 'logic', REPEAT: 'logic', PARALLEL: 'logic', JOIN: 'logic', WAIT: 'timing', TIMER: 'timing', END: 'end'};
