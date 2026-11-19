"""Generate fictional sample invoices (PDF + ground-truth JSON) for the invoice-agent playground."""
import json
import os
import random
import subprocess
import sys
from decimal import Decimal, ROUND_HALF_UP

from PIL import Image, ImageFilter
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfgen import canvas

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
random.seed(42)

TWO = Decimal("0.01")


def money(x):
    return Decimal(x).quantize(TWO, rounding=ROUND_HALF_UP)


def fmt_de(d):
    s = f"{d:,.2f}"
    return s.replace(",", "X").replace(".", ",").replace("X", ".") + " €"


def fmt_en(d):
    return f"€{d:,.2f}"


INVOICES = [
    {
        "file": "01_clean_de_single_vat.pdf",
        "lang": "de",
        "seller": {"name": "Muster Bürobedarf GmbH", "street": "Beispielstraße 12", "city": "70173 Stuttgart",
                   "vat_id": "DE123456789", "iban": "DE02 1203 0000 0000 2020 51"},
        "buyer": {"name": "Testkunde Logistik AG", "street": "Hafenweg 3", "city": "20457 Hamburg",
                  "buyer_reference": "04011000-12345-34"},
        "invoice_number": "RE-2026-1001", "invoice_date": "2026-11-02", "due_date": "2026-12-02",
        "order_reference": "PO-88231",
        "lines": [("Kopierpapier A4, 80 g (Karton à 2.500 Blatt)", 4, "29.90", 19),
                  ("Toner schwarz TN-2420", 2, "54.50", 19),
                  ("Ordner breit, blau", 25, "2.35", 19)],
    },
    {
        "file": "02_clean_de_mixed_vat.pdf",
        "lang": "de",
        "seller": {"name": "Beispiel Kantinenservice GmbH", "street": "Lindenallee 7", "city": "68159 Mannheim",
                   "vat_id": "DE987654321", "iban": "DE89 3704 0044 0532 0130 00"},
        "buyer": {"name": "Musterwerk Maschinenbau KG", "street": "Industriestraße 40", "city": "69469 Weinheim",
                  "buyer_reference": None},
        "invoice_number": "2026/0457", "invoice_date": "2026-11-05", "due_date": "2026-11-19",
        "order_reference": None,
        "lines": [("Mittagessen Kantine (Portionen)", 120, "6.20", 7),
                  ("Kaffee & Getränke Besprechung", 1, "84.00", 19),
                  ("Servicepauschale Catering", 1, "150.00", 19)],
    },
    {
        "file": "03_clean_en_reverse_charge.pdf",
        "lang": "en",
        "seller": {"name": "Example Cloud Services B.V.", "street": "Voorbeeldstraat 21", "city": "1011 AB Amsterdam, NL",
                   "vat_id": "NL000099998B57", "iban": "NL91 ABNA 0417 1643 00"},
        "buyer": {"name": "Sample Software GmbH", "street": "Musterplatz 1", "city": "80331 München",
                  "vat_id": "DE111222333", "buyer_reference": None},
        "invoice_number": "INV-000873", "invoice_date": "2026-10-31", "due_date": "2026-11-30",
        "order_reference": "Contract C-2025-17",
        "lines": [("Managed Kubernetes cluster – October 2026", 1, "1450.00", 0),
                  ("Object storage (TB-month)", 12, "21.50", 0),
                  ("Premium support", 1, "300.00", 0)],
        "note": "Reverse charge: VAT to be accounted for by the recipient (Art. 196 VAT Directive).",
    },
    {
        "file": "04_error_wrong_total.pdf",
        "lang": "de",
        "seller": {"name": "Muster Elektro Großhandel GmbH", "street": "Am Beispielpark 5", "city": "90402 Nürnberg",
                   "vat_id": "DE555666777", "iban": "DE44 5001 0517 5407 3249 31"},
        "buyer": {"name": "Testkunde Logistik AG", "street": "Hafenweg 3", "city": "20457 Hamburg",
                  "buyer_reference": "04011000-12345-34"},
        "invoice_number": "ME-77412", "invoice_date": "2026-11-10", "due_date": "2026-12-10",
        "order_reference": "PO-88290",
        "lines": [("Kabel NYM-J 3x1,5 (100 m Ring)", 6, "48.90", 19),
                  ("Steckdosenleiste 6-fach", 15, "12.40", 19)],
        # deliberate error: printed gross total is off by 10,00 €
        "gross_override_delta": "10.00",
    },
    {
        "file": "05_error_missing_vat_id.pdf",
        "lang": "de",
        "seller": {"name": "Beispiel Reinigung Schmidt", "street": "Gartenweg 2", "city": "75328 Schömberg",
                   "vat_id": None, "iban": "DE75 5121 0800 1245 1261 99"},
        "buyer": {"name": "Musterwerk Maschinenbau KG", "street": "Industriestraße 40", "city": "69469 Weinheim",
                  "buyer_reference": None},
        "invoice_number": "17", "invoice_date": "2026-11-12", "due_date": None,
        "order_reference": None,
        "lines": [("Unterhaltsreinigung Büro Oktober", 1, "680.00", 19),
                  ("Fensterreinigung", 1, "120.00", 19)],
    },
]


def compute(inv):
    lines = []
    tax = {}
    net_total = Decimal("0")
    for desc, qty, price, rate in inv["lines"]:
        net = money(Decimal(price) * qty)
        lines.append({"description": desc, "quantity": qty, "unit_price": str(money(Decimal(price))),
                      "net_amount": str(net), "vat_rate": rate})
        net_total += net
        tax[rate] = tax.get(rate, Decimal("0")) + net
    breakdown = [{"vat_rate": r, "taxable_amount": str(money(b)), "vat_amount": str(money(b * r / 100))}
                 for r, b in sorted(tax.items())]
    vat_total = sum(Decimal(b["vat_amount"]) for b in breakdown)
    gross = money(net_total + vat_total)
    printed_gross = gross + Decimal(inv.get("gross_override_delta", "0"))
    return lines, breakdown, money(net_total), money(vat_total), gross, printed_gross


def draw(inv, path):
    de = inv["lang"] == "de"
    f = fmt_de if de else fmt_en
    lines, breakdown, net, vat, gross, printed_gross = compute(inv)
    c = canvas.Canvas(path, pagesize=A4)
    w, h = A4
    c.setFont("Helvetica-Oblique", 8)
    c.setFillGray(0.4)
    c.drawString(20 * mm, h - 10 * mm, "MUSTERRECHNUNG – fiktive Testdaten / SAMPLE INVOICE – fictional test data")
    c.setFillGray(0)
    s = inv["seller"]
    c.setFont("Helvetica-Bold", 14)
    c.drawString(20 * mm, h - 25 * mm, s["name"])
    c.setFont("Helvetica", 9)
    c.drawString(20 * mm, h - 31 * mm, f"{s['street']} · {s['city']}")
    b = inv["buyer"]
    y = h - 50 * mm
    c.setFont("Helvetica", 10)
    for t in (b["name"], b["street"], b["city"]):
        c.drawString(20 * mm, y, t)
        y -= 5 * mm
    x2 = 125 * mm
    y2 = h - 50 * mm
    meta = [("Rechnungsnummer" if de else "Invoice no.", inv["invoice_number"]),
            ("Rechnungsdatum" if de else "Invoice date", inv["invoice_date"]),
            ("Fällig am" if de else "Due date", inv["due_date"] or "–")]
    if inv.get("order_reference"):
        meta.append(("Bestellnummer" if de else "Order ref.", inv["order_reference"]))
    if b.get("buyer_reference"):
        meta.append(("Leitweg-ID", b["buyer_reference"]))
    if b.get("vat_id"):
        meta.append(("Kunden-USt-IdNr." if de else "Customer VAT ID", b["vat_id"]))
    for k, v in meta:
        c.setFont("Helvetica", 9)
        c.drawString(x2, y2, k)
        c.setFont("Helvetica-Bold", 9)
        c.drawRightString(190 * mm, y2, v)
        y2 -= 5 * mm
    c.setFont("Helvetica-Bold", 16)
    c.drawString(20 * mm, h - 90 * mm, "Rechnung" if de else "Invoice")
    y = h - 102 * mm
    head = ["Pos.", "Beschreibung" if de else "Description", "Menge" if de else "Qty",
            "Einzelpreis" if de else "Unit price", "USt." if de else "VAT", "Betrag" if de else "Amount"]
    xs = [20, 30, 120, 140, 162, 190]
    c.setFont("Helvetica-Bold", 9)
    for i, t in enumerate(head):
        (c.drawRightString if i >= 2 else c.drawString)(xs[i] * mm, y, t)
    c.line(20 * mm, y - 2 * mm, 190 * mm, y - 2 * mm)
    y -= 8 * mm
    c.setFont("Helvetica", 9)
    for i, l in enumerate(lines, 1):
        row = [str(i), l["description"], str(l["quantity"]), f(Decimal(l["unit_price"])), f"{l['vat_rate']} %",
               f(Decimal(l["net_amount"]))]
        for j, t in enumerate(row):
            (c.drawRightString if j >= 2 else c.drawString)(xs[j] * mm, y, t)
        y -= 6 * mm
    c.line(120 * mm, y, 190 * mm, y)
    y -= 6 * mm
    c.drawString(120 * mm, y, "Nettobetrag" if de else "Net total")
    c.drawRightString(190 * mm, y, f(net))
    for bd in breakdown:
        y -= 5 * mm
        label = (f"zzgl. {bd['vat_rate']} % USt. auf {f(Decimal(bd['taxable_amount']))}" if de
                 else f"VAT {bd['vat_rate']} % on {f(Decimal(bd['taxable_amount']))}")
        c.drawString(120 * mm, y, label[:40])
        c.drawRightString(190 * mm, y, f(Decimal(bd["vat_amount"])))
    y -= 7 * mm
    c.setFont("Helvetica-Bold", 10)
    c.drawString(120 * mm, y, "Gesamtbetrag" if de else "Total due")
    c.drawRightString(190 * mm, y, f(printed_gross))
    c.setFont("Helvetica", 8)
    y = 40 * mm
    if inv.get("note"):
        c.drawString(20 * mm, y + 10 * mm, inv["note"])
    pay = ("Zahlbar ohne Abzug bis " + (inv["due_date"] or "sofort") if de
           else "Payable without deduction by " + (inv["due_date"] or "on receipt"))
    c.drawString(20 * mm, y, pay)
    foot = f"{s['name']} · IBAN {s['iban']}"
    if s.get("vat_id"):
        foot += f" · USt-IdNr. {s['vat_id']}" if de else f" · VAT ID {s['vat_id']}"
    c.drawString(20 * mm, y - 5 * mm, foot)
    c.save()
    return lines, breakdown, net, vat, gross, printed_gross


def make_scan(src_pdf, dst_pdf):
    base = dst_pdf[:-4]
    subprocess.run(["pdftoppm", "-r", "110", "-png", "-singlefile", src_pdf, base], check=True)
    img = Image.open(base + ".png").convert("L")
    img = img.rotate(1.3, expand=True, fillcolor=255).filter(ImageFilter.GaussianBlur(0.6))
    px = img.load()
    for _ in range(img.width * img.height // 120):
        x, y = random.randrange(img.width), random.randrange(img.height)
        px[x, y] = random.choice((90, 160, 200))
    img.save(dst_pdf, "PDF", resolution=110)
    os.remove(base + ".png")


truth = []
for inv in INVOICES:
    path = os.path.join(OUT, inv["file"])
    lines, breakdown, net, vat, gross, printed_gross = draw(inv, path)
    expected_errors = []
    if printed_gross != gross:
        expected_errors.append(f"printed total {printed_gross} != computed total {gross}")
    if not inv["seller"].get("vat_id"):
        expected_errors.append("seller VAT ID / tax number missing (required on German invoices)")
    if not inv.get("due_date"):
        expected_errors.append("no due date / payment terms")
    truth.append({
        "file": inv["file"], "language": inv["lang"], "seller": inv["seller"], "buyer": inv["buyer"],
        "invoice_number": inv["invoice_number"], "invoice_date": inv["invoice_date"],
        "due_date": inv["due_date"], "order_reference": inv.get("order_reference"), "currency": "EUR",
        "lines": lines, "vat_breakdown": breakdown, "net_total": str(net), "vat_total": str(vat),
        "gross_total_computed": str(gross), "gross_total_printed": str(printed_gross),
        "note": inv.get("note"), "expected_validation_errors": expected_errors,
    })

make_scan(os.path.join(OUT, "01_clean_de_single_vat.pdf"), os.path.join(OUT, "06_scanned_copy_of_01.pdf"))
t = dict(truth[0])
t["file"] = "06_scanned_copy_of_01.pdf"
t["note"] = "Image-only scan of 01 (rotated, blurred, noise): needs OCR or a vision model."
truth.append(t)

with open(os.path.join(OUT, "ground_truth.json"), "w", encoding="utf-8") as fh:
    json.dump(truth, fh, ensure_ascii=False, indent=2)
print("\n".join(sorted(os.listdir(OUT))))
