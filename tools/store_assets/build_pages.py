#!/usr/bin/env python3
"""Builds HTML pages for the Play Store graphics (one page per image), from the app's real strings and questions.

Usage: python3 tools/store_assets/build_pages.py <out_dir> <fonts_dir>
Then:  node tools/store_assets/capture.js <out_dir>   (renders every page to PNG with Chromium)
"""
import html
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(sys.argv[1])
FONTS = Path(sys.argv[2])
LANGS = ("ar", "fr", "en")
RES = {"ar": "values", "fr": "values-fr", "en": "values-en"}
LABELS = {"ar": ["أ", "ب", "ج", "د"], "fr": list("ABCD"), "en": list("ABCD")}

# ---------------------------------------------------------------- app data

def strings(lang):
    tree = ET.parse(ROOT / f"app/src/main/res/{RES[lang]}/strings.xml")
    return {e.get("name"): (e.text or "").replace("\\'", "'") for e in tree.getroot()}

def fmt(s, *args):
    for i, a in enumerate(args, 1):
        s = re.sub(rf"%{i}\$[sd]", str(a), s)
    return s.replace("%%", "%")

def prize(lang, n):
    s = f"{n:,}"
    return s.replace(",", " ") if lang == "fr" else s

BANK = {l: {q["id"]: q for q in json.loads((ROOT / f"app/src/main/assets/questions_{l}.json").read_text())} for l in LANGS}
S = {l: strings(l) for l in LANGS}
e = html.escape

CAPTIONS = {
    "en": ["15 questions to a million", "4 lifelines to help you", "1,000+ questions, 10 topics",
           "Challenge your friends with a code", "Climb the world ranking", "Learn with every answer",
           "English, French or Arabic", "Become a millionaire"],
    "fr": ["15 questions vers le million", "4 jokers pour vous aider", "1000+ questions, 10 thèmes",
           "Défiez vos amis avec un code", "Grimpez au classement mondial", "Apprenez à chaque réponse",
           "Français, arabe ou anglais", "Devenez millionnaire"],
    "ar": ["15 سؤالاً تفصلك عن المليون", "4 وسائل مساعدة", "أكثر من 1000 سؤال في 10 مواضيع",
           "تحدَّ أصدقاءك برمز", "اصعد في الترتيب العالمي", "تعلّم مع كل إجابة",
           "عربي، فرنسي، إنجليزي", "كن مليونيراً"],
}
FEATURE = {
    "en": ("15 questions. 1 million.", "1,000+ questions · 10 topics · 3 languages"),
    "fr": ("15 questions. 1 million.", "1000+ questions · 10 thèmes · 3 langues"),
    "ar": ("15 سؤالاً. مليون واحد.", "أكثر من 1000 سؤال · 10 مواضيع · 3 لغات"),
}

# ---------------------------------------------------------------- CSS (values from ui/theme/Theme.kt and the composables)

CSS = """
@font-face{font-family:Roboto;font-weight:400;src:url(FONTS/roboto/files/roboto-latin-400-normal.woff2)}
@font-face{font-family:Roboto;font-weight:500;src:url(FONTS/roboto/files/roboto-latin-500-normal.woff2)}
@font-face{font-family:Roboto;font-weight:700;src:url(FONTS/roboto/files/roboto-latin-700-normal.woff2)}
@font-face{font-family:Roboto;font-weight:900;src:url(FONTS/roboto/files/roboto-latin-900-normal.woff2)}
@font-face{font-family:NotoArabic;font-weight:400;src:url(FONTS/noto-sans-arabic/files/noto-sans-arabic-arabic-400-normal.woff2)}
@font-face{font-family:NotoArabic;font-weight:700;src:url(FONTS/noto-sans-arabic/files/noto-sans-arabic-arabic-700-normal.woff2)}
@font-face{font-family:NotoArabic;font-weight:900;src:url(FONTS/noto-sans-arabic/files/noto-sans-arabic-arabic-900-normal.woff2)}
:root{--night:#0A0E33;--deep:#1B1464;--gold:#FFC107;--goldl:#FFE082;--opt:#13185A;--optb:#5C6BC0;
 --sel:#FF9800;--ok:#2E7D32;--bad:#C62828;--sec:rgba(255,255,255,.8)}
*{box-sizing:border-box;margin:0;padding:0}
body{width:var(--cw);height:var(--ch);overflow:hidden;font-family:Roboto,NotoArabic,'Noto Color Emoji',sans-serif;color:#fff;
 background:radial-gradient(circle at 20% 10%,rgba(255,193,7,.18),transparent 35%),
 radial-gradient(circle at 85% 60%,rgba(92,107,192,.35),transparent 40%),linear-gradient(160deg,#2a1f8a,#1B1464 40%,#0A0E33)}
.sparkle{position:absolute;border-radius:50%;background:radial-gradient(circle,#FFE082,transparent 70%);opacity:.7}
.caption{position:absolute;left:0;right:0;text-align:center;font-weight:900;color:#fff;letter-spacing:-.5px;
 text-shadow:0 4px 18px rgba(0,0,0,.45);padding:0 60px;line-height:1.15}
.caption b{color:var(--gold)}
.device{position:absolute;left:50%;transform:translateX(-50%);background:#05061a;
 box-shadow:0 30px 80px rgba(0,0,0,.6),0 0 0 3px #2c2f5c inset}
.device .screen-wrap{position:absolute;overflow:hidden;background:#000}
.screen{position:absolute;top:0;left:0;transform-origin:0 0;overflow:hidden;
 background:linear-gradient(var(--deep),var(--night));font-size:16px;line-height:1.35}
.status{height:30px;display:flex;justify-content:space-between;align-items:center;padding:0 22px;font-size:13px;font-weight:500;opacity:.95}
.col{display:flex;flex-direction:column}
.row{display:flex;align-items:center}
.pad{padding:12px 16px}
.gap14>*+*{margin-top:14px}.gap10>*+*{margin-top:10px}.gap20>*+*{margin-top:20px}
.sec{color:var(--sec)} .gold{color:var(--gold)} .bold{font-weight:700}
.t-label{font-size:14px;font-weight:500}.t-title{font-size:22px}.t-hl{font-size:32px}.t-body-s{font-size:13px}
.qcard{background:var(--opt);border:1.5px solid var(--gold);border-radius:24px;min-height:120px;padding:20px;
 display:flex;align-items:center;justify-content:center;text-align:center;font-size:22px;line-height:28px}
.opt{border:2px solid var(--optb);background:var(--opt);border-radius:999px;padding:14px 20px;display:flex;align-items:center}
.opt .lab{color:var(--gold);font-weight:700;margin-inline-end:12px}
.opt.pending{background:rgba(255,152,0,.35);border-color:var(--sel)}
.opt.correct{background:var(--ok);border-color:var(--ok)}
.opt.dim{opacity:.6}
.opt.hidden{opacity:.25}
.btn{height:52px;border-radius:28px;background:var(--gold);color:#0A0E33;display:flex;align-items:center;justify-content:center;font-weight:500;font-size:16px}
.btn.tall{height:56px}
.obtn{min-height:40px;border-radius:28px;border:1px solid var(--optb);color:var(--gold);display:flex;align-items:center;justify-content:center;padding:8px 16px;font-weight:500;font-size:14px;text-align:center}
.tbtn{color:var(--gold);font-weight:500;font-size:14px;padding:10px 12px}
.life{display:flex;justify-content:space-around}
.life .item{display:flex;flex-direction:column;align-items:center}
.life .circ{width:56px;height:56px;border-radius:50%;border:2px solid var(--optb);background:var(--opt);display:flex;align-items:center;justify-content:center;font-size:22px}
.life .lbl{font-size:11px;font-weight:500;margin-top:2px}
.card{background:var(--opt);border-radius:12px;padding:16px}
.chip{display:inline-flex;align-items:center;height:32px;padding:0 12px;border-radius:8px;border:1px solid var(--optb);font-size:14px;font-weight:500;margin:0 8px 8px 0}
[dir=rtl] .chip{margin:0 0 8px 8px}
.chip.on{background:#4A4458;border-color:#4A4458;color:#E8DEF8}
.chip.on::before{content:'✓';margin-inline-end:6px}
.surface{border-radius:20px;background:var(--opt);padding:16px}
.rank{border-radius:14px;background:var(--opt);padding:12px;display:flex;align-items:center}
.rank.me{background:rgba(255,193,7,.18);border:1.5px solid var(--gold)}
.rank .medal{width:44px;font-size:18px}
.dim-overlay{position:absolute;inset:0;background:rgba(0,0,0,.55);display:flex;align-items:center;justify-content:center}
.dialog{background:#2B2930;border-radius:28px;padding:24px;width:86%}
.bars{display:flex;justify-content:space-around;align-items:flex-end;height:200px}
.bars .b{display:flex;flex-direction:column;align-items:center;justify-content:flex-end;height:100%}
.bars .bar{width:32px;background:var(--gold);border-radius:6px 6px 0 0}
.ring{position:relative;width:52px;height:52px;display:flex;align-items:center;justify-content:center}
.ring svg{position:absolute;inset:0}
.feature{position:absolute;inset:0}
"""

def page(body, cw, ch, lang, extra_css=""):
    css = CSS.replace("FONTS", FONTS.as_uri())
    return f"""<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><style>{css}{extra_css}
body{{--cw:{cw}px;--ch:{ch}px}}</style></head><body>{body}</body></html>"""

# ---------------------------------------------------------------- app screens (dp units)

def status(lang):
    return '<div class="status"><span>9:41</span><span>▴ ◉ ▮</span></div>'

def timer(left, limit):
    import math
    r, c = 22, 2 * math.pi * 22
    dash = c * left / limit
    return f'''<div class="ring"><svg viewBox="0 0 52 52"><circle cx="26" cy="26" r="{r}" stroke="#13185A" stroke-width="4" fill="none"/>
<circle cx="26" cy="26" r="{r}" stroke="#FFC107" stroke-width="4" fill="none" stroke-dasharray="{dash:.1f} {c:.1f}" transform="rotate(-90 26 26)" stroke-linecap="round"/></svg>
<span class="bold">{left}</span></div>'''

LADDER = [100, 200, 300, 500, 1000, 2000, 4000, 8000, 16000, 32000, 64000, 125000, 250000, 500000, 1000000]

def header(lang, index, left, limit):
    s = S[lang]
    return f'''<div class="row"><div class="col" style="flex:1"><span class="t-label sec">{e(fmt(s["game_question_of"], index + 1, 15))}</span>
<span class="t-title gold bold">{e(fmt(s["game_for_prize"], prize(lang, LADDER[index])))}</span></div>
{timer(left, limit)}<span style="width:8px"></span><span style="font-size:24px;padding:8px">🏆</span></div>'''

def lifelines(lang, used=()):
    s = S[lang]
    items = [("½", "lifeline_fifty"), ("👥", "lifeline_audience"), ("📞", "lifeline_friend"), ("🔄", "lifeline_switch")]
    out = []
    for i, (icon, key) in enumerate(items):
        u = i in used
        out.append(f'''<div class="item" style="opacity:{.3 if u else 1}"><div class="circ" style="border-color:{"#C62828" if u else "#5C6BC0"}">{"✕" if u else icon}</div>
<span class="lbl">{e(s[key])}</span></div>''')
    return f'<div class="life">{"".join(out)}</div>'

def options(lang, q, states):
    out = []
    for i, text in enumerate(q["options"]):
        st = states.get(i, "")
        shown = "" if st == "hidden" else e(text)
        out.append(f'<div class="opt {st}"><span class="lab">{LABELS[lang][i]}:</span><span>{shown}</span></div>')
    return "".join(out)

def game_screen(lang, qid, index, left, limit, pending=None, used=(), hidden=()):
    s, q = S[lang], BANK[lang][qid]
    states = {i: "hidden" for i in hidden}
    if pending is not None:
        states[pending] = "pending"
    bottom = ""
    if pending is not None:
        bottom += f'<div class="btn">{e(s["game_final_answer"])}</div>'
    bottom += f'<div class="obtn">{e(fmt(s["game_walk_away"], prize(lang, LADDER[index - 1] if index else 0)))}</div>'
    return f'''{status(lang)}<div class="col pad gap14">{header(lang, index, left, limit)}{lifelines(lang, used)}
<div class="qcard">{e(q["text"])}</div>{options(lang, q, states)}{bottom}</div>'''

def audience_screen(lang, qid, index):
    s, q = S[lang], BANK[lang][qid]
    votes = [0, 0, 0, 0]
    others = [9, 14, 6]
    for i in range(4):
        votes[i] = 71 if i == q["correctIndex"] else others.pop(0)
    bars = "".join(f'''<div class="b"><span style="font-size:12px;font-weight:500">{v}%</span>
<div class="bar" style="height:{max(2, 140 * v // 100)}px"></div><span class="bold">{LABELS[lang][i]}</span></div>''' for i, v in enumerate(votes))
    dialog = f'''<div class="dim-overlay"><div class="dialog"><div style="font-size:24px;margin-bottom:16px">👥 {e(s["audience_title"])}</div>
<div class="bars">{bars}</div><div style="text-align:end;margin-top:18px" class="gold bold">{e(s["ok"])}</div></div></div>'''
    return game_screen(lang, qid, index, 29, 45, used=(1,)) + dialog

def reveal_screen(lang, qid, index):
    s, q = S[lang], BANK[lang][qid]
    states = {i: ("correct" if i == q["correctIndex"] else "dim") for i in range(4)}
    panel = f'''<div class="surface" style="border:1.5px solid var(--ok)"><div class="col gap10">
<span class="bold" style="color:#43A047;font-size:16px">{e(s["game_correct"])}</span>
<span class="sec">💡 {e(q["explanation"])}</span><span class="tbtn" style="padding:4px 0;font-size:13px">🚩 {e(s["report_button"])}</span>
<div class="btn" style="height:44px">{e(s["game_next"])}</div></div></div>'''
    return f'''{status(lang)}<div class="col pad gap14">{header(lang, index, 21, 30)}{lifelines(lang, used=(0,))}
<div class="qcard">{e(q["text"])}</div>{options(lang, q, states)}{panel}</div>'''

def home_screen(lang):
    s = S[lang]
    topics = [s["topic_all"]] + [t for t in TOPICS[lang]]
    chips_lang = "".join(f'<span class="chip {"on" if l == lang else ""}">{n}</span>' for l, n in (("ar", "العربية"), ("fr", "Français"), ("en", "English")))
    topic_chips = "".join(f'<span class="chip {"on" if i == 10 else ""}">{"🎲 " if i == 0 else ""}{e(t)}</span>' for i, t in enumerate(topics))
    return f'''{status(lang)}<div class="col" style="padding:16px 24px;align-items:center">
<div style="display:flex">{chips_lang}</div>
<div style="font-size:64px;line-height:1.1;margin-top:6px">💎</div>
<div class="t-hl gold bold" style="margin-top:12px">{e(s["app_name"])}</div>
<div class="sec" style="margin-top:12px;text-align:center">{e(s["home_tagline"])}</div>
<div class="card col" style="width:100%;margin-top:20px"><span class="gold bold">{e(fmt(s["home_best_prize"], prize(lang, 250000)))}</span>
<span style="margin-top:6px">{e(fmt(s["home_games_played"], 27))}</span><span style="margin-top:6px">{e(fmt(s["home_million_wins"], 1))}</span></div>
<div class="card" style="width:100%;margin-top:20px"><div class="bold" style="margin-bottom:10px">{e(s["home_topic"])}</div><div>{topic_chips}</div></div>
<div class="btn tall" style="width:100%;margin-top:20px">{e(s["home_play"])}</div></div>'''

def online_header(lang, title, refresh=True):
    s = S[lang]
    return f'''<div class="row" style="padding:4px 8px"><span class="tbtn">{e(s["back"])}</span>
<span class="t-title gold bold" style="flex:1">{title}</span>{f'<span class="tbtn">{e(s["refresh"])}</span>' if refresh else ""}</div>'''

def rank_row(lang, rank, name, detail, value, me=False):
    medal = {1: "🥇", 2: "🥈", 3: "🥉"}.get(rank, f"#{rank}")
    you = f' {e(S[lang]["leaderboard_you"])}' if me else ""
    det = f'<span class="t-body-s sec">{e(detail)}</span>' if detail else ""
    return f'''<div class="rank {"me" if me else ""}"><span class="medal">{medal}</span><div class="col" style="flex:1">
<span class="bold">{e(name)}{you}</span>{det}</div><span class="gold bold">{prize(lang, value)}</span></div>'''

ME = {"ar": "مريم", "fr": "Inès", "en": "Sam"}

def leaderboard_screen(lang):
    s = S[lang]
    players = [("Youssef_92", 1000000, 3), ("Salma", 1000000, 1), ("Léa", 500000, 0), ("أمين", 250000, 0),
               ("Karim", 250000, 0), ("Nour", 125000, 0), (ME[lang], 125000, 0), ("Sofia", 64000, 0), ("Omar", 64000, 0), ("Hugo", 32000, 0)]
    rows = "".join(rank_row(lang, i + 1, n, f"💎 × {m}" if m else "", v, me=(n == ME[lang])) for i, (n, v, m) in enumerate(players))
    return f'''{status(lang)}{online_header(lang, "🏆 " + e(s["leaderboard_title"]))}<div class="col gap10" style="padding:16px">
<span class="gold bold" style="font-size:16px">{e(fmt(s["leaderboard_my_rank"], 7))}</span>{rows}</div>'''

def challenge_screen(lang):
    s, code = S[lang], "K7P2QX"
    creator = {"ar": "أمين", "fr": "Amine", "en": "Amine"}[lang]
    players = [(creator, 125000, 12, "4:05"), (ME[lang], 64000, 11, "3:12"), ("Salma", 32000, 10, "2:58"), ("Karim", 4000, 7, "1:46")]
    rows = "".join(rank_row(lang, i + 1, n, fmt(s["challenge_correct_time"], c, t), v, me=(n == ME[lang])) for i, (n, v, c, t) in enumerate(players))
    return f'''{status(lang)}{online_header(lang, "⚔️ " + e(fmt(s["challenge_results_title"], code)))}<div class="col gap10" style="padding:16px">
<span class="sec">{e(fmt(s["challenge_by"], creator))}</span><div class="obtn">📨 {e(s["challenge_share"])}</div>{rows}</div>'''

def win_screen(lang):
    s = S[lang]
    return f'''{status(lang)}<div class="col" style="padding:24px;align-items:center">
<div style="font-size:72px;margin-top:40px">🏆</div>
<div class="gold" style="font-size:28px;font-weight:900;text-align:center;margin-top:16px">{e(s["result_won"])}</div>
<div class="card col" style="width:100%;align-items:center;margin-top:20px;padding:20px">
<span class="sec">{e(s["result_prize_label"])}</span>
<span class="gold" style="font-size:36px;font-weight:900;margin-top:8px">{e(fmt(s["points"], prize(lang, 1000000)))}</span>
<span style="margin-top:8px">{e(fmt(s["result_correct_answers"], 15, 15))}</span>
<span class="sec" style="margin-top:8px">{e(fmt(s["home_best_prize"], prize(lang, 1000000)))}</span></div>
<div class="gold bold" style="margin-top:18px">🎉 {e(s["result_new_record"])}</div>
<div class="btn" style="width:100%;margin-top:18px">{e(s["result_play_again"])}</div>
<div class="obtn" style="width:100%;margin-top:14px">{e(s["result_share"])}</div>
<div class="tbtn" style="margin-top:8px">{e(s["result_home"])}</div></div>'''

TOPICS = {
    "ar": ["ثقافة عامة", "علوم", "تاريخ", "جغرافيا", "رياضة", "دين وحضارة إسلامية", "تقنية وبرمجة", "أدب ولغة عربية", "أفلام ومسلسلات", "المغرب العربي"],
    "fr": ["Culture générale", "Sciences", "Histoire", "Géographie", "Sport", "Religion et civilisation islamique", "Technologie et programmation", "Littérature et langue", "Cinéma et séries", "Le Maghreb"],
    "en": ["General knowledge", "Science", "History", "Geography", "Sport", "Religion and Islamic civilisation", "Technology and programming", "Literature and language", "Film and TV", "The Maghreb"],
}

# ---------------------------------------------------------------- composition

def device(screen_html, lang, sw, sh, frame_w, top, radius, bezel):
    scale = (frame_w - 2 * bezel) / sw
    frame_h = sh * scale + 2 * bezel
    d = "rtl" if lang == "ar" else "ltr"
    return f'''<div class="device" style="width:{frame_w}px;height:{frame_h:.0f}px;top:{top}px;border-radius:{radius}px">
<div class="screen-wrap" style="left:{bezel}px;top:{bezel}px;width:{sw*scale:.0f}px;height:{sh*scale:.0f}px;border-radius:{radius-bezel}px">
<div class="screen" dir="{d}" style="width:{sw}px;height:{sh}px;transform:scale({scale:.4f})">{screen_html}</div></div></div>'''

def sparkles(cw, ch):
    import random
    rnd = random.Random(cw * 7 + ch)
    out = []
    for _ in range(14):
        size = rnd.randint(6, 18)
        out.append(f'<div class="sparkle" style="width:{size}px;height:{size}px;left:{rnd.randint(0, cw)}px;top:{rnd.randint(0, ch)}px"></div>')
    return "".join(out)

def caption_html(text, lang, top, size):
    d = "rtl" if lang == "ar" else "ltr"
    return f'<div class="caption" dir="{d}" style="top:{top}px;font-size:{size}px">{e(text)}</div>'

FORMATS = {
    # name: canvas, screen size (dp), frame width, frame top, radius, bezel, caption top, caption size
    "phone": dict(cw=1080, ch=1920, sw=412, sh=900, fw=780, ft=330, r=70, bz=18, ct=95, cs=76),
    "tablet7": dict(cw=1200, ch=1920, sw=520, sh=860, fw=1000, ft=320, r=46, bz=24, ct=95, cs=76),
    "tablet10": dict(cw=1600, ch=2560, sw=600, sh=980, fw=1340, ft=420, r=56, bz=28, ct=125, cs=100),
}

SCREENS = [
    lambda l: game_screen(l, "g9-2-0-06", 6, 32, 45, pending=BANK[l]["g9-2-0-06"]["correctIndex"]),
    lambda l: audience_screen(l, "m10", 7),
    lambda l: home_screen(l),
    lambda l: challenge_screen(l),
    lambda l: leaderboard_screen(l),
    lambda l: reveal_screen(l, "g9-1-0-22", 2),
    None,  # languages collage, built separately
    lambda l: win_screen(l),
]
TABLET_SCREENS = [0, 1, 2, 5]

def languages_collage(lang, f):
    """Three phones, one per language (the listing's language first), each cropped to the question."""
    qid, out = "m12", []
    order = [lang] + [l for l in LANGS if l != lang]
    fw = int(f["cw"] * 0.64)
    crop, step = 545, 530
    for i, l in enumerate(order):
        scr = game_screen(l, qid, 1, 24, 30)
        left = [f["cw"] * 0.07, f["cw"] * 0.29, f["cw"] * 0.07][i] if lang != "ar" else [f["cw"] * 0.29, f["cw"] * 0.07, f["cw"] * 0.29][i]
        dev = device(scr, l, f["sw"], f["sh"], fw, f["ft"] - 60 + i * step, int(f["r"] * .8), int(f["bz"] * .8))
        dev = dev.replace('class="device" style="', f'class="device" style="left:{left:.0f}px;transform:none;z-index:{3 - i};')
        # Crop each phone below the question card, with a soft fade.
        dev = re.sub(r'height:(\d+)px;top', f'height:{crop}px;overflow:hidden;top', dev, count=1)
        dev = dev.replace("</div></div></div>", '</div></div><div style="position:absolute;left:0;right:0;bottom:0;height:90px;'
                          'background:linear-gradient(transparent,#0A0E33)"></div></div>')
        out.append(dev)
    return "".join(out)

def build():
    OUT.mkdir(parents=True, exist_ok=True)
    manifest = []
    for lang in LANGS:
        for fmt_name, f in FORMATS.items():
            indices = range(8) if fmt_name == "phone" else TABLET_SCREENS
            for n, i in enumerate(indices, 1):
                cap = caption_html(CAPTIONS[lang][i], lang, f["ct"], f["cs"])
                if SCREENS[i] is None:
                    body = sparkles(f["cw"], f["ch"]) + cap + languages_collage(lang, f)
                else:
                    body = sparkles(f["cw"], f["ch"]) + cap + device(SCREENS[i](lang), lang, f["sw"], f["sh"], f["fw"], f["ft"], f["r"], f["bz"])
                name = f"{lang}/{fmt_name}/{n:02d}.png"
                (OUT / f"{lang}-{fmt_name}-{n:02d}.html").write_text(page(body, f["cw"], f["ch"], lang))
                manifest.append({"html": f"{lang}-{fmt_name}-{n:02d}.html", "png": name, "w": f["cw"], "h": f["ch"]})
        # Feature graphic 1024x500
        title, sub = FEATURE[lang]
        d = "rtl" if lang == "ar" else "ltr"
        phone = device(game_screen(lang, "g9-2-0-06", 6, 32, 45, pending=BANK[lang]["g9-2-0-06"]["correctIndex"]),
                       lang, 412, 900, 300, 40, 40, 10)
        side = "right:70px;left:auto" if lang != "ar" else "left:70px;right:auto"
        phone = phone.replace('class="device" style="', f'class="device" style="{side};transform:rotate({-6 if lang != "ar" else 6}deg);')
        text_side = "left:64px" if lang != "ar" else "right:64px"
        body = sparkles(1024, 500) + phone + f'''<div dir="{d}" style="position:absolute;{text_side};top:90px;width:560px">
<div style="font-size:64px;line-height:1">💎</div>
<div style="font-size:58px;font-weight:900;color:#FFC107;margin-top:14px;line-height:1.05">{e(S[lang]["app_name"])}</div>
<div style="font-size:38px;font-weight:700;margin-top:18px">{e(title)}</div>
<div style="font-size:22px;margin-top:14px;color:rgba(255,255,255,.85)">{e(sub)}</div></div>'''
        (OUT / f"{lang}-feature.html").write_text(page(body, 1024, 500, lang))
        manifest.append({"html": f"{lang}-feature.html", "png": f"{lang}/feature-graphic-1024x500.png", "w": 1024, "h": 500, "opaque": True})
    # App icon 512x512 (same design as the launcher icon, enlarged for the store)
    icon = '''<svg width="512" height="512" viewBox="0 0 108 108" style="position:absolute;inset:0">
<rect width="108" height="108" fill="#1B1464"/>
<g transform="translate(54 54) scale(1.55) translate(-54 -54)">
<path fill="#FFC107" d="M54,26 L80,54 L54,82 L28,54 Z"/><path fill="#1B1464" d="M54,36 L70,54 L54,72 L38,54 Z"/>
<path fill="#FFE082" d="M54,44 L62,54 L54,64 L46,54 Z"/></g></svg>'''
    (OUT / "icon.html").write_text(page(icon, 512, 512, "en"))
    manifest.append({"html": "icon.html", "png": "icon-512.png", "w": 512, "h": 512, "opaque": True})
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=1))
    print(len(manifest), "pages")

build()
