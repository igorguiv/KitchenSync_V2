# KitchenSync — одноразове налаштування хмари (≈10 хв, робить лише власник)
1. https://console.firebase.google.com → «Add project» (Analytics вимкнути).
2. Build → Firestore Database → Create database → будь-який регіон → «Start in production mode».
3. Вкладка Rules → вставити і Publish:
   rules_version = '2';
   service cloud.firestore { match /databases/{db}/documents { match /fridges/{doc=**} { allow read, write: if true; } } }
4. Project settings → Add app → Android → package `com.kitchensync.app` → завантажити `google-services.json`.
5. Закодувати в base64 (на ПК: `base64 -w0 google-services.json`, або онлайн-кодувальник).
6. GitHub → репозиторій → Settings → Secrets and variables → Actions → New secret: `GOOGLE_SERVICES_JSON` = base64-рядок.
7. Actions → Build APK → Run workflow → APK у Artifacts. Родичам просто даєте APK — їм нічого налаштовувати не треба.
