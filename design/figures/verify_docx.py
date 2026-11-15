"""Checks a figure-swapped application DOCX against the revised DOCX it was made from.

PASS means: only the four media parts and document.xml changed; document.xml differs only inside the four
picture drawings and the one figure 3 caption phrase; media equal the evidence PNGs; the PDF has 7 pages and
pages without figures render pixel-identical to the revised PDF; new alt text holds no contact data.

Usage: python design/figures/verify_docx.py <revised.docx> <revised.pdf> <new.docx> <new.pdf>
"""
import hashlib
import re
import sys
import zipfile
from pathlib import Path

import pypdfium2 as pdfium
from PIL import ImageChops

sys.path.insert(0, str(Path(__file__).parent))
from replace_docx_media import CAPTIONS, FIGURES  # noqa: E402

DRAWING = re.compile(r'<w:drawing>.*?</w:drawing>', re.S)


def main(old_docx: Path, old_pdf: Path, new_docx: Path, new_pdf: Path) -> None:
    old, new = zipfile.ZipFile(old_docx), zipfile.ZipFile(new_docx)
    assert new.testzip() is None
    assert old.namelist() == new.namelist(), 'package part list changed'
    media = {part: path for part, path, _ in FIGURES.values()}
    for name in old.namelist():
        if name == 'word/document.xml':
            continue
        if name in media:
            assert new.read(name) == media[name].read_bytes(), name
            assert new.read(name) != old.read(name), f'{name} was not replaced'
        else:
            assert old.read(name) == new.read(name), f'{name} changed'
    a, b = old.read('word/document.xml').decode('utf-8'), new.read('word/document.xml').decode('utf-8')
    for before, after in CAPTIONS:
        a = a.replace(before, after)
    assert DRAWING.sub('', a) == DRAWING.sub('', b), 'document.xml changed outside drawings and the figure 3 caption'
    old_drawings, new_drawings = DRAWING.findall(a), DRAWING.findall(b)
    assert len(old_drawings) == len(new_drawings)
    changed = [i for i, (x, y) in enumerate(zip(old_drawings, new_drawings)) if x != y]
    assert [re.search(r'r:embed="(rId\d+)"', new_drawings[i]).group(1) for i in changed] == list(FIGURES), changed
    assert 'srcRect' not in ''.join(new_drawings[i] for i in changed)
    alt = ' '.join(t for _, _, t in FIGURES.values())
    assert not re.search(r'[\w.+-]+@[\w.-]+\.\w+|https?://|01[016789][ -]?\d{3,4}[ -]?\d{4}|[A-Za-z]:\\', alt)

    po, pn = pdfium.PdfDocument(old_pdf), pdfium.PdfDocument(new_pdf)
    assert len(pn) == len(po) == 7, (len(po), len(pn))
    same = []
    for i in range(7):
        x, y = po[i].render(scale=1.2).to_pil().convert('RGB'), pn[i].render(scale=1.2).to_pil().convert('RGB')
        if x.size == y.size and ImageChops.difference(x, y).getbbox() is None:
            same.append(i + 1)
    for page in (1, 6, 7):
        assert page in same, f'page {page} differs'
    print('PASS: 4 media + drawings + 1 caption changed; other parts byte-identical; 7 pages;',
          f'pixel-identical pages {same}; alt text has no contact data.')
    print('new docx sha256', hashlib.sha256(new_docx.read_bytes()).hexdigest())


if __name__ == '__main__':
    main(*map(Path, sys.argv[1:5]))
