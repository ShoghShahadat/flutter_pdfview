# Flutter PDFView Plus (نسخه ۲.۰)

<p align="center">
  <a href="https://pub.dev/packages/flutter_pdfview"><img src="https://img.shields.io/pub/v/flutter_pdfview.svg" alt="Pub Version"></a>
  <a href="https://github.com/endigo/flutter_pdfview"><img src="https://img.shields.io/github/stars/endigo/flutter_pdfview.svg?style=social" alt="GitHub Stars"></a>
  <br>
  <img src="https://img.shields.io/badge/platform-android%20%7C%20ios-blue.svg" alt="Platform">
  <img src="https://img.shields.io/badge/license-MIT-green.svg" alt="License">
</p>

یک پلاگین قدرتمند فلاتر برای نمایش اسناد PDF در اندروید و iOS، همراه با قابلیت‌های پیشرفته برای مدیریت دقیق اسکرول و استخراج محتوا. این پروژه یک نسخه بهبودیافته بر پایه `flutter_pdfview` اصلی است.

---

## ✨ قابلیت‌های کلیدی نسخه Plus

این نسخه علاوه بر تمام ویژگی‌های استاندارد، قابلیت‌های منحصر به فرد زیر را ارائه می‌دهد:

- **💾 مدیریت دقیق موقعیت اسکرول:**
  - امکان ذخیره و بازیابی موقعیت دقیق (x, y) اسکرول کاربر.
  - ایده‌آل برای قابلیت "ادامه مطالعه" و جلوگیری از گم کردن صفحه.
  - فراهم کردن زیرساخت برای اسکرول خودکار.

- **🖼️ استخراج هوشمند تصاویر:**
  - استخراج تمام تصاویر موجود در سند PDF با حفظ **ترتیب بصری** و **فرمت اصلی** (JPEG, PNG, GIF, ...).
  - امکان ایجاد گالری تصاویر از محتوای PDF به سادگی.

---

## 🔧 نصب

برای استفاده از این پلاگین، آن را به صورت محلی در پروژه خود قرار دهید (طبق راهنمای مراحل قبل) و وابستگی آن را در `pubspec.yaml` بازنویسی کنید.

```yaml
# pubspec.yaml
dependency_overrides:
  flutter_pdfview:
    path: packages/flutter_pdfview # مسیر پلاگین محلی شما
```

سپس دستور زیر را اجرا کنید:
`flutter pub get`

### تنظیمات اختصاصی اندروید

پلاگین اصلاح‌شده ما به طور خودکار تمام تنظیمات لازم (وابستگی `pdfbox` و قوانین `R8`) را مدیریت می‌کند و **نیازی به تنظیمات دستی اضافی نیست.**

---

## 🚀 نحوه استفاده

### نمایش ساده یک PDF

برای نمایش یک فایل PDF از حافظه دستگاه یا از داده‌های باینری (`Uint8List`) استفاده کنید.

```dart
import 'package:flutter/material.dart';
import 'package:flutter_pdfview/flutter_pdfview.dart';

class PDFScreen extends StatefulWidget {
  final String? path;

  const PDFScreen({Key? key, this.path}) : super(key: key);

  @override
  _PDFScreenState createState() => _PDFScreenState();
}

class _PDFScreenState extends State<PDFScreen> {
  final Completer<PDFViewController> _controller = Completer<PDFViewController>();
  int? pages = 0;
  int? currentPage = 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("نمایش سند")),
      body: PDFView(
        filePath: widget.path,
        enableSwipe: true,
        swipeHorizontal: false,
        autoSpacing: false,
        pageFling: true,
        onRender: (_pages) {
          setState(() {
            pages = _pages;
          });
        },
        onError: (error) {
          print(error.toString());
        },
        onPageError: (page, error) {
          print('$page: ${error.toString()}');
        },
        onViewCreated: (PDFViewController pdfViewController) {
          _controller.complete(pdfViewController);
        },
        onPageChanged: (int? page, int? total) {
          setState(() {
            currentPage = page;
          });
        },
      ),
    );
  }
}
```

### استفاده از قابلیت‌های پیشرفته

#### ۱. مدیریت موقعیت اسکرول

شما می‌توانید موقعیت دقیق اسکرول را ذخیره کرده و بعداً بازیابی کنید.

```dart
// ذخیره موقعیت فعلی
Future<void> _savePosition() async {
  final PDFViewController controller = await _controller.future;
  final Map<String, double>? position = await controller.getPosition();
  if (position != null) {
    // موقعیت x و y را در SharedPreferences یا دیتابیس ذخیره کنید
    print("موقعیت ذخیره شد: X=${position['x']}, Y=${position['y']}");
  }
}

// بازیابی و پرش به موقعیت ذخیره شده
Future<void> _restorePosition() async {
  // موقعیت را از محل ذخیره‌سازی بخوانید
  final double savedX = 120.5;
  final double savedY = 2500.0;
  
  final PDFViewController controller = await _controller.future;
  await controller.setPosition(savedX, savedY);
  print("موقعیت بازیابی شد.");
}
```
**نکته:** برای دریافت گزارش زنده از تغییرات اسکرول (که در کتابخانه نیتیو حذف شده)، می‌توانید از یک `Timer.periodic` برای فراخوانی متد `getPosition` در فواصل زمانی کوتاه استفاده کنید.

#### ۲. استخراج و نمایش تصاویر

به سادگی تمام تصاویر یک سند را استخراج کرده و در یک گالری نمایش دهید.

```dart
// لیستی برای نگهداری تصاویر استخراج شده
List<PDFImage> extractedImages = [];

// فراخوانی متد برای استخراج
Future<void> _extractAllImages() async {
  final PDFViewController controller = await _controller.future;
  final images = await controller.extractImages();
  if (images != null) {
    setState(() {
      extractedImages = images;
    });
    print("${extractedImages.length} تصویر با موفقیت استخراج شد.");
  }
}

// نمایش تصاویر در یک ListView
Widget buildImageGallery() {
  return ListView.builder(
    itemCount: extractedImages.length,
    itemBuilder: (context, index) {
      final image = extractedImages[index];
      return Card(
        margin: const EdgeInsets.all(8.0),
        child: Column(
          children: [
            // نمایش تصویر با استفاده از متد کمکی bytes
            Image.memory(image.bytes),
            Padding(
              padding: const EdgeInsets.all(8.0),
              // نمایش فرمت اصلی تصویر
              child: Text("فرمت: ${image.format}"),
            ),
          ],
        ),
      );
    },
  );
}
```

---

## 📚 راهنمای API

### پارامترهای ویجت `PDFView`

| پارامتر                | نوع                       | توضیحات                                                                 |
| ----------------------- | -------------------------- | ------------------------------------------------------------------------ |
| `filePath`              | `String?`                  | مسیر فایل PDF در حافظه دستگاه.                                           |
| `pdfData`               | `Uint8List?`               | داده‌های باینری فایل PDF.                                                 |
| `onViewCreated`         | `PDFViewCreatedCallback?`  | پس از ساخته شدن ویجت فراخوانی می‌شود و کنترلر را برمی‌گرداند.              |
| `onRender`              | `RenderCallback?`          | پس از رندر شدن اولیه، تعداد کل صفحات را برمی‌گرداند.                      |
| `onPageChanged`         | `PageChangedCallback?`     | با تغییر صفحه، شماره صفحه فعلی و کل صفحات را برمی‌گرداند.                  |
| `onError`               | `ErrorCallback?`           | در صورت بروز خطای کلی فراخوانی می‌شود.                                   |
| `onPageError`           | `PageErrorCallback?`       | در صورت بروز خطا در رندر یک صفحه خاص فراخوانی می‌شود.                     |
| `onLinkHandler`         | `LinkHandlerCallback?`     | هنگام کلیک روی یک لینک (در صورت فعال بودن `preventLinkNavigation`) فراخوانی می‌شود. |
| `enableSwipe`           | `bool`                     | فعال/غیرفعال کردن تغییر صفحه با سوایپ. (پیش‌فرض: `true`)                  |
| `swipeHorizontal`       | `bool`                     | فعال کردن سوایپ افقی. (پیش‌فرض: `false`)                                 |
| `password`              | `String?`                  | رمز عبور برای فایل‌های PDF محافظت‌شده.                                    |
| `nightMode`             | `bool`                     | فعال کردن حالت شب. (پیش‌فرض: `false`)                                   |
| `fitPolicy`             | `FitPolicy`                | نحوه فیت شدن صفحات در صفحه نمایش. (پیش‌فرض: `FitPolicy.WIDTH`)           |
| `preventLinkNavigation` | `bool`                     | جلوگیری از باز شدن خودکار لینک‌ها. (پیش‌فرض: `false`)                     |
| `backgroundColor`       | `Color?`                   | تنظیم رنگ پس‌زمینه نمایشگر.                                              |

### متدهای `PDFViewController`

| متد             | خروجی                          | توضیحات                                                                     |
| --------------- | ------------------------------ | -------------------------------------------------------------------------- |
| `getPageCount`  | `Future<int?>`                 | تعداد کل صفحات PDF را برمی‌گرداند.                                          |
| `getCurrentPage`| `Future<int?>`                 | شماره صفحه فعلی (شروع از ۰) را برمی‌گرداند.                                  |
| `setPage`       | `Future<bool?>`                | به صفحه مشخص‌شده پرش می‌کند.                                                 |
| `getPosition`   | `Future<Map<String, double>?>` | **(جدید)** موقعیت دقیق اسکرول (x, y) را برمی‌گرداند.                         |
| `setPosition`   | `Future<bool?>`                | **(جدید)** نمایشگر را به موقعیت اسکرول (x, y) مشخص‌شده منتقل می‌کند.         |
| `extractImages` | `Future<List<PDFImage>?>`      | **(جدید)** تمام تصاویر را به ترتیب و با فرمت اصلی استخراج می‌کند.           |

---

## 🤝 مشارکت

از مشارکت شما در این پروژه استقبال می‌کنیم. لطفاً برای ارسال Pull Request یا ثبت Issue از طریق صفحه گیت‌هاب اقدام کنید.

## 📜 مجوز

این پروژه تحت مجوز MIT منتشر شده است.
