"""Enregistre docs/demo.gif en pilotant l'interface web avec Playwright.

Prérequis : l'application tourne sur le port 8080 (avec Ollama et la base).
Lancement (depuis la racine du projet) :

    docker run --rm --add-host=host.docker.internal:host-gateway -v "$PWD/docs:/docs" \
        mcr.microsoft.com/playwright/python:v1.55.0-noble \
        sh -c "pip install -q pillow && python /docs/record_demo.py"
"""
import io
import os
import time

from PIL import Image
from playwright.sync_api import sync_playwright

URL = os.environ.get("DEMO_URL", "http://host.docker.internal:8080")
OUTPUT = os.environ.get("DEMO_OUTPUT", "/docs/demo.gif")
WIDTH, HEIGHT = 900, 780

# Une conversation qui montre : procédure + source, incident en cours, création de ticket après accord.
SCENARIO = [
    "Mon VPN ne se connecte plus, je suis à l'hôtel",
    "Et mes mails restent bloqués dans la boîte d'envoi Outlook",
    "Autre chose : mon clavier Bluetooth se déconnecte toutes les dix minutes",
    "Oui, créez le ticket",
]

frames = []  # (image, durée en ms)


def capture(page, duration):
    png = page.screenshot()
    image = Image.open(io.BytesIO(png)).convert("RGB")
    frames.append((image, duration))


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": WIDTH, "height": HEIGHT}, device_scale_factor=1)
        page.goto(URL)
        capture(page, 1200)
        for message in SCENARIO:
            page.click("#input")
            for i, char in enumerate(message):
                page.keyboard.type(char)
                if i % 4 == 3:
                    capture(page, 60)
            capture(page, 500)
            page.keyboard.press("Enter")
            # Pendant que le modèle répond : quelques images seulement, affichées vite
            waited = 0
            while page.is_disabled("#send"):
                time.sleep(0.25)
                waited += 1
                if waited % 8 == 1:
                    capture(page, 250)
            page.wait_for_timeout(300)
            # La réponse reste affichée le temps de la lire
            capture(page, 4500)
        browser.close()

    images = [f[0] for f in frames]
    durations = [f[1] for f in frames]
    palette_images = [im.quantize(colors=128, method=Image.Quantize.MEDIANCUT) for im in images]
    palette_images[0].save(OUTPUT, save_all=True, append_images=palette_images[1:], duration=durations,
                           loop=0, optimize=True, disposal=1)
    print(f"{OUTPUT} : {len(frames)} images, {os.path.getsize(OUTPUT) // 1024} Ko")


if __name__ == "__main__":
    main()
