# Biscuit Orbital Command — Minecraft 26.3 (Fabric)

Naya client + server mod. Purane 1.20.1 jar se alag hai: isme **camera cinematic, HUD, custom GUI aur sounds** hain.

## PART 1 — Jar banwana (sab browser mein, PC pe kuch install nahi karna)

1. **github.com** kholo → *Sign up* (free). Email verify karo.
2. Upar right mein **+** → **New repository**. Naam: `biscuit-orbital`. **Private** chuno → **Create repository**.
3. Ye zip **Extract All** karo. Andar ka folder kholo — isme `build.gradle`, `gradle.properties`, `settings.gradle`, `src`, `tools`, `.github` dikhenge.
   - `.github` na dikhe to File Explorer → *View* → *Show* → **Hidden items** on karo.
4. GitHub repo page par **"uploading an existing file"** link dabao. Folder ke **andar ki saari files/folders select karke** (folder khud nahi) browser mein drag-drop karo. Upload khatam hone par neeche green **Commit changes** dabao.
5. Check karo ki repo mein `.github/workflows/build.yml` dikh rahi hai. Agar nahi dikhi:
   *Add file → Create new file* → naam mein `.github/workflows/build.yml` type karo (slash type karte hi folder ban jata hai) → neeche wala text paste karo → *Commit*.
   ```yaml
   name: build-mod
   on:
     push:
     workflow_dispatch:
   jobs:
     build:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v4
         - uses: actions/setup-java@v4
           with:
             distribution: temurin
             java-version: 25
         - uses: gradle/actions/setup-gradle@v4
           with:
             gradle-version: current
         - name: Build the mod
           run: gradle build --no-daemon --stacktrace
         - uses: actions/upload-artifact@v4
           with:
             name: biscuit-orbital-jar
             path: build/libs/*.jar
   ```
6. Repo ke **Actions** tab par jao. `build-mod` chalega (peela circle). 5-10 minute lagte hain.
7. **Green tick** aaye to us run par click karo → sabse neeche **Artifacts** → `biscuit-orbital-jar` download karo → zip extract → `biscuit-orbital-2.0.0.jar` milega.
8. **Red cross** aaye to: run kholo → **build** → **Build the mod** step expand karo → **last 50-60 lines copy karke mujhe bhej do**. Pehli baar compile errors aa sakte hain, ye normal hai (neeche "Honest limits" dekho). Main fix karke naya zip dunga, bas wahi files dobara upload karni hongi.

## PART 2 — Game mein lagana

- Minecraft **26.3 + Fabric Loader** (tumhare paas video mein already hai).
- `mods` folder mein daalo: **Fabric API (26.3 wala)** + ye jar.
- **Purana `biscuit-orbital` 1.20.1 jar hata do.**

## PART 3 — Use kaise karna hai

| Kya | Kaise |
|---|---|
| Remote lena | `/give @s biscuit_orbital:uplink_remote` |
| Strike chalana | Remote hath mein, **right-click** jis block ko dekh rahe ho (range = tumhara render distance, max ~480 blocks) |
| Menu kholna | **H** dabao, ya remote se **Shift + right-click** |
| Settings | Sliders drag karo: **Radius**, **Damage** (right end = ∞), **Speed**. Toggles: **Cutscenes**, **Play for everyone**. Auto-save hote hain |
| Menu band | **Esc** |

Cinematic: lock beam → badalon ke paar uthna → Earth → solar system → galaxy → railgun station (title card) → fire → white flash → crater ke upar camera orbit. Poora ~40 sec (Speed slider se tez/dheema).

**Warning:** strike zameen ko **world top se void tak** kaat deta hai. Pehle world ka **backup** lo. Pehli test ke liye Radius 96 hi rakho. Bada radius = zyada time aur lag.

## Honest limits (seedhi baat)

- Mujhe is project ko compile ya game mein chalane ka access nahi tha. API names Fabric ke 26.2 docs se verify kiye hain, par 26.3 mein chhote badlav ho sakte hain. Isliye pehle build mein compile error aana possible hai.
- Visuals (camera, HUD, GUI) pehli baar tumhare game mein hi dikhenge. Video bhejo, main tune karunga.
- Abhi **sirf 1 strike (Railgun)** hai. Baaki 5 GUI mein "locked / phase 2" dikhte hain.
- Space art (Earth, galaxy, station, sounds) sab **original** hain, mere banaye hue. Reference video ke kisi asset ya naam ka use nahi hua.
