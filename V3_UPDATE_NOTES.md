# Mexo Music v3 Update — YouTube se bajega 🎵

## Kya badla hai is update me?

- Ab gaana **YouTube se bajega** — JioSaavn wala purana tareeka (`/api/song`, `/api/stream`) pura hata diya
- Search pehle jaisa hi tumhari Netlify API (mexomusic.netlify.app) se aayega — wo already live hai aur app me chal raha hai
- Gaane ka play link **app ke andar se** nikalega (NewPipeExtractor library se) — isliye koi API dobara host nahi karni, kuch aur deploy nahi karna
- Koi gaana na baj paye to ab hourglass (⏳) atakega nahi — button wapas ▶ ho jayega aur message aayega: "Ye gaana nahi baj paya, dusra try karo"

## Repo me kaunsi files badalni hain? (mexo-music repo)

Is update zip me sirf ye files hain — inhi ko repo me same jagah par replace/add karna hai:

1. `app/src/main/java/com/mexo/music/MainActivity.java` — replace
2. `app/src/main/java/com/mexo/music/YtStreamResolver.java` — **nayi file**, same folder me add
3. `app/build.gradle` — replace
4. `settings.gradle` — replace
5. `README.md` — replace
6. `V3_UPDATE_NOTES.md` — ye wali file, root me add (sirf jaankari ke liye)

Baaki sab files (layout, icon, workflow) waise ke waise rahenge — unhe mat chhuo.

## Phir kya karna hai?

1. Files replace karke GitHub par push/commit kar do — **Actions** tab me build apne aap shuru ho jayega
2. Build thoda zyada time le sakta hai is baar (nayi library download hogi) — green tick ✅ ka wait karo
3. Green tick aaye to build kholo → **Artifacts** → **MexoMusic-APK** download → `app-debug.apk` install karo
   - Purane app ke upar hi install ho jayega, history/playlist nahi udegi
   - APK ka size pehle se bada hoga (lagbhag 10–15 MB) — ye normal hai, andar YouTube wali library hai
4. Build laal ❌ ho jaye to error ka screenshot bhej dena — fix ho jayega

## Test kaise karna hai?

1. App kholo, koi gaana search karo (jaise wahi jo pehle search kiya tha)
2. Gaane par tap karo — thoda sa ⏳ aayega (link phone se nikal raha hai, 3–8 second lag sakte hain), phir gaana bajna chahiye ▶
3. Agla/pichhla button, seekbar aur gaana khatam hone par auto-next bhi check kar lena

## Ek sachhi baat

YouTube kabhi-kabhi kuch gaano par extra check (PO token) mangta hai — aisa koi gaana na baje to app atakega nahi, message deke ruk jayega. Dusra gaana try karna. Zyada tar gaane bajne chahiye — phone ke apne net se link nikalta hai, isiliye ye tareeka chalta hai, server wala nahi chala tha.
