# مالية البيت — تطبيق أندرويد

التطبيق كله في `app/src/main/assets/index.html`، والكود ده بيفتحه في شاشة كاملة على الموبايل.
البيانات بتتحفظ على الموبايل نفسه. خد نسخة احتياطية من الإعدادات كل فترة.

## تنزيل الـ APK
كل مرة يتعمل push على `main`، GitHub Actions بيبني التطبيق وبينزّله في صفحة **Releases** بتاعة الـ repo.
افتح آخر Release من الموبايل، ونزّل ملف `HouseholdFinance-1.0.X.apk`، وافتحه عشان تثبته.

## تحديثات من غير ما تمسح التطبيق (اختياري)
من غير مفتاح توقيع ثابت، كل نسخة جديدة بتتوقّع بمفتاح مختلف، فلازم تمسح القديمة قبل ما تثبت الجديدة، وده بيمسح بياناتك (خد نسخة احتياطية الأول).
عشان التحديثات تتثبت فوق القديمة، ضيف secrets في Settings ← Secrets and variables ← Actions:
- `KS_B64`: ملف keystore متحوّل لـ base64
- `KS_PASS`: الباسورد بتاعه (نفس الباسورد للـ store والـ key، والـ alias اسمه `householdfinance`)

تقدر تعمل الـ keystore بالأمر ده:
`keytool -genkeypair -keystore release.jks -alias householdfinance -keyalg RSA -keysize 2048 -validity 10000`
وبعدين: `base64 -w0 release.jks`

## البناء من Android Studio
Open ← اختار الفولدر ده ← Build ← Build APK(s).
