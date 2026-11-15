"""Swap the four application figures inside a copy of the revised application DOCX.

Only word/media/image1..4.png and their drawing XML (size, crop, alt text) change, plus one caption
phrase for figure 3 whose source screen changed. Every other package part is copied byte for byte.
The DOCX holds personal data: keep input and output under .local/ (Git-ignored).

Usage: python design/figures/replace_docx_media.py <revised.docx> <output.docx>
"""
import hashlib
import re
import struct
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
FIG = REPO / 'evidence' / 'application-figures-2026-11-15'

# rId -> (media part, new png, new alt text)
FIGURES = {
    'rId9': ('word/media/image1.png', FIG / 'fig1-winter-draft.png',
             '사용 시나리오 예시. 노드 흐름도: 주차 후 시작 → 외기 5°C 이하 조건. 충족하면 공조 목표 23°C를 거치고, '
             '미충족이면 공조를 건너뛴다. 이어서 운전석 등받이(실행마다 확인), 조명 30%(제안값), 완료 순서다.'),
    'rId10': ('word/media/image2.png', FIG / 'fig2-light-edit.png',
              '사용 시나리오 예시. 조명 30% 노드를 선택해 AI에게 “10분 뒤 밝기를 20%로 낮춰줘”라고 요청하면, '
              '수정 후 흐름이 조명 30% → 10분 대기(추가) → 조명 20%(추가) → 완료로 바뀐다. 공조와 시트 단계는 그대로다.'),
    'rId11': ('word/media/image3.png', FIG / 'fig3-commute-screen.png',
              '평일 아침 출근 예시. 제출 APK를 공식 Pleos 에뮬레이터에서 실행한 실제 화면의 흐름 부분: '
              '운전석 탑승 → 회사로 길 안내 → 외기 온도 ≤ 5°C → 운전석 열선 2단계 → 완료.'),
    'rId12': ('word/media/image4.png', FIG / 'fig4-driving-restriction.png',
              '실제 Pleos 주행 제한 화면의 중앙 안내(기어 D, 15 km/h). 주행 중에는 사용할 수 없습니다.'),
}
CAPTIONS = [('평일 아침 출근 예시 · 실제 편집 화면 발췌.', '평일 아침 출근 예시 · 제출 APK 실제 화면 발췌.')]


def png_size(data: bytes) -> tuple[int, int]:
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    return struct.unpack('>II', data[16:24])


def xml_attr(text: str) -> str:
    return text.replace('&', '&amp;').replace('"', '&quot;').replace('<', '&lt;').replace('>', '&gt;')


def main(src: Path, out: Path) -> None:
    pngs = {rid: path.read_bytes() for rid, (_, path, _) in FIGURES.items()}
    with zipfile.ZipFile(src) as zin:
        doc = zin.read('word/document.xml').decode('utf-8')
        rels = zin.read('word/_rels/document.xml.rels').decode('utf-8')
        for rid, (part, _, _) in FIGURES.items():
            assert f'Id="{rid}"' in rels and f'Target="{part[5:]}"' in rels, rid

        def swap(match: re.Match) -> str:
            block = match.group(0)
            rid = re.search(r'r:embed="(rId\d+)"', block)
            if not rid or rid.group(1) not in FIGURES:
                return block
            part, path, alt = FIGURES[rid.group(1)]
            w, h = png_size(pngs[rid.group(1)])
            cx = int(re.search(r'<wp:extent cx="(\d+)"', block).group(1))
            cy = round(cx * h / w)
            block = re.sub(r'<wp:extent cx="\d+" cy="\d+"/>', f'<wp:extent cx="{cx}" cy="{cy}"/>', block)
            block = re.sub(r'<a:ext cx="\d+" cy="\d+"/>', f'<a:ext cx="{cx}" cy="{cy}"/>', block)
            block = re.sub(r'<a:srcRect[^>]*/>', '', block)
            block = re.sub(r'(<wp:docPr [^>]*descr=")[^"]*(")', lambda m: m.group(1) + xml_attr(alt) + m.group(2), block)
            block = re.sub(r'(<pic:cNvPr id="0" name=")[^"]*(")', lambda m: m.group(1) + path.name + m.group(2), block)
            print(f'{rid.group(1)} {part}: {w}x{h}px -> {cx / 360000:.2f} x {cy / 360000:.2f} cm')
            return block

        new_doc, count = re.subn(r'<w:drawing>.*?</w:drawing>', swap, doc, flags=re.S)
        for old, new in CAPTIONS:
            assert new_doc.count(old) == 1, old
            new_doc = new_doc.replace(old, new)
        out.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(out, 'w') as zout:
            for info in zin.infolist():
                data = zin.read(info.filename)
                if info.filename == 'word/document.xml':
                    data = new_doc.encode('utf-8')
                for rid, (part, _, _) in FIGURES.items():
                    if info.filename == part:
                        data = pngs[rid]
                zout.writestr(info, data, compress_type=info.compress_type)
    print('sha256', hashlib.sha256(out.read_bytes()).hexdigest(), out)


if __name__ == '__main__':
    main(Path(sys.argv[1]), Path(sys.argv[2]))
