"""Original four-second fountain noise. Authoring only: numpy and soundfile required.

No recordings or third-party audio are used. Generate offline, never in Minecraft.
"""
from pathlib import Path
import numpy as np
import soundfile as sf

RATE = 44100
SECONDS = 4
TARGET = Path(__file__).resolve().parents[1] / "src/main/resources/assets/daedalon/sounds/fountain/steady.ogg"


def generate():
    rng = np.random.default_rng(20260908)
    count = RATE * SECONDS
    frequencies = np.fft.rfftfreq(count, 1 / RATE)
    # A close splashing hiss: remove river-like low rumble and harsh top-end fizz.
    shape = (frequencies / (frequencies + 330)) ** 2
    shape /= np.sqrt(np.maximum(frequencies, 450))
    shape *= np.exp(-(frequencies / 7500) ** 4)
    water = np.fft.irfft(np.fft.rfft(rng.normal(size=count)) * shape, n=count)
    water /= np.std(water)
    # Dense, quiet micro-bubbles add water texture without distinct rhythmic drips.
    bubbles = np.zeros(count)
    for _ in range(1000):
        start = int(rng.integers(count))
        length = int(rng.uniform(.008, .025) * RATE)
        time = np.arange(length) / RATE
        frequency = rng.uniform(900, 4300)
        envelope = np.sin(np.linspace(0, np.pi, length)) ** 2
        grain = np.sin(2 * np.pi * frequency * time * (1 + 5 * time)) * envelope
        bubbles[(start + np.arange(length)) % count] += grain
    water += .13 * bubbles
    # Remove slow swells; circular smoothing also treats the loop join consistently.
    smoothing = np.exp(-2 * (np.pi * frequencies * .06) ** 2)
    energy = np.fft.irfft(np.fft.rfft(water * water) * smoothing, n=count)
    water /= np.sqrt(np.maximum(energy, .01))
    water = np.tanh(water * .19)
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    sf.write(TARGET, water, RATE, format="OGG", subtype="VORBIS")
    decoded, rate = sf.read(TARGET)
    levels = [20 * np.log10(np.sqrt(np.mean(chunk * chunk))) for chunk in np.array_split(decoded, 16)]
    assert rate == RATE and len(decoded) == count
    assert np.max(np.abs(decoded)) < .95
    assert max(levels) - min(levels) < 1.0, levels
    print(f"4.000 s mono; quarter-second loudness spread {max(levels)-min(levels):.2f} dB; {TARGET.stat().st_size} bytes")


if __name__ == "__main__":
    generate()
