"""Tải dữ liệu cho app Quran & Solah vào thư mục qdata/ (chạy trên GitHub Actions lúc đóng gói)."""
import json, os, re, sys, time, urllib.request, xml.etree.ElementTree as ET

UA = {"User-Agent": "Mozilla/5.0 (QuranSolah build)"}
os.makedirs("qdata", exist_ok=True)

def get(url, tries=5):
    for k in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=90) as r:
                return r.read()
        except Exception as e:
            print(f"thử lại ({k + 1}) {url}: {e}", flush=True); time.sleep(6)
    print(f"::error::Không tải được {url}"); sys.exit(1)

def save(name, data): open(os.path.join("qdata", name), "wb").write(data); print("ok", name, len(data), flush=True)

save("quran.txt", get("https://tanzil.net/pub/download/index.php?marks=true&sajdah=true&rub=false&tatweel=true&quranType=uthmani&outType=txt-2&agree=true"))
n = sum(1 for l in open("qdata/quran.txt", encoding="utf-8") if l.strip() and not l.startswith("#"))
if n != 6236: print(f"::error::quran.txt có {n} dòng"); sys.exit(1)

root = ET.fromstring(get("https://tanzil.net/res/text/metadata/quran-data.xml"))
rows = ["\t".join([s.get("index"), s.get("ayas"), s.get("name"), s.get("tname"), s.get("ename"), s.get("type")])
        for s in root.find("suras").findall("sura")]
if len(rows) != 114: print(f"::error::suras {len(rows)}"); sys.exit(1)
save("suras.tsv", ("\n".join(rows) + "\n").encode())

save("AmiriQuran.ttf", get("https://raw.githubusercontent.com/google/fonts/main/ofl/amiriquran/AmiriQuran-Regular.ttf"))

out = []
for i in range(1, 115):
    d = json.loads(get(f"https://quranenc.com/api/v1/translation/sura/vietnamese_rwwad/{i}"))
    for r in d["result"]:
        t = re.sub(r"\s+", " ", (r.get("translation") or "")).strip()
        out.append(f'{r["sura"]}|{r["aya"]}|{t}')
if len(out) != 6236: print(f"::error::vi.txt có {len(out)} dòng"); sys.exit(1)
save("vi.txt", ("\n".join(out) + "\n").encode())

