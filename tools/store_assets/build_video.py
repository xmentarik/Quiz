#!/usr/bin/env python3
"""Builds the 32-second promo video (1920x1080, 30 fps) for each language, for YouTube / Google Play.

Every scene is an HTML page animated with CSS keyframes on one global timeline; video_capture.js seeks the
animations frame by frame and pipes screenshots into ffmpeg. The soundtrack is mixed from the app's own sounds.

Usage: CHROME_PATH=<chromium> python3 tools/store_assets/build_video.py <work_dir> <fonts_dir> <out_dir>
"""
import random
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import build_pages as bp  # noqa: E402

e, S, fmt, prize = bp.e, bp.S, bp.fmt, bp.prize
T, FPS, W, H = 32.0, 30, 1920, 1080
EASE = "cubic-bezier(.2,.8,.2,1)"
RAW = bp.ROOT / "app/src/main/res/raw"

CTA = {"ar": "مجاناً على Google Play", "fr": "Gratuit sur Google Play", "en": "Free on Google Play"}

# ---------------------------------------------------------------- timeline helpers

_keyframes = []

def track(prop, pts, ease=EASE):
    """One keyframes animation spanning the whole video; pts = [(seconds, value), ...] in time order."""
    pts = [(0.0, pts[0][1])] + list(pts) + [(T, pts[-1][1])]
    name = f"k{len(_keyframes)}"
    frames = "".join(f"{min(100, t / T * 100):.4f}%{{{prop}:{v};animation-timing-function:{ease}}}" for t, v in pts)
    _keyframes.append(f"@keyframes {name}{{{frames}}}")
    return f"{name} {T}s linear 0s both"

def anim(ease=EASE, **props):
    return "animation:" + ",".join(track(p.replace("_", "-"), pts, ease) for p, pts in props.items()) + ";"

def fade(t_in, t_out, d=0.45):
    return [(t_in, 0), (t_in + d, 1), (t_out - d, 1), (t_out, 0)]

def show_at(t, d=0.25):
    return anim(opacity=[(t - d, 0), (t, 1)])

def pop(t, d=0.45, scale_from=0.0):
    return anim(opacity=[(t, 0), (t + d * .6, 1)], transform=[(t, f"scale({scale_from})"), (t + d, "scale(1)")])

# ---------------------------------------------------------------- building blocks

def game(lang, qid, index, left, limit, states=None, used=(), pending=False):
    s, q = S[lang], bp.BANK[lang][qid]
    bottom = f'<div class="btn">{e(s["game_final_answer"])}</div>' if pending else ""
    bottom += f'<div class="obtn">{e(fmt(s["game_walk_away"], prize(lang, bp.LADDER[index - 1] if index else 0)))}</div>'
    return f'''{bp.status(lang)}<div class="col pad gap14">{bp.header(lang, index, left, limit)}{bp.lifelines(lang, used)}
<div class="qcard">{e(q["text"])}</div>{bp.options(lang, q, states or {})}{bottom}</div>'''

def layer(html, style=""):
    return f'<div style="position:absolute;inset:0;background:linear-gradient(var(--deep),var(--night));{style}">{html}</div>'

def phone(lang, layers, fw=520, top=52, side="inline-end:200px", style="", sh=800):
    dev = bp.device("".join(layers), lang, 412, sh, fw, 0, 64, 18)
    h = sh * (fw - 36) / 412 + 36
    return f'<div style="position:absolute;width:{fw}px;height:{h:.0f}px;top:{top}px;inset-{side};{style}">{dev}</div>'

def text_col(inner):
    return f'''<div style="position:absolute;top:0;bottom:0;inset-inline-start:130px;width:860px;display:flex;flex-direction:column;
justify-content:center">{inner}</div>'''

def caption(text, style=""):
    return f'<div class="vcap" style="{style}">{e(text)}</div>'

DIAMOND = '''<svg viewBox="28 26 52 56" style="width:100%;height:100%"><path fill="#FFC107" d="M54,26 L80,54 L54,82 L28,54 Z"/>
<path fill="#1B1464" d="M54,36 L70,54 L54,72 L38,54 Z"/><path fill="#FFE082" d="M54,44 L62,54 L54,64 L46,54 Z"/></svg>'''

VCSS = """
.vcap{font-size:76px;font-weight:900;line-height:1.12;letter-spacing:-.5px;text-shadow:0 6px 24px rgba(0,0,0,.45)}
.ladder{position:relative;width:540px;margin-top:56px}
.ladder .r{height:64px;display:flex;justify-content:space-between;align-items:center;padding:0 28px;font-size:34px;font-weight:700;position:relative}
.ladder .r .p{color:var(--gold)}
.ladder .hl{position:absolute;left:0;right:0;height:64px;border-radius:32px;background:rgba(255,193,7,.2);border:3px solid var(--gold)}
.blife{display:flex;gap:34px;margin-top:60px}
.blife .i{display:flex;flex-direction:column;align-items:center;width:150px}
.blife .c{width:130px;height:130px;border-radius:50%;border:4px solid var(--optb);background:var(--opt);display:flex;align-items:center;
 justify-content:center;font-size:56px;font-weight:700}
.blife .l{font-size:26px;font-weight:500;margin-top:12px;text-align:center}
.bchips{margin-top:52px;display:flex;flex-wrap:wrap}
.bchip{font-size:30px;font-weight:500;padding:12px 24px;border:2px solid var(--optb);background:var(--opt);border-radius:16px;margin:0 16px 16px 0}
[dir=rtl] .bchip{margin:0 0 16px 16px}
.code{display:flex;gap:14px;margin-top:20px}[dir=rtl] .code{direction:ltr;justify-content:flex-end}
.code span{width:104px;height:124px;border-radius:18px;background:var(--opt);border:3px solid var(--gold);display:flex;align-items:center;
 justify-content:center;font-size:66px;font-weight:900;color:var(--gold)}
.big{font-size:120px;font-weight:900;color:var(--gold);text-shadow:0 8px 30px rgba(255,193,7,.35)}
.confetti{position:absolute;top:-40px;width:16px;height:26px;border-radius:3px}
.cta{display:inline-block;margin-top:44px;padding:22px 56px;border-radius:999px;background:var(--gold);color:#0A0E33;font-size:46px;font-weight:900}
"""

# ---------------------------------------------------------------- scenes

def scene(t0, t1, inner, d=0.45):
    return f'<div style="position:absolute;inset:0;{anim(opacity=fade(t0, t1, d))}">{inner}</div>'

def slide_in(t0, t1, sign, dist=320):
    return anim(transform=[(t0, f"translateX({dist * sign}px)"), (t0 + .7, "translateX(0)"),
                           (t1 - .5, "translateX(0)"), (t1, f"translateX({-dist * sign * .4}px)")])

def rise(t, dist=50, d=.6):
    return anim(opacity=[(t, 0), (t + d, 1)], transform=[(t, f"translateY({dist}px)"), (t + d, "translateY(0)")])

def intro(lang):
    s = S[lang]
    glow = anim(opacity=[(0.3, 0), (1.0, .9), (3.6, .5)], transform=[(0.3, "scale(.6)"), (3.6, "scale(1.25)")])
    icon = anim(opacity=[(0.15, 0), (0.5, 1)], transform=[(0.15, "scale(0) rotate(-90deg)"), (0.85, "scale(1.12) rotate(0)"), (1.1, "scale(1)")])
    return scene(0, 3.6, f'''<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center">
<div style="position:absolute;width:760px;height:760px;border-radius:50%;background:radial-gradient(circle,rgba(255,193,7,.35),transparent 65%);{glow}"></div>
<div style="width:250px;height:270px;{icon}">{DIAMOND}</div>
<div style="font-size:118px;font-weight:900;color:var(--gold);margin-top:34px;{rise(.85)}">{e(s["app_name"])}</div>
<div style="font-size:56px;font-weight:700;margin-top:18px;{rise(1.45)}">{e(bp.FEATURE[lang][0])}</div></div>''')

def gameplay(lang, sign):
    t0, t1, qid, idx = 3.6, 10.4, "g9-2-0-06", 6
    c = bp.BANK[lang][qid]["correctIndex"]
    layers = [layer(game(lang, qid, idx, 33, 45)),
              layer(game(lang, qid, idx, 32, 45), show_at(4.6, .05)),
              layer(game(lang, qid, idx, 31, 45), show_at(5.4, .05)),
              layer(game(lang, qid, idx, 30, 45, {c: "pending"}, pending=True), show_at(6.0, .2)),
              layer(game(lang, qid, idx, 30, 45, {i: ("correct" if i == c else "dim") for i in range(4)}), show_at(8.0, .2))]
    rungs = list(range(9, 3, -1))  # 32,000 .. 1,000, highest first
    rows = "".join(f'<div class="r"><span>{i + 1}</span><span class="p">{prize(lang, bp.LADDER[i])}</span></div>' for i in rungs)
    y = lambda i: rungs.index(i) * 64
    hl = anim(transform=[(8.3, f"translateY({y(idx)}px)"), (8.9, f"translateY({y(idx + 1)}px)")],
              background_color=[(8.0, "rgba(255,193,7,.2)"), (8.2, "rgba(46,125,50,.6)"), (8.9, "rgba(255,193,7,.2)")])
    ladder = f'<div class="ladder" style="{rise(4.3)}"><div class="hl" style="top:0;{hl}"></div>{rows}</div>'
    return scene(t0, t1, text_col(caption(bp.CAPTIONS[lang][0], rise(3.9)) + ladder)
                 + phone(lang, layers, style=slide_in(t0, t1, sign)))

def lifelines(lang, sign):
    t0, t1, qid, idx = 10.4, 15.6, "m10", 7
    s, q = S[lang], bp.BANK[lang]["m10"]
    c = q["correctIndex"]
    wrong = [i for i in range(4) if i != c]
    hidden = {wrong[0]: "hidden", wrong[2]: "hidden"}
    votes = {c: 78, wrong[1]: 22}
    bars = "".join(f'''<div class="b"><span style="font-size:12px;font-weight:500">{votes.get(i, 0)}%</span>
<div class="bar" style="height:{max(2, 150 * votes.get(i, 0) // 100)}px;transform-origin:bottom;{anim(transform=[(13.2 + i * .08, "scaleY(0)"), (14.0 + i * .08, "scaleY(1)")])}"></div>
<span class="bold">{bp.LABELS[lang][i]}</span></div>''' for i in range(4))
    dialog = f'''<div class="dim-overlay"><div class="dialog"><div style="font-size:24px;margin-bottom:16px">👥 {e(s["audience_title"])}</div>
<div class="bars">{bars}</div><div style="text-align:end;margin-top:18px" class="gold bold">{e(s["ok"])}</div></div></div>'''
    layers = [layer(game(lang, qid, idx, 40, 45)),
              layer(game(lang, qid, idx, 38, 45, hidden, used=(0,)), show_at(11.8, .2)),
              layer(game(lang, qid, idx, 36, 45, hidden, used=(0, 1)) + dialog, show_at(13.0, .25))]
    icons = [("½", "lifeline_fifty", 11.8), ("👥", "lifeline_audience", 13.0), ("📞", "lifeline_friend", None), ("🔄", "lifeline_switch", None)]
    items = []
    for i, (icon, key, used_at) in enumerate(icons):
        p = 10.9 + i * .15
        tr = [(p, "scale(0)"), (p + .45, "scale(1)")]
        border = [(0, "#5C6BC0")]
        if used_at:
            tr += [(used_at - .01, "scale(1)"), (used_at + .2, "scale(1.2)"), (used_at + .6, "scale(1)")]
            border = [(used_at, "#5C6BC0"), (used_at + .2, "#FFC107")]
        items.append(f'''<div class="i" style="{anim(opacity=[(p, 0), (p + .3, 1)], transform=tr)}"><div class="c" style="{anim(border_color=border)}">{icon}</div>
<div class="l">{e(s[key])}</div></div>''')
    return scene(t0, t1, text_col(caption(bp.CAPTIONS[lang][1], rise(10.7)) + f'<div class="blife">{"".join(items)}</div>')
                 + phone(lang, layers, style=slide_in(t0, t1, sign)))

def topics(lang, sign):
    t0, t1 = 15.6, 19.6
    chips = "".join(f'<span class="bchip" style="{rise(16.1 + i * .12, 30, .4)}">{e(t)}</span>' for i, t in enumerate(bp.TOPICS[lang]))
    return scene(t0, t1, text_col(caption(bp.CAPTIONS[lang][2], rise(15.9)) + f'<div class="bchips">{chips}</div>')
                 + phone(lang, [layer(bp.home_screen(lang))], style=slide_in(t0, t1, sign)))

def online(lang, sign):
    t0, t1, swap = 19.6, 24.4, 22.0
    s = S[lang]
    a = anim(opacity=[(19.9, 0), (20.4, 1), (swap - .3, 1), (swap, 0)])
    b = anim(opacity=[(swap, 0), (swap + .4, 1)])
    rank = f'<div class="big" style="margin-top:40px;{pop(20.5, .5, .4)}">🌍 #7</div>'
    code = "".join(f'<span style="{pop(swap + .2 + i * .1, .35, .3)}">{ch}</span>' for i, ch in enumerate("K7P2QX"))
    left = f'''<div style="position:relative;height:420px">
<div style="position:absolute;inset:0;{a}">{caption(bp.CAPTIONS[lang][4])}{rank}</div>
<div style="position:absolute;inset:0;{b}">{caption(bp.CAPTIONS[lang][3])}<div class="code" style="margin-top:50px">{code}</div>
<div style="font-size:30px;margin-top:26px;color:var(--sec);{rise(swap + .9, 20, .4)}">📨 {e(s["challenge_share"])}</div></div></div>'''
    layers = [layer(bp.leaderboard_screen(lang)), layer(bp.challenge_screen(lang), show_at(swap, .3))]
    return scene(t0, t1, text_col(left) + phone(lang, layers, style=slide_in(t0, t1, sign)))

def languages(lang):
    t0, t1 = 24.4, 27.6
    order = [l for l in bp.LANGS if l != lang]
    order = [order[0], lang, order[1]]
    out = [f'<div class="vcap" style="position:absolute;top:60px;left:0;right:0;text-align:center;{rise(24.6)}">{e(bp.CAPTIONS[lang][6])}</div>']
    for i, l in enumerate(order):
        mid = l == lang
        fw = 400 if mid else 340
        top = 220 if mid else 290
        x = [560, 960, 1360][i] - fw / 2
        st = 24.6 + [0.25, 0, 0.5][i]
        style = anim(opacity=[(st, 0), (st + .3, 1)], transform=[(st, "translateY(500px)"), (st + .7, "translateY(0)")])
        out.append(phone(l, [layer(bp.game_screen(l, "m12", 1, 24, 30))], fw=fw, top=top, side=f"inline-start:0px;left:{x:.0f}px;right:auto",
                         style=style + f"z-index:{2 if mid else 1}"))
    return scene(t0, t1, "".join(out))

def win(lang, sign):
    t0, t1 = 27.6, 30.0
    rnd = random.Random(7)
    confetti = []
    for _ in range(80):
        st = 27.8 + rnd.random() * 1.2
        x = rnd.randint(0, W)
        col = rnd.choice(["#FFC107", "#FFE082", "#FF9800", "#5C6BC0", "#ffffff", "#43A047"])
        rot = rnd.randint(360, 1080) * rnd.choice([1, -1])
        confetti.append(f'''<div class="confetti" style="left:{x}px;background:{col};{anim("linear", transform=[(st, "translateY(0) rotate(0)"),
            (st + 2.2, f"translateY({H + 80}px) rotate({rot}deg)")])}"></div>''')
    big = f'<div class="big" style="margin-top:30px;{pop(28.3, .5, .5)}">{prize(lang, 1000000)}</div>'
    return scene(t0, t1, text_col(caption(bp.CAPTIONS[lang][7], rise(27.8)) + big)
                 + phone(lang, [layer(bp.win_screen(lang))], style=slide_in(t0, t1 + 2, sign)) + "".join(confetti))

def end_card(lang):
    s = S[lang]
    return f'''<div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;
background:radial-gradient(circle at 50% 40%,rgba(255,193,7,.22),transparent 45%),linear-gradient(160deg,#2a1f8a,#1B1464 40%,#0A0E33);
{anim(opacity=[(29.6, 0), (30.0, 1)])}"><div style="width:200px;height:216px;{pop(29.9, .6)}">{DIAMOND}</div>
<div style="font-size:104px;font-weight:900;color:var(--gold);margin-top:28px;{rise(30.1)}">{e(s["app_name"])}</div>
<div style="font-size:40px;margin-top:14px;color:rgba(255,255,255,.88);{rise(30.3)}">{e(bp.FEATURE[lang][1])}</div>
<div class="cta" style="{pop(30.6, .5, .7)}">{e(CTA[lang])}</div></div>'''

def video_page(lang):
    _keyframes.clear()
    sign = -1 if lang == "ar" else 1
    drift = anim("linear", transform=[(0, "translateY(0)"), (T, "translateY(-80px)")])
    body = (f'<div style="position:absolute;inset:0;{drift}">{bp.sparkles(W, H + 100)}</div>'
            f'<div dir="{"rtl" if lang == "ar" else "ltr"}" style="position:absolute;inset:0">'
            + intro(lang) + gameplay(lang, sign) + lifelines(lang, sign) + topics(lang, sign) + online(lang, sign)
            + languages(lang) + win(lang, sign) + end_card(lang) + "</div>")
    return bp.page(body, W, H, lang, VCSS + "".join(_keyframes))

# ---------------------------------------------------------------- soundtrack

CUES = [("sfx_game_start", 0.0, .9), ("sfx_select", 6.0, 1), ("sfx_final_answer", 6.8, .9), ("sfx_correct", 8.0, 1),
        ("sfx_next_question", 10.4, .7), ("sfx_lifeline", 11.8, .9), ("sfx_lifeline", 13.0, .9), ("sfx_next_question", 15.6, .7),
        ("sfx_next_question", 19.6, .7), ("sfx_milestone", 22.0, .6), ("sfx_next_question", 24.4, .7), ("sfx_win", 27.7, 1)]

def mux(silent, out):
    music_start, music_len = 3.6, 24.4
    inputs = ["-i", str(silent), "-stream_loop", "2", "-i", str(RAW / "music_tense.ogg")]
    parts = [f"[1:a]atrim=0:{music_len},afade=t=in:d=0.6,afade=t=out:st={music_len - 1.2}:d=1.2,volume=0.5,"
             f"adelay=delays={int(music_start * 1000)}:all=1[m]"]
    labels = ["[m]"]
    for n, (name, t, gain) in enumerate(CUES):
        inputs += ["-i", str(RAW / f"{name}.ogg")]
        parts.append(f"[{n + 2}:a]volume={gain},adelay=delays={int(t * 1000)}:all=1[c{n}]")
        labels.append(f"[c{n}]")
    parts.append(f"{''.join(labels)}amix=inputs={len(labels)}:normalize=0:duration=longest,atrim=0:{T},"
                 f"afade=t=out:st={T - 1.5}:d=1.5,alimiter=limit=0.9,aformat=channel_layouts=stereo[a]")
    subprocess.run(["ffmpeg", "-y", "-v", "error", *inputs, "-filter_complex", ";".join(parts), "-map", "0:v", "-map", "[a]",
                    "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-shortest", str(out)], check=True)

if __name__ == "__main__":
    work, bp.FONTS, out = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    langs = sys.argv[4:] or bp.LANGS
    work.mkdir(parents=True, exist_ok=True)
    out.mkdir(parents=True, exist_ok=True)
    capture = Path(__file__).with_name("video_capture.js")
    for lang in langs:
        page = work / f"video-{lang}.html"
        page.write_text(video_page(lang))
        silent = work / f"video-{lang}-silent.mp4"
        subprocess.run(["node", str(capture), str(page), str(silent), str(T), str(FPS)], check=True)
        mux(silent, out / f"promo-{lang}.mp4")
        print("wrote", out / f"promo-{lang}.mp4")
