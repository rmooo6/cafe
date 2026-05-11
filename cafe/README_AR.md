# Cafe Screen Local

تطبيق Android TV / TV Box للكافي يعمل بدون إنترنت وبدون Google Play.

## ماذا يفعل؟

- يعرض الصور والفيديوهات على الشاشة Full Screen.
- يفتح سيرفر محلي داخل الشاشة على المنفذ 8080.
- صاحب الكافي يفتح الرابط من هاتفه، ويرفع الصور والفيديوهات من المتصفح.
- الملفات تحفظ داخل ذاكرة الشاشة داخل مجلد التطبيق.
- يعمل على نفس شبكة Wi‑Fi حتى لو ما في إنترنت.

## طريقة الاستخدام بعد تثبيت APK

1. وصل الشاشة أو TV Box على Wi‑Fi.
2. وصل الهاتف على نفس Wi‑Fi.
3. افتح تطبيق `Cafe Screen Local` على الشاشة.
4. سيظهر رابط مثل:

```text
http://192.168.1.55:8080
```

5. افتح الرابط من متصفح الهاتف.
6. اكتب PIN:

```text
1234
```

7. اختر صورة أو فيديو واضغط رفع.
8. المحتوى سيظهر تلقائياً على الشاشة.

## تغيير PIN

افتح الملف:

```text
app/src/main/java/com/rox/cafescreen/MainActivity.java
```

وغيّر هذا السطر:

```java
private static final String ADMIN_PIN = "1234";
```

مثلاً:

```java
private static final String ADMIN_PIN = "2468";
```

## إنشاء APK من Android Studio

1. افتح Android Studio.
2. اختر `Open`.
3. افتح مجلد المشروع `CafeScreenLocal`.
4. انتظر Gradle Sync.
5. من القائمة:

```text
Build > Build Bundle(s) / APK(s) > Build APK(s)
```

6. ستجد APK عادة في:

```text
app/build/outputs/apk/debug/app-debug.apk
```

أو إذا بنيت Release:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

## تثبيت APK على الشاشة بدون Play Store

### عبر USB

1. انسخ ملف APK على فلاشة USB.
2. ضع الفلاشة في TV Box أو Android TV.
3. افتح File Manager.
4. اضغط على APK.
5. إذا ظهرت رسالة منع، فعّل:

```text
Settings > Security > Install unknown apps
```

6. ثبّت التطبيق وافتحه.

### إذا التطبيق لا يظهر في شاشة التطبيقات

نزّل تطبيق:

```text
Sideload Launcher
```

أو تأكد أن `AndroidManifest.xml` يحتوي على:

```xml
<category android:name="android.intent.category.LEANBACK_LAUNCHER" />
```

هذا موجود مسبقاً في المشروع.

## أفضل إعدادات الفيديو

- صيغة: MP4
- الاتجاه: أفقي Landscape
- الدقة: 1920x1080
- الحجم: أقل من 250MB
- الترميز: H.264 إن أمكن

## ملاحظات مهمة

- الهاتف والشاشة لازم يكونوا على نفس Wi‑Fi.
- لا يحتاج إنترنت.
- لا يحتاج Firebase.
- لا يحتاج Chrome على الشاشة.
- التطبيق يخزن الملفات داخل ذاكرة الشاشة، وليس على الفلاشة حالياً.
- الفلاشة حالياً تستخدم فقط لنقل APK للشاشة.

## التطوير القادم المقترح

- ترتيب الصور من الهاتف.
- تحديد شاشة رقم 1 / شاشة رقم 2.
- QR Code للرابط.
- حفظ الملفات على USB Flash بدلاً من ذاكرة الشاشة.
- تصميم لوحة إدارة أجمل.
- إضافة شعار الكافي واسم الكافي.

## بناء APK بدون Android Studio عبر GitHub Actions

إذا لا تملك Android Studio، ارفع مجلد المشروع إلى GitHub ثم افتح:

Actions → Build APK → Run workflow

بعد انتهاء البناء، افتح آخر Workflow وانزل إلى Artifacts وحمّل:

CafeScreenLocal-debug-apk

ستجد داخله ملف:

app-debug.apk

انسخه إلى فلاشة USB وثبته على Android TV / TV Box.
