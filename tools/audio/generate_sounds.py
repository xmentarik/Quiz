#!/usr/bin/env python3
"""Synthesises the game's sound effects and music loops from scratch (no samples, no licences).

Usage:  python3 tools/audio/generate_sounds.py app/src/main/res/raw
Needs:  numpy, and ffmpeg built with libvorbis.

Every sound is generated with additive synthesis, filtered noise and an FFT reverb.
Music loops are rendered so their reverb tail wraps around, which makes them loop seamlessly.
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44_100
rng = np.random.default_rng(7)


# ---------------------------------------------------------------- building blocks

def samples(seconds):
    return int(round(seconds * SR))


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def adsr(n, a=0.01, d=0.1, s=0.7, r=0.1):
    a_n, d_n, r_n = samples(a), samples(d), samples(r)
    s_n = max(n - a_n - d_n - r_n, 0)
    e = np.concatenate([
        np.linspace(0, 1, a_n, endpoint=False),
        np.linspace(1, s, d_n, endpoint=False),
        np.full(s_n, s),
        np.linspace(s, 0, r_n),
    ])
    return np.pad(e, (0, max(n - len(e), 0)))[:n]


def phase_of(freq, n):
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    return 2 * np.pi * np.cumsum(f) / SR


def additive(freq, seconds, harmonics, rolloff=1.0, odd_only=False):
    """Band-limited saw/square-like tone; `rolloff` > 1 makes it darker."""
    n = samples(seconds)
    ph = phase_of(freq, n)
    top = float(np.max(freq))
    out = np.zeros(n)
    for k in range(1, harmonics + 1):
        if odd_only and k % 2 == 0:
            continue
        if k * top >= SR / 2:
            break
        out += np.sin(k * ph) / k ** rolloff
    return out


def sine(freq, seconds):
    return np.sin(phase_of(freq, samples(seconds)))


def decay(n, rate):
    return np.exp(-np.arange(n) / SR * rate)


def noise(seconds):
    return rng.standard_normal(samples(seconds))


def spectral(x, gain):
    """Static FFT filter: gain(freqs) -> multiplier."""
    spec = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(len(x), 1 / SR)
    return np.fft.irfft(spec * gain(freqs), len(x))


def lowpass(x, cutoff, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (f / cutoff) ** (2 * order)))


def highpass(x, cutoff, order=2):
    return spectral(x, lambda f: 1 / np.sqrt(1 + (cutoff / np.maximum(f, 1e-3)) ** (2 * order)))


def swept_band(x, start_hz, end_hz, width=0.6, frame=2048):
    """Time-varying band-pass (overlap-add STFT) whose centre glides from start_hz to end_hz."""
    hop = frame // 2
    win = np.hanning(frame)
    padded = np.pad(x, (0, frame))
    out = np.zeros_like(padded)
    freqs = np.fft.rfftfreq(frame, 1 / SR)
    frames = max(1, (len(x)) // hop)
    for i in range(frames):
        pos = i * hop
        centre = start_hz * (end_hz / start_hz) ** (i / max(frames - 1, 1))
        g = np.exp(-0.5 * (np.log2(np.maximum(freqs, 1) / centre) / width) ** 2)
        seg = padded[pos:pos + frame] * win
        out[pos:pos + frame] += np.fft.irfft(np.fft.rfft(seg) * g, frame) * win
    return out[:len(x)]


def convolve(x, ir):
    n = len(x) + len(ir) - 1
    size = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:n]


def impulse(seconds=1.8, brightness=5000):
    n = samples(seconds)
    ir = rng.standard_normal(n) * decay(n, 6.9 / seconds)
    ir = lowpass(ir, brightness)
    ir[:samples(0.012)] = 0  # pre-delay
    return ir / np.sqrt(np.sum(ir ** 2))


def reverb(x, mix=0.25, seconds=1.8, brightness=5000):
    wet = convolve(x, impulse(seconds, brightness))
    dry = np.pad(x, (0, len(wet) - len(x)))
    return (1 - mix) * dry + mix * wet


class Mix:
    """Growing buffer: add(signal, at_seconds, gain)."""

    def __init__(self, seconds=0.0):
        self.buf = np.zeros(samples(seconds))

    def add(self, sig, at=0.0, gain=1.0):
        start = samples(at)
        end = start + len(sig)
        if end > len(self.buf):
            self.buf = np.pad(self.buf, (0, end - len(self.buf)))
        self.buf[start:end] += sig * gain
        return self


# ---------------------------------------------------------------- instruments

def pad(notes, seconds, attack=0.4, release=0.8, cutoff=1800, harmonics=10, gain=1.0):
    n = samples(seconds)
    out = np.zeros(n)
    for m in notes:
        for cents in (-7, 0, 7):
            out += additive(midi(m) * 2 ** (cents / 1200), seconds, harmonics, rolloff=1.2)
    out = lowpass(out, cutoff) * adsr(n, attack, 0.2, 0.85, release)
    return out * gain / (len(notes) * 3)


def pluck(m, seconds=0.6, brightness=1.0):
    n = samples(seconds)
    ph = phase_of(midi(m), n)
    out = np.zeros(n)
    for k in range(1, 7):
        out += np.sin(k * ph) * decay(n, 4 + 3 * k / brightness) / k
    return out * adsr(n, 0.002, 0.05, 1.0, 0.05)


def bell(m, seconds=1.2):
    n = samples(seconds)
    f = midi(m)
    out = (np.sin(phase_of(f, n)) * decay(n, 3)
           + 0.5 * np.sin(phase_of(f * 2.76, n)) * decay(n, 6)
           + 0.3 * np.sin(phase_of(f * 5.4, n)) * decay(n, 10))
    return out * adsr(n, 0.002, 0.05, 1.0, 0.1)


def bass(m, seconds, staccato=False):
    n = samples(seconds)
    ph = phase_of(midi(m), n)
    out = np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph)
    shape = decay(n, 9) if staccato else adsr(n, 0.01, 0.15, 0.7, 0.08)
    return out * shape


def kick(seconds=0.35, top=120, bottom=42):
    n = samples(seconds)
    sweep = bottom + (top - bottom) * decay(n, 25)
    return np.sin(phase_of(sweep, n)) * decay(n, 9)


def heartbeat(gain=1.0):
    return Mix().add(kick(0.35), 0).add(kick(0.35, 100, 38), 0.2, 0.65).buf * gain


def hat(seconds=0.05):
    n = samples(seconds)
    return highpass(noise(seconds), 7000) * decay(n, 70) * 0.5


def woodblock(seconds=0.06):
    n = samples(seconds)
    return (np.sin(phase_of(1800, n)) + 0.5 * np.sin(phase_of(2700, n))) * decay(n, 80)


def timpani(m, seconds=1.4):
    n = samples(seconds)
    f = midi(m) * (1 + 0.04 * decay(n, 20))
    body = np.sin(phase_of(f, n)) + 0.4 * np.sin(phase_of(f * 1.5, n))
    thump = lowpass(noise(seconds), 400) * decay(n, 30) * 0.6
    return (body * decay(n, 3.5) + thump) * adsr(n, 0.003, 0.05, 1.0, 0.2)


def brass(notes, seconds, gain=1.0):
    """Saw stack whose upper harmonics open up slowly, like a brass swell."""
    n = samples(seconds)
    out = np.zeros(n)
    for m in notes:
        ph = phase_of(midi(m) * (1 + 0.002 * np.sin(2 * np.pi * 5 * np.arange(n) / SR)), n)
        for k in range(1, 14):
            if k * midi(m) >= SR / 2:
                break
            out += np.sin(k * ph) / k * adsr(n, 0.03 + 0.02 * k, 0.2, 0.8, 0.25)
    return out * gain / len(notes)


def strings(notes, seconds, attack=0.6, release=0.8, cutoff=3200, gain=1.0):
    """Ensemble strings: detuned saws with slow vibrato."""
    n = samples(seconds)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for m in notes:
        for cents, rate in ((-9, 5.1), (0, 5.6), (9, 6.0)):
            f = midi(m) * 2 ** (cents / 1200) * (1 + 0.003 * np.sin(2 * np.pi * rate * t))
            out += additive(f, seconds, 16, rolloff=1.0)
    out = lowpass(out, cutoff) * adsr(n, attack, 0.3, 0.9, release)
    return out * gain / (len(notes) * 3)


def cymbal(seconds=2.0):
    n = samples(seconds)
    return highpass(noise(seconds), 4500) * decay(n, 3.0) * adsr(n, 0.002, 0.05, 1.0, 0.3) * 0.5


def tremolo(x, rate, depth):
    return x * (1 - depth + depth * (0.5 + 0.5 * np.sin(2 * np.pi * rate * np.arange(len(x)) / SR)))


# ---------------------------------------------------------------- output

def finish(x, peak=0.89, max_seconds=None):
    x = np.tanh(x / (np.max(np.abs(x)) + 1e-9) * 1.2)
    # Drop the inaudible part of the reverb tail (below -60 dB).
    loud = np.nonzero(np.abs(x) > 1e-3 * np.max(np.abs(x)))[0]
    x = x[:loud[-1] + 1]
    if max_seconds is not None:
        x = x[:samples(max_seconds)]
    fade = min(samples(0.08 if max_seconds else 0.01), len(x))
    x[-fade:] *= np.linspace(1, 0, fade)
    return x / np.max(np.abs(x)) * peak


def write_ogg(path, x, quality=4):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    try:
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", str(quality), path],
            check=True,
        )
    finally:
        os.remove(wav_path)


def loop(builder, seconds, mix=0.25, reverb_seconds=1.8, peak=0.7):
    """Renders one cycle, folds overhanging notes and the reverb tail back to the start."""
    length = samples(seconds)
    dry = builder().buf
    folded = np.zeros(length)
    for start in range(0, len(dry), length):
        chunk = dry[start:start + length]
        folded[:len(chunk)] += chunk
    wet = reverb(np.tile(folded, 2), mix, reverb_seconds)
    cycle = wet[length:2 * length]
    cycle = np.tanh(cycle / np.max(np.abs(cycle)) * 1.1)
    return cycle / np.max(np.abs(cycle)) * peak


# ---------------------------------------------------------------- sound effects

def sfx_select():
    m = Mix()
    m.add(kick(0.18, 160, 70), 0, 0.8)
    blip = additive(1100, 0.07, 9, odd_only=True) * adsr(samples(0.07), 0.002, 0.02, 0.4, 0.04)
    m.add(lowpass(blip, 3500), 0, 0.35)
    m.add(pluck(76, 0.35, brightness=1.5), 0, 0.6)
    m.add(pluck(83, 0.3, brightness=1.5), 0.03, 0.4)
    return finish(reverb(m.buf, 0.15, 0.5), 0.9)


def sfx_final_answer():
    seconds = 1.5
    n = samples(seconds)
    m = Mix()
    m.add(timpani(33, 1.5), 0, 1.2)
    rise = sine(110 * 2 ** (np.linspace(0, 2, n)), seconds) * np.linspace(0.1, 1, n) ** 2
    m.add(tremolo(rise, 9, 0.5), 0, 0.5)
    m.add(pad([45, 46, 52], seconds, attack=1.2, release=0.1, cutoff=1500), 0, 1.6)
    # Cut right when the answer is revealed (QuizViewModel.REVEAL_DELAY_MS).
    return finish(reverb(m.buf[:n], 0.2, 1.0), max_seconds=seconds)


def sfx_correct():
    m = Mix()
    for i, note in enumerate([72, 76, 79, 84]):
        m.add(bell(note, 1.0), i * 0.08, 0.45)
    m.add(brass([48, 60, 64, 67], 1.3), 0, 1.4)
    m.add(timpani(36, 1.0), 0, 0.9)
    m.add(kick(0.5, 90, 40), 0, 0.6)
    m.add(cymbal(1.2), 0, 0.25)
    return finish(reverb(m.buf, 0.3, 1.5))


def sfx_milestone():
    m = Mix()
    for i, note in enumerate([72, 76, 79, 84, 88]):
        m.add(bell(note, 1.2), i * 0.07, 0.7)
    m.add(timpani(36, 1.5), 0, 0.9)
    m.add(brass([48, 55, 60, 64, 67], 2.2), 0.05, 1.3)
    return finish(reverb(m.buf, 0.3, 2.0))


def sfx_wrong():
    seconds = 1.3
    n = samples(seconds)
    slide = midi(52) * 2 ** (-np.linspace(0, 4, n) / 12)
    buzz = additive(slide, seconds, 20) + additive(slide * 2 ** (6 / 12), seconds, 20)
    buzz = lowpass(buzz, 1200) * adsr(n, 0.01, 0.3, 0.6, 0.5)
    m = Mix().add(buzz, 0, 0.6).add(kick(0.6, 90, 35), 0, 1.0)
    return finish(reverb(m.buf, 0.2, 1.2))


def sfx_time_up():
    seconds = 0.9
    n = samples(seconds)
    tone = additive(220, seconds, 30, odd_only=True) + additive(233, seconds, 30, odd_only=True)
    tone = lowpass(tone, 5000) * adsr(n, 0.003, 0.05, 0.9, 0.12)
    m = Mix().add(tone, 0, 0.8).add(kick(0.6, 100, 36), 0, 1.0)
    return finish(reverb(m.buf, 0.18, 0.9), 0.65)


def sfx_tick():
    return finish(woodblock(), 0.6)


def sfx_lifeline():
    m = Mix()
    notes = [69, 72, 74, 76, 79, 81, 84, 86, 88, 91, 93, 96]
    for i, note in enumerate(notes):
        m.add(bell(note, 0.6), i * 0.04, 0.35)
    m.add(swept_band(noise(0.8), 800, 6000, 0.5) * adsr(samples(0.8), 0.2, 0.2, 0.5, 0.3), 0, 0.25)
    return finish(reverb(m.buf, 0.35, 1.5))


def sfx_next_question():
    seconds = 0.8
    n = samples(seconds)
    whoosh = swept_band(noise(seconds), 300, 3500, 0.7) * np.sin(np.linspace(0, np.pi, n)) ** 2
    m = Mix().add(whoosh, 0, 1.0).add(kick(0.4, 80, 40), 0.55, 0.5)
    return finish(reverb(m.buf, 0.2, 1.0), 0.7)


def sfx_game_start():
    m = Mix()
    m.add(swept_band(noise(1.0), 200, 4000, 0.6) * np.linspace(0, 1, samples(1.0)) ** 2, 0, 0.5)
    stabs = [(1.0, 0.18, [45, 57, 60, 64]), (1.25, 0.18, [45, 57, 60, 64]), (1.5, 2.0, [48, 60, 64, 67, 72])]
    for at, length, chord in stabs:
        m.add(brass(chord, length), at, 1.0)
        m.add(timpani(chord[0] - 12, 1.2), at, 0.8)
    m.add(strings([72, 76, 79], 2.0, attack=0.2), 1.5, 0.6)
    m.add(cymbal(2.5), 1.5, 0.4)
    return finish(reverb(m.buf, 0.3, 2.0))


def sfx_win():
    m = Mix()
    phrase = [(0.0, 0.35, [60, 64, 67]), (0.4, 0.2, [60, 64, 67]), (0.6, 0.55, [65, 69, 72]),
              (1.2, 0.55, [67, 71, 74]), (1.8, 2.4, [60, 64, 67, 72])]
    for at, length, chord in phrase:
        m.add(brass([c - 12 for c in chord] + chord, length), at, 1.0)
    for at in (0.0, 0.6, 1.2):
        m.add(timpani(36, 0.8), at, 0.7)
    for i in range(16):
        m.add(timpani(36, 0.3), 1.8 + i * 0.05, 0.25 * (1 - i / 16))
    for i, note in enumerate([84, 88, 91, 96, 91, 96, 100]):
        m.add(bell(note, 1.0), 1.85 + i * 0.12, 0.3)
    return finish(reverb(m.buf, 0.3, 2.2))


def sfx_walk_away():
    m = Mix()
    for i, note in enumerate([79, 76, 72, 67]):
        m.add(bell(note, 1.0), i * 0.15, 0.6)
    for i in range(10):
        m.add(highpass(noise(0.03), 5000) * decay(samples(0.03), 120), 0.05 + rng.uniform(0, 0.8), 0.25)
    return finish(reverb(m.buf, 0.3, 1.5))


# ---------------------------------------------------------------- music loops

def music_menu():
    beat = 60 / 84
    bar = beat * 4
    chords = [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62],
              [57, 60, 64], [53, 57, 60], [50, 53, 57], [52, 56, 59]]
    melody = [(0, 76, 2), (2, 74, 1), (3, 72, 1), (4, 77, 3), (7, 76, 1), (8, 79, 2), (10, 76, 2),
              (12, 74, 4), (16, 76, 2), (18, 74, 1), (19, 72, 1), (20, 77, 2), (22, 81, 2), (24, 80, 4), (28, 76, 4)]

    def build():
        m = Mix()
        for b, chord in enumerate(chords):
            at = b * bar
            m.add(strings([c - 12 for c in chord] + chord, bar + 0.6, attack=0.5), at, 1.0)
            m.add(bass(chord[0] - 24, bar), at, 0.4)
            if b % 4 == 0:
                m.add(timpani(chord[0] - 24, 1.5), at, 0.25)
            for i, note in enumerate([chord[0], chord[2], chord[1] + 12, chord[2]]):
                m.add(pluck(note + 12, 0.8, brightness=0.7), at + i * beat, 0.12)
        for beat_index, note, length in melody:
            m.add(brass([note], length * beat * 0.95), beat_index * beat, 0.3)
        return m

    return loop(build, len(chords) * bar, mix=0.32, reverb_seconds=2.6, peak=0.6)


def music_calm():
    beat = 60 / 90
    bar = beat * 4
    bars = 8
    motif = {0: [76], 2: [72], 4: [76, 77], 6: [74]}

    def build():
        m = Mix()
        for half in range(2):
            notes = [45, 52, 57] if half == 0 else [41, 48, 57]
            m.add(pad(notes, bar * 4 + 0.8, attack=1.5, release=1.0, cutoff=1200), half * bar * 4, 0.8)
        for b in range(bars):
            root = 33 if b < 4 else 29
            for i in range(8):
                m.add(bass(root, beat / 2, staccato=True), b * bar + i * beat / 2, 0.35 if i % 2 == 0 else 0.2)
            for i in range(4):
                m.add(woodblock(), b * bar + i * beat, 0.05)
            for j, note in enumerate(motif.get(b, [])):
                m.add(pluck(note, 1.2, brightness=0.6), b * bar + j * beat * 1.5, 0.3)
        return m

    return loop(build, bars * bar, mix=0.3, reverb_seconds=2.4, peak=0.55)


def music_tense():
    beat = 60 / 105
    bar = beat * 4
    bars = 8
    ostinato = [69, 72, 76, 77]

    def build():
        m = Mix()
        for b in range(bars):
            at = b * bar
            tension = (b // 2) % 2 == 1
            chord = [46, 50, 53] if tension else [45, 48, 52]
            if b % 2 == 0:
                m.add(pad(chord, bar * 2 + 0.4, attack=0.8, release=0.5, cutoff=1600), at, 0.75)
            root = 34 if tension else 33
            for i in range(16):
                m.add(bass(root, beat / 4, staccato=True), at + i * beat / 4, 0.32 if i % 4 == 0 else 0.18)
            m.add(heartbeat(), at, 0.7)
            for i in range(8):
                m.add(hat(), at + i * beat / 2, 0.1 if i % 2 else 0.06)
            for i in range(8):
                note = ostinato[i % 4] + (1 if tension and ostinato[i % 4] == 69 else 0)
                m.add(pluck(note, 0.35), at + i * beat / 2, 0.12)
        return m

    return loop(build, bars * bar, mix=0.25, reverb_seconds=1.8, peak=0.55)


def music_intense():
    beat = 60 / 72
    bar = beat * 4
    bars = 6

    def build():
        m = Mix()
        low = strings([33, 45, 46], bars * bar + 0.5, attack=1.5, cutoff=1400)
        m.add(tremolo(low, 11, 0.5), 0, 1.0)
        high = strings([81, 82], bars * bar + 0.5, attack=2.5, cutoff=5000)
        m.add(high, 0, 0.18)
        for b in range(bars):
            at = b * bar
            m.add(heartbeat(), at, 1.0)
            m.add(heartbeat(), at + 2 * beat, 0.8)
            for i in range(4):
                m.add(woodblock(), at + i * beat + beat / 2, 0.08)
            if b % 2 == 1:
                for i in range(12):
                    m.add(timpani(33, 0.3), at + 2 * beat + i * beat / 6, 0.12 + 0.02 * i)
        rise_len = 2 * bar
        n = samples(rise_len)
        rise = strings([57], rise_len, attack=0.3, cutoff=4000)
        bend = additive(midi(57) * 2 ** (np.linspace(0, 5, n) / 12), rise_len, 10, rolloff=1.3)
        m.add((rise + 0.3 * lowpass(bend, 2500)) * np.linspace(0, 1, n) ** 2, (bars - 2) * bar, 0.35)
        return m

    return loop(build, bars * bar, mix=0.3, reverb_seconds=2.4, peak=0.55)


SOUNDS = {
    "sfx_select": sfx_select,
    "sfx_final_answer": sfx_final_answer,
    "sfx_correct": sfx_correct,
    "sfx_milestone": sfx_milestone,
    "sfx_wrong": sfx_wrong,
    "sfx_time_up": sfx_time_up,
    "sfx_tick": sfx_tick,
    "sfx_lifeline": sfx_lifeline,
    "sfx_next_question": sfx_next_question,
    "sfx_game_start": sfx_game_start,
    "sfx_win": sfx_win,
    "sfx_walk_away": sfx_walk_away,
    "music_menu": music_menu,
    "music_calm": music_calm,
    "music_tense": music_tense,
    "music_intense": music_intense,
}


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
    os.makedirs(out_dir, exist_ok=True)
    for name, make in SOUNDS.items():
        audio = make()
        write_ogg(os.path.join(out_dir, f"{name}.ogg"), audio, quality=3 if name.startswith("music") else 4)
        print(f"{name}: {len(audio) / SR:.2f}s")


if __name__ == "__main__":
    main()
