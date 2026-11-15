# Astryx 적용 기록

2026-11-15 KST. 사용자 요청으로 Meta의 Astryx를 설치해 디자인 기준으로 삼았다. 이 폴더는 앱에 들어가지 않는 디자인 참조와 재현 가능한 기획서 그림 생성 도구다.

## 설치한 것

| 항목 | 버전·위치 | 비고 |
|---|---|---|
| Claude Code 플러그인 | `claude plugin marketplace add meta/astryx` → `claude plugin install astryx@astryx` (사용자 범위, 플러그인 0.1.0, 저장소 커밋 `5ac1267`) | 스킬은 `astryx` CLI로 문서·컴포넌트를 찾으라는 안내 하나뿐이다 |
| `@astryxdesign/core`, `@astryxdesign/theme-neutral`, `@astryxdesign/cli` | 0.7.16 (npm latest), MIT | `design/package.json`. postinstall은 `astryx init` 안내 출력뿐이라 실행하지 않았다 |
| `lucide-react` | 1.73.0, ISC | theme-neutral의 의존성. neutral 테마의 아이콘 레지스트리가 Lucide다(`theme-neutral/dist/icons.mjs`) |
| `react`, `react-dom`, `@stylexjs/stylex` | 19.3.1, 0.19.2 | Astryx peer 의존성. 그림 생성에는 CSS 토큰만 쓴다 |

## 확인한 사실

- Astryx는 React 19 + StyleX 웹 디자인 시스템이다. Android용 바인딩은 아직 없다(`packages/native/README.md`: React Native와 Android 바인딩은 미구현, 실험 패키지는 canary 전용). 그래서 앱(Compose)에는 컴포넌트가 아니라 **토큰·아이콘·형태 규칙**을 옮긴다.
- 색은 모두 `light-dark()` 쌍이다. 밝은/어두운 모드를 시스템 설정으로 자동 전환한다는 뜻이며, Pleos 디자인 가이드의 주간·야간 자동 대응과 맞는다.
- 테마 범위는 `[data-astryx-theme="neutral"]`, 모드는 `html[data-theme="light|dark"]`의 `color-scheme`으로 정한다.
- 아이콘은 의미 이름 레지스트리다. neutral은 Lucide를 쓰고, 실험 패키지의 Android 기본값은 Material Symbols다(`packages/native/src/icons/defaults.ts`). 테마가 아이콘을 정하면 테마 쪽이 우선하므로 이 프로젝트는 neutral의 Lucide를 그림과 앱에 함께 쓴다.
- neutral 본문 글꼴은 Figtree(Google Fonts, `theme-neutral/fonts.css`)다. 한글 글리프가 없어 그림은 Figtree → Noto Sans KR → 맑은 고딕 순으로 지정했다. 앱은 기기 한글 시스템 글꼴을 유지한다.

## 사용한 토큰 (neutral 0.7.16 `dist/theme.css`, 밝은 / 어두운)

| 역할 | 토큰 | 값 |
|---|---|---|
| 바탕 | `--color-background-body` | `#f1f1f1` / `#1b1b1b` |
| 면 | `--color-background-surface` | `#ffffff` / `#262626` |
| 본문 / 보조 글자 | `--color-text-primary` / `--color-text-secondary` | `#000000`·`#474747` / `#ffffff`·`#9e9e9e` |
| 강조 | `--color-accent` / `--color-on-accent` | `#1b1b1b`·`#ffffff` / `#f1f1f1`·`#111111` |
| 테두리 | `--color-border` / `--color-border-emphasized` | `#00000014`·`#d4d4d4` / `#ffffff1a`·`#525252` |
| 상태 | `--color-success` · `--color-error` · `--color-info` · `--color-warning` | `#198100`·`#c9303a`·`#0074e2`·`#ffce2f` / `#64af4c`·`#ff705d`·`#6d9cfe`·`#ffce2f` |
| 반경 | `--radius-inner` · `--radius-element` · `--radius-container` · `--radius-page` | 6 · 10 · 12 · 28px |

`--color-warning`은 단일값이며 글자색 대비가 낮아 채움(`-muted`)에만 쓴다. 상태 글자는 `--color-on-<status>-muted`를 쓴다.

## 흐름 시각 언어

[flow-visuals.mjs](flow-visuals.mjs)가 그림과 앱 리소스의 단일 기준이다. 노드는 분류 색 정사각형(시작은 왼쪽이 둥근 D형, 종료는 원) + 아래 한국어 제목·보조 설명 + 배지다. 연결은 베지어 곡선과 화살표, 포트 라벨(충족/미충족)이다.

| 분류 | 팔레트 | 아이콘(Lucide) |
|---|---|---|
| 시작 | orange | `zap` |
| 흐름 제어(조건·반복·병렬·합류) | yellow | `split`, `repeat`, `git-fork`, `git-merge` |
| 시간(대기·타이머) | gray | `hourglass`, `timer` |
| 공조 | blue | `thermometer` |
| 조명(밝기·색상) | purple | `lightbulb`, `palette` |
| 시트(이동·열선) | teal | `armchair`, `flame` |
| 길 안내·장소 | green | `navigation`, `navigation-off`, `map-pin`, `map-pinned` |
| 음원·미디어 | pink | `music`, `volume-x`, `circle-play` |
| 앱·일정·스마트홈 | cyan | `app-window`, `calendar`, `house` |
| 종료 | gray | `flag` |

## 기획서 그림 재현

```powershell
cd design
npm ci
node figures/render.mjs        # evidence/application-figures-2026-11-15/fig1~4.png + figures.sha256
python figures/replace_docx_media.py <수정본.docx> <출력.docx>
powershell -File figures/render-word.ps1 -InputDocx <출력.docx 절대경로> -OutputPdf <출력.pdf 절대경로>
python figures/verify_docx.py <수정본.docx> <수정본.pdf> <출력.docx> <출력.pdf>
```

Python은 Pillow·pypdfium2가 있는 환경에서 실행한다. 그림 3·4는 에뮬레이터 원본 픽셀을 그대로 잘라 틀만 씌운다. 화면을 새로 그리지 않는다.
