# Mexo Music — personal music player app (v3)

Search apni API (https://mexomusic.netlify.app, YouTube Music search) se aata hai,
aur gaana app ke andar se YouTube se bajta hai (NewPipeExtractor). Sirf personal use ke liye.

**v3 update lagana ho to pehle `V3_UPDATE_NOTES.md` padho — usme file-by-file steps hain.**

## APK kaise banega — GitHub Actions se (phone se)

1. GitHub par naya repo banao, naam: `mexo-music`
2. Is zip ko extract karke **saari files aur folders** repo me upload karo.
   - ⚠️ `.github` folder sabse zaroori hai — yahi APK banata hai. Ye folder phone par kabhi-kabhi chhupa (hidden) rehta hai; file manager me "hidden files dikhao" on karke dekho.
   - Agar `.github` folder upload na ho paye to GitHub website par: **Add file → Create new file** → naam me ye likho: `.github/workflows/build.yml` → andar ka content isi zip ki usi file se copy-paste kardo. Folder khud ban jayega.
3. Upload (push) hote hi **Actions** tab me build apne aap shuru ho jayega. Agar na ho to Actions tab → **Build APK** → **Run workflow** dabao.
4. 3–5 minute me build complete hoga (green tick ✅). Us build ko kholo → neeche **Artifacts** me **MexoMusic-APK** milega → download karo → andar `app-debug.apk` hai.
5. APK install karo. Pehli baar "unknown apps install allow" ki permission mangega — apne browser/file manager ko allow kardo (ye tumhara khud ka banaya app hai).

## Build fail ho jaye to?

Actions me laal ❌ wale build ko kholo, jo error dikhe uska screenshot bhej do — fix ho jayega.

## App me kya-kya hai

- Search: gaana ya singer likho, list aa jayegi (photo ke saath) — YouTube Music se
- Kisi gaane par tap = play — stream link app ke andar se YouTube se nikalta hai, sabse achhi audio quality apne aap chunti hai
- Play/Roko, Agla/Pichhla button, seekbar (gaana aage-peeche)
- Gaana khatam hote hi agla apne aap
- API ka URL badalna ho to: `app/src/main/java/com/mexo/music/MainActivity.java` me sabse upar `BASE_URL` wali ek hi line hai

## Abhi ke liye ek kami

App band (close) karne par gaana ruk jata hai. Background/notification wala play agle version me aayega.
