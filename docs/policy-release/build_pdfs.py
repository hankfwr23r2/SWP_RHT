"""Build the three customer policies."""
from pathlib import Path
import html
import markdown
from weasyprint import HTML

ROOT = Path(__file__).resolve().parent
DOCS = [
    ("transport", "dieu-khoan-van-chuyen", "Điều khoản vận chuyển", "customer"),
    ("liability", "dieu-khoan-trach-nhiem-han-che", "Điều khoản Trách nhiệm Hạn chế", "customer"),
    ("incident_cost", "chinh-sach-chi-phi-su-co", "Chính sách chi phí khi có sự cố", "customer"),
]
CSS = """
@font-face { font-family: Policy; src: url('file:///usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'); }
@font-face { font-family: Policy; font-weight: 700; src: url('file:///usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'); }
@page {
  size: A4; margin: 19mm 17mm 20mm;
  @top-left { content: 'RHT  /  VẬN CHUYỂN NGỰA ĐUA'; font-family: Policy; font-size: 8pt; color: #64748b; }
  @bottom-left { content: 'Phiên bản 1.0  ·  17/11/2026'; font-family: Policy; font-size: 8pt; color: #64748b; }
  @bottom-right { content: counter(page) ' / ' counter(pages); font-family: Policy; font-size: 8pt; color: #64748b; }
}
html { font-family: Policy, sans-serif; font-size: 10pt; color: #253246; line-height: 1.57; }
body { margin: 0; }
h1 { color: #172c43; font-size: 24pt; line-height: 1.22; margin: 2mm 0 5mm; padding-bottom: 5mm; border-bottom: 2pt solid #c87c35; }
h1 + p { font-size: 8.5pt; color: #6a7684; margin-bottom: 7mm; }
h2 { font-size: 12.4pt; color: #172c43; line-height: 1.35; margin: 6mm 0 2.5mm; break-after: avoid; }
h3 { font-size: 11pt; break-after: avoid; }
p { margin: 0 0 3mm; orphans: 3; widows: 3; }
strong { color: #172c43; }
a { color: #995620; text-decoration: underline; overflow-wrap: anywhere; }
code { font-family: Policy; font-size: 8.5pt; background: #f0f3f6; overflow-wrap: anywhere; }
table { width: 100%; border-collapse: collapse; margin: 3mm 0 5mm; table-layout: fixed; font-size: 8.3pt; line-height: 1.5; }
th { text-align: left; color: #172c43; background: #edf2f6; font-weight: 700; }
th, td { border: .5pt solid #d4dde5; padding: 2.5mm; vertical-align: top; overflow-wrap: anywhere; }
tr { break-inside: avoid; }
thead { display: table-header-group; }
li { padding-left: 1mm; margin: 0 0 2mm; orphans: 3; widows: 3; }
ul, ol { padding-left: 5mm; }
.classification { color: #995620; font-size: 8pt; letter-spacing: .4pt; font-weight: 700; margin-bottom: 4mm; }
"""


def main():
    (ROOT / "pdf").mkdir(exist_ok=True)
    for key, stem, title, audience in DOCS:
        source = ROOT / "source" / f"{stem}.md"
        body = markdown.markdown(source.read_text(), extensions=["tables", "fenced_code"])
        classification = "TÀI LIỆU NỘI BỘ · BÀN GIAO DEV" if audience == "internal" else "ĐIỀU KHOẢN VÀ CHÍNH SÁCH"
        page = f'''<!doctype html><html lang="vi"><head><meta charset="utf-8">
        <title>{html.escape(title)}</title><meta name="author" content="Vận chuyển Ngựa đua">
        <style>{CSS}</style></head><body><div class="classification">{classification}</div>{body}</body></html>'''
        rendered = HTML(string=page, base_url=str(ROOT)).render()
        target = ROOT / "pdf" / f"{stem}.pdf"
        rendered.write_pdf(target)
        print(f"{target.name}: {len(rendered.pages)} pages")


if __name__ == "__main__":
    main()
