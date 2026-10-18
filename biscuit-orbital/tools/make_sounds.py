#!/usr/bin/env python3
"""
Biscuit Orbital - procedural sound generator.

Synthesises every custom sound effect with numpy/scipy, encodes them to mono OGG Vorbis
with ffmpeg and writes assets/biscuit_orbital/sounds.json.

Usage:  python3 tools/make_sounds.py
Needs:  numpy, scipy, ffmpeg (with libvorbis)

Want to swap a sound for a real recording? Just drop your own mono .ogg with the same
file name into src/main/resources/assets/biscuit_orbital/sounds/ (and don't re-run this).
"""
import json
import os
import subprocess
import tempfile
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
rng = np.random.default_rng(20261017)

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/biscuit_orbital"
OUT = ASSETS / "sounds"
OUT.mkdir(parents=True, exist_ok=True)
WAV = Path(tempfile.mkdtemp(prefix="biscuit_wav_"))


# ----------------------------------------------------------------------------- helpers
def N(d):
    return int(round(SR * d))


def T(d):
    return np.arange(N(d)) / SR


def white(d):
    return rng.standard_normal(N(d))


def lp(x, fc, o=4):
    return signal.sosfilt(signal.butter(o, fc, "low", fs=SR, output="sos"), x)


def hp(x, fc, o=4):
    return signal.sosfilt(signal.butter(o, fc, "high", fs=SR, output="sos"), x)


def bp(x, lo, hi, o=3):
    return signal.sosfilt(signal.butter(o, [lo, hi], "band", fs=SR, output="sos"), x)


def ex(t, tau):
    return np.exp(-t / tau)


def atk(t, a):
    return np.minimum(1.0, t / a)


def phase_from(f):
    return 2 * np.pi * np.cumsum(f) / SR


def sinf(f):
    return np.sin(phase_from(f))


def place(dst, src, start, gain=1.0):
    i = N(start)
    if i >= len(dst):
        return
    j = min(len(dst), i + len(src))
    dst[i:j] += src[: j - i] * gain


def clicks(d, dens, burstlen=180, tau=30, hpf=1500):
    """Random crackle / debris: impulse train convolved with a tiny noise burst."""
    n = N(d)
    imp = (rng.random(n) < dens) * rng.standard_normal(n)
    b = rng.standard_normal(burstlen) * np.exp(-np.arange(burstlen) / tau)
    return hp(signal.fftconvolve(imp, b)[:n], hpf)


def reverb(x, tail=1.5, mix=0.3, damp=4000):
    n = N(tail)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-6.9 * t / tail)
    ir = lp(ir, damp, 2)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    xp = np.concatenate([x, np.zeros(n)])
    wet = signal.fftconvolve(xp, ir)[: len(xp)]
    return xp * (1 - mix) + wet * mix * 1.6


def soft(x, drive=1.8):
    return np.tanh(x * drive) / np.tanh(drive)


STATS = []


def finish(x, name, peak=0.93, fin=0.003, fout=0.04):
    x = np.asarray(x, dtype=np.float64)
    x = x - np.mean(x)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    fi, fo = N(fin), N(fout)
    x[:fi] *= np.linspace(0, 1, fi)
    x[-fo:] *= np.linspace(1, 0, fo)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    wp = WAV / f"{name}.wav"
    wavfile.write(str(wp), SR, pcm)
    op = OUT / f"{name}.ogg"
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", str(wp), "-ac", "1", "-ar", str(SR),
         "-c:a", "libvorbis", "-q:a", "5", str(op)],
        check=True,
    )
    STATS.append((name, len(x) / SR, float(np.max(np.abs(x))), float(np.sqrt(np.mean(x ** 2))),
                  os.path.getsize(op)))


# ----------------------------------------------------------------------------- the sounds
def uplink_open():
    """Radio squelch + two beeps: the walkie-talkie connecting."""
    d = 0.62
    out = np.zeros(N(d))
    t = T(0.17)
    place(out, bp(white(0.17), 350, 3400) * ex(t, 0.05) * atk(t, 0.003), 0.0)
    for st, f in [(0.15, 1480), (0.27, 1976)]:
        t2 = T(0.10)
        place(out, np.sin(2 * np.pi * f * t2) * atk(t2, 0.004) * ex(t2, 0.055), st, 0.55)
    t3 = T(0.12)
    place(out, hp(white(0.12), 1800) * ex(t3, 0.03), 0.42, 0.35)
    return out


def uplink_lock():
    """Four-step digital 'target locked' chirp."""
    d = 0.7
    out = np.zeros(N(d))
    for k, f in enumerate([880, 1175, 1568, 2093]):
        t = T(0.075)
        sq = signal.square(2 * np.pi * f * t) * 0.35 + np.sin(2 * np.pi * f * t) * 0.65
        place(out, lp(sq, 6000) * atk(t, 0.003) * ex(t, 0.07), 0.02 + k * 0.09, 0.6)
    t = T(0.08)
    place(out, bp(white(0.08), 1200, 5000) * ex(t, 0.03), 0.0, 0.3)
    t = T(0.2)
    place(out, np.sin(2 * np.pi * 2093 * t) * ex(t, 0.09) * atk(t, 0.004), 0.40, 0.5)
    return out


def beep():
    """Countdown blip (pitched up/down in-game)."""
    t = T(0.13)
    return (np.sin(2 * np.pi * 1200 * t) + 0.3 * np.sin(2 * np.pi * 2400 * t)) * atk(t, 0.003) * ex(t, 0.045)


def railgun_charge():
    """3.5s rising electric whine that peaks right before the shot."""
    d = 3.75
    t = T(d)
    u = np.clip(t / 3.55, 0, 1)
    f = 90 * (3300 / 90) ** (u ** 1.5)
    ph = phase_from(f)
    tone = sum(np.sin(h * ph) / h for h in range(1, 6))
    buzz = 0.55 + 0.45 * np.sin(2 * np.pi * (35 + 170 * u) * t)
    x = tone * (u ** 2.2) * buzz * 0.55
    x += hp(white(d), 2500) * (u ** 1.8) * 0.28
    x += np.sin(2 * np.pi * 52 * t) * (u ** 1.1) * 0.30
    x += clicks(d, 0.0002 + 0.0045 * u ** 2, 120, 22, 2500) * (0.3 + 0.7 * u) * 2.0
    x *= np.where(t > 3.55, np.exp(-(t - 3.55) / 0.03), 1.0)
    return x


def railgun_fire():
    """Crack + sub thump + descending whistle + electric sizzle + long tail."""
    d = 4.3
    t = T(d)
    crack = hp(white(d), 700) * ex(t, 0.018) * 1.2
    thump = sinf(26 + 120 * ex(t, 0.22)) * ex(t, 0.55) * 1.5
    body = lp(white(d) * ex(t, 0.85), 2600) * 0.9
    whistle = sinf(260 + 3400 * ex(t, 0.45)) * ex(t, 0.6) * 0.35 * atk(t, 0.01)
    zap = bp(white(d), 3000, 9500) * ex(t, 0.14) * 0.55
    crackle = clicks(d, 0.0016 * ex(t, 1.2), 150, 25, 2500) * 1.5
    x = reverb(crack + thump + body + whistle + zap + crackle, 2.6, 0.45, 5000)
    return soft(x, 1.6)


def siren():
    """Air-raid siren, 3 slow wails."""
    d = 4.8
    t = T(d)
    lfo = 0.5 - 0.5 * np.cos(2 * np.pi * t / 1.6)
    ph = phase_from(420 + 470 * lfo)
    x = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph)
    x = lp(x, 3600) * atk(t, 0.2) * np.clip((d - t) / 0.35, 0, 1)
    return reverb(x, 0.9, 0.22, 3500)


def bomb_whistle():
    """Falling-bomb whistle (descending, getting louder)."""
    d = 1.45
    t = T(d)
    ph = phase_from(2900 * (620 / 2900) ** (t / d))
    x = (np.sin(ph) + 0.28 * np.sin(2 * ph)) * 0.6 + bp(white(d), 1800, 4800) * 0.22
    return x * (0.25 + 0.75 * (t / d) ** 1.2) * atk(t, 0.03)


def bomb_boom():
    d = 3.0
    t = T(d)
    crack = hp(white(d), 600) * ex(t, 0.012) * 1.1
    thump = sinf(30 + 75 * ex(t, 0.18)) * ex(t, 0.30) * 1.5
    rumble = lp(white(d), 750) * ex(t, 0.5)
    debris = clicks(d, 0.0022 * ex(t, 0.7), 160, 28, 1800) * 1.3
    return soft(reverb(crack + thump + rumble + debris, 1.8, 0.35, 4500), 1.8)


def meteor_whoosh():
    """Fireball screaming down: noise bands sweeping up, swell, rumble."""
    d = 2.4
    t = T(d)
    u = t / d
    w = white(d)
    low, mid, high = bp(w, 160, 520), bp(w, 520, 1500), bp(w, 1500, 4200)
    g_low = np.exp(-((u - 0.35) / 0.30) ** 2) + 0.2
    g_mid = np.exp(-((u - 0.55) / 0.25) ** 2)
    g_high = np.exp(-((u - 0.70) / 0.18) ** 2) * 0.7
    env = np.sin(np.pi * np.clip(u, 0, 1) ** 0.75) ** 1.6
    x = (low * g_low + mid * g_mid + high * g_high) * env
    x += np.sin(2 * np.pi * 58 * t) * env * (0.5 + 0.5 * np.sin(2 * np.pi * 17 * t)) * 0.5
    return x


def meteor_impact():
    d = 3.9
    t = T(d)
    crack = hp(white(d), 500) * ex(t, 0.02) * 1.1
    thump = sinf(24 + 70 * ex(t, 0.22)) * ex(t, 0.55) * 1.6
    rumble = lp(white(d), 520) * ex(t, 0.95) * 1.1
    roar = lp(white(d), 1700) * ex(t, 0.35) * 0.7
    debris = clicks(d, 0.003 * ex(t, 1.3), 180, 32, 1200) * 1.6
    return soft(reverb(crack + thump + rumble + roar + debris, 2.4, 0.4, 4000), 1.8)


def storm_charge():
    """Electric hum climbing in pitch with growing crackle."""
    d = 3.45
    t = T(d)
    u = np.clip(t / 3.2, 0, 1)
    ph = phase_from(55 * (230 / 55) ** u)
    tone = np.sin(ph) + 0.6 * np.sin(2 * ph) + 0.4 * np.sin(3 * ph) + 0.25 * np.sin(5 * ph)
    x = tone * (u ** 1.4) * 0.5
    x += hp(white(d), 4200) * (u ** 1.6) * 0.2
    x += clicks(d, 0.0003 + 0.007 * u ** 2, 140, 20, 2500) * (0.3 + 0.7 * u) * 2.2
    x *= np.where(t > 3.2, np.exp(-(t - 3.2) / 0.025), 1.0)
    return x


def thunder_crack():
    d = 3.3
    t = T(d)
    crack = (hp(white(d), 1500) * ex(t, 0.028) + 0.5 * hp(white(d), 4000) * ex(t, 0.11)) * 1.1
    sizzle = bp(white(d), 2500, 9500) * ex(t, 0.25) * 0.35
    rum = lp(white(d), 270) * ex(t, 1.4) * (0.65 + 0.35 * np.sin(2 * np.pi * 3.1 * t)) * atk(t, 0.05) * 1.4
    return soft(reverb(crack + sizzle + rum, 2.2, 0.4, 5000), 1.8)


def singularity_hum():
    """Deep beating drone, swirling noise bands and a slow heartbeat."""
    d = 5.3
    t = T(d)
    w = white(d)
    drone = ((np.sin(2 * np.pi * 38 * t) + np.sin(2 * np.pi * 39.4 * t)) * 0.6
             + 0.25 * np.sin(2 * np.pi * 76 * t) + 0.12 * np.sin(2 * np.pi * 113 * t))
    sw = (bp(w, 150, 320) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.35 * t))
          + bp(w, 500, 950) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.55 * t + 1))
          + 0.4 * bp(w, 1200, 2300) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.8 * t + 2)))
    heart = np.exp(-(t % 0.8) / 0.11) * np.sin(2 * np.pi * 46 * t) * 0.55
    x = drone * 0.8 + sw * 0.3 + heart
    return x * atk(t, 0.6) * np.clip((d - t) / 0.9, 0, 1)


def singularity_collapse():
    """2.4s reverse-swell suction, a beat of silence, then the boom."""
    d = 5.6
    out = np.zeros(N(d))
    bd = 2.4
    tb = T(bd)
    u = tb / bd
    w = white(bd)
    build = ((bp(w, 150, 500) * (1 - u) + bp(w, 500, 1600) * np.sin(np.pi * u) + bp(w, 1600, 5000) * u)
             * (u ** 2.2) + sinf(60 * (950 / 60) ** u) * (u ** 2.6) * 0.5)
    place(out, build, 0.0)
    d2 = 3.1
    t2 = T(d2)
    boom = (hp(white(d2), 500) * ex(t2, 0.03) * 1.1 + sinf(22 + 90 * ex(t2, 0.2)) * ex(t2, 0.7) * 1.6
            + lp(white(d2), 500) * ex(t2, 1.0) * 1.1)
    place(out, boom, 2.45)
    return soft(reverb(out, 2.4, 0.3, 4500), 1.7)


def cryo_incoming():
    """Icy descending shriek with bell-like inharmonic partials."""
    d = 2.7
    t = T(d)
    u = t / d
    ph = phase_from(3700 * (950 / 3700) ** u * (1 + 0.04 * np.sin(2 * np.pi * 6.5 * t)))
    x = np.sin(ph) * 0.6 + np.sin(ph * 1.51) * 0.25 + np.sin(ph * 2.76) * 0.2
    x += hp(white(d), 6000) * 0.22 * (0.3 + 0.7 * u)
    x *= (0.2 + 0.8 * u ** 1.5) * atk(t, 0.05)
    return reverb(x, 1.0, 0.25, 6000)


def ice_shatter():
    d = 2.5
    t = T(d)
    out = hp(white(d), 2400) * ex(t, 0.17) * 0.9 + hp(white(d), 6000) * ex(t, 0.08) * 0.5
    out += sinf(60 + 60 * ex(t, 0.04)) * ex(t, 0.07) * 0.8
    for _ in range(80):
        st = rng.uniform(0, 1.5)
        f = rng.uniform(2500, 9000)
        tau = rng.uniform(0.02, 0.12)
        a = rng.uniform(0.15, 0.7)
        tt = T(0.35)
        place(out, np.sin(2 * np.pi * f * tt) * ex(tt, tau) * atk(tt, 0.001), st, a * 0.5)
    return reverb(out, 1.4, 0.3, 7000)


def fire_roar():
    """Roaring firestorm: brown noise, flutter, crackle."""
    d = 3.5
    t = T(d)
    w = white(d)
    brown = hp(np.cumsum(w), 40, 2)
    brown /= np.max(np.abs(brown)) + 1e-9
    base = lp(brown, 1300) * 1.5 + lp(w, 900) * 0.3
    mod = 0.65 + 0.35 * np.sin(2 * np.pi * (5 + 3 * np.sin(2 * np.pi * 0.4 * t)) * t)
    swell = 0.35 + 0.65 * np.sin(np.pi * np.clip(t / d, 0, 1)) ** 0.7
    x = base * mod * swell + clicks(d, 0.004, 120, 20, 1500) * 0.9
    return x * atk(t, 0.15) * np.clip((d - t) / 0.5, 0, 1)


def nuke_boom():
    """Huge sub-bass detonation with mountain-echoes and a long debris tail."""
    d = 9.6
    t = T(d)
    crack = hp(white(d), 400) * ex(t, 0.05)
    thump = sinf(17 + 60 * ex(t, 0.35)) * ex(t, 1.8) * 1.5
    rumble = (lp(white(d), 380) * ex(t, 3.2)
              * (0.7 + 0.3 * np.sin(2 * np.pi * (3 + 1.2 * np.sin(2 * np.pi * 0.2 * t)) * t)) * 1.2)
    roar = lp(white(d), 1500) * ex(t, 0.9) * 0.6
    core = crack + thump + rumble + roar
    out = core.copy()
    for st, g in [(1.15, 0.5), (2.2, 0.3), (3.6, 0.2)]:
        seg = core[: N(2.2)] * np.linspace(1, 0, N(2.2)) ** 1.5
        place(out, lp(seg, 700), st, g)
    out += clicks(d, 0.0035 * ex(t, 2.2), 200, 35, 1100) * 1.4
    return soft(reverb(out, 3.0, 0.4, 3500), 1.7)


SOUNDS = {
    "uplink_open": (uplink_open, "Biscuit Uplink connects"),
    "uplink_lock": (uplink_lock, "Orbital target locked"),
    "beep": (beep, "Countdown beep"),
    "railgun_charge": (railgun_charge, "Railgun charges"),
    "railgun_fire": (railgun_fire, "Orbital railgun fires"),
    "siren": (siren, "Air raid siren"),
    "bomb_whistle": (bomb_whistle, "Bomb whistles"),
    "bomb_boom": (bomb_boom, "Bomb explodes"),
    "meteor_whoosh": (meteor_whoosh, "Meteor screams down"),
    "meteor_impact": (meteor_impact, "Meteor impacts"),
    "storm_charge": (storm_charge, "Storm charges"),
    "thunder_crack": (thunder_crack, "Thunder cracks"),
    "singularity_hum": (singularity_hum, "Singularity hums"),
    "singularity_collapse": (singularity_collapse, "Singularity collapses"),
    "cryo_incoming": (cryo_incoming, "Ice comet shrieks"),
    "ice_shatter": (ice_shatter, "Ice shatters"),
    "fire_roar": (fire_roar, "Firestorm roars"),
    "nuke_boom": (nuke_boom, "Nuclear detonation"),
}

if __name__ == "__main__":
    sounds_json = {}
    for name, (fn, subtitle) in SOUNDS.items():
        finish(fn(), name)
        sounds_json[name] = {
            "sounds": [{"name": f"biscuit_orbital:{name}"}],
            "subtitle": f"subtitles.biscuit_orbital.{name}",
        }
    with open(ASSETS / "sounds.json", "w", encoding="utf-8") as fh:
        json.dump(sounds_json, fh, indent=2)
    print(f"{'sound':24s} {'sec':>5s} {'peak':>5s} {'rms':>6s} {'ogg kB':>7s}")
    for name, dur, pk, rms, size in STATS:
        print(f"{name:24s} {dur:5.2f} {pk:5.2f} {rms:6.3f} {size / 1024:7.1f}")
    print("total ogg kB:", round(sum(s[4] for s in STATS) / 1024, 1))
