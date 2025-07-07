import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

typedef PDFViewCreatedCallback = void Function(PDFViewController controller);
typedef RenderCallback = void Function(int? pages);
typedef PageChangedCallback = void Function(int? page, int? total);
typedef ErrorCallback = void Function(dynamic error);
typedef PageErrorCallback = void Function(int? page, dynamic error);
typedef LinkHandlerCallback = void Function(String? uri);
typedef ScrollChangedCallback = void Function(double? x, double? y);

/// قابلیت جدید: یک کلاس مدل برای نگهداری داده‌های تصویر استخراج شده.
/// این کلاس شامل فرمت اصلی تصویر و داده‌های Base64 آن است.
class PDFImage {
  final String format;
  final String base64Data;

  PDFImage({required this.format, required this.base64Data});

  factory PDFImage.fromMap(Map<dynamic, dynamic> map) {
    return PDFImage(
      format: map['format'] ?? 'unknown',
      base64Data: map['data'] ?? '',
    );
  }

  /// یک متد کمکی برای نمایش آسان تصویر با ویجت Image.
  /// مثال: Image.memory(pdfImage.bytes)
  Uint8List get bytes => base64Decode(base64Data);
}

enum FitPolicy { WIDTH, HEIGHT, BOTH }

class PDFView extends StatefulWidget {
  const PDFView({
    Key? key,
    this.filePath,
    this.pdfData,
    this.onViewCreated,
    this.onRender,
    this.onPageChanged,
    this.onError,
    this.onPageError,
    this.onLinkHandler,
    this.onScroll,
    this.gestureRecognizers,
    this.enableSwipe = true,
    this.swipeHorizontal = false,
    this.password,
    this.nightMode = false,
    this.autoSpacing = true,
    this.pageFling = true,
    this.pageSnap = true,
    this.fitEachPage = true,
    this.defaultPage = 0,
    this.fitPolicy = FitPolicy.WIDTH,
    this.preventLinkNavigation = false,
    this.backgroundColor,
  })  : assert(filePath != null || pdfData != null),
        super(key: key);

  @override
  _PDFViewState createState() => _PDFViewState();

  final PDFViewCreatedCallback? onViewCreated;
  final RenderCallback? onRender;
  final PageChangedCallback? onPageChanged;
  final ErrorCallback? onError;
  final PageErrorCallback? onPageError;
  final LinkHandlerCallback? onLinkHandler;
  final ScrollChangedCallback? onScroll;
  final Set<Factory<OneSequenceGestureRecognizer>>? gestureRecognizers;
  final String? filePath;
  final Uint8List? pdfData;
  final bool enableSwipe;
  final bool swipeHorizontal;
  final String? password;
  final bool nightMode;
  final bool autoSpacing;
  final bool pageFling;
  final bool pageSnap;
  final int defaultPage;
  final FitPolicy fitPolicy;
  @Deprecated("will be removed next version")
  final bool fitEachPage;
  final bool preventLinkNavigation;
  final Color? backgroundColor;
}

class _PDFViewState extends State<PDFView> {
  final Completer<PDFViewController> _controller =
      Completer<PDFViewController>();

  @override
  Widget build(BuildContext context) {
    if (defaultTargetPlatform == TargetPlatform.android) {
      return PlatformViewLink(
        viewType: 'plugins.endigo.io/pdfview',
        surfaceFactory: (
          BuildContext context,
          PlatformViewController controller,
        ) {
          return AndroidViewSurface(
            controller: controller as AndroidViewController,
            gestureRecognizers: widget.gestureRecognizers ??
                const <Factory<OneSequenceGestureRecognizer>>{},
            hitTestBehavior: PlatformViewHitTestBehavior.opaque,
          );
        },
        onCreatePlatformView: (PlatformViewCreationParams params) {
          return PlatformViewsService.initSurfaceAndroidView(
            id: params.id,
            viewType: 'plugins.endigo.io/pdfview',
            layoutDirection: TextDirection.rtl,
            creationParams: _CreationParams.fromWidget(widget).toMap(),
            creationParamsCodec: const StandardMessageCodec(),
          )
            ..addOnPlatformViewCreatedListener(params.onPlatformViewCreated)
            ..addOnPlatformViewCreatedListener((int id) {
              _onPlatformViewCreated(id);
            })
            ..create();
        },
      );
    } else if (defaultTargetPlatform == TargetPlatform.iOS) {
      return UiKitView(
        viewType: 'plugins.endigo.io/pdfview',
        onPlatformViewCreated: _onPlatformViewCreated,
        gestureRecognizers: widget.gestureRecognizers,
        creationParams: _CreationParams.fromWidget(widget).toMap(),
        creationParamsCodec: const StandardMessageCodec(),
      );
    }
    return Text(
        '$defaultTargetPlatform is not yet supported by the pdfview_flutter plugin');
  }

  void _onPlatformViewCreated(int id) {
    final PDFViewController controller = PDFViewController._(id, widget);
    _controller.complete(controller);
    if (widget.onViewCreated != null) {
      widget.onViewCreated!(controller);
    }
  }

  @override
  void didUpdateWidget(PDFView oldWidget) {
    super.didUpdateWidget(oldWidget);
    _controller.future.then(
        (PDFViewController controller) => controller._updateWidget(widget));
  }

  @override
  void dispose() {
    _controller.future
        .then((PDFViewController controller) => controller.dispose());
    super.dispose();
  }
}

class _CreationParams {
  _CreationParams({
    this.filePath,
    this.pdfData,
    this.settings,
  });

  static _CreationParams fromWidget(PDFView widget) {
    return _CreationParams(
      filePath: widget.filePath,
      pdfData: widget.pdfData,
      settings: _PDFViewSettings.fromWidget(widget),
    );
  }

  final String? filePath;
  final Uint8List? pdfData;
  final _PDFViewSettings? settings;

  Map<String, dynamic> toMap() {
    Map<String, dynamic> params = {
      'filePath': filePath,
      'pdfData': pdfData,
    };
    params.addAll(settings!.toMap());
    return params;
  }
}

class _PDFViewSettings {
  _PDFViewSettings({
    this.enableSwipe,
    this.swipeHorizontal,
    this.password,
    this.nightMode,
    this.autoSpacing,
    this.pageFling,
    this.pageSnap,
    this.defaultPage,
    this.fitPolicy,
    this.preventLinkNavigation,
    this.backgroundColor,
    this.onScroll,
  });

  static _PDFViewSettings fromWidget(PDFView widget) {
    return _PDFViewSettings(
      enableSwipe: widget.enableSwipe,
      swipeHorizontal: widget.swipeHorizontal,
      password: widget.password,
      nightMode: widget.nightMode,
      autoSpacing: widget.autoSpacing,
      pageFling: widget.pageFling,
      pageSnap: widget.pageSnap,
      defaultPage: widget.defaultPage,
      fitPolicy: widget.fitPolicy,
      preventLinkNavigation: widget.preventLinkNavigation,
      backgroundColor: widget.backgroundColor,
      onScroll: widget.onScroll,
    );
  }

  final bool? enableSwipe;
  final bool? swipeHorizontal;
  final String? password;
  final bool? nightMode;
  final bool? autoSpacing;
  final bool? pageFling;
  final bool? pageSnap;
  final int? defaultPage;
  final FitPolicy? fitPolicy;
  final bool? preventLinkNavigation;
  final Color? backgroundColor;
  final ScrollChangedCallback? onScroll;

  Map<String, dynamic> toMap() {
    return <String, dynamic>{
      'enableSwipe': enableSwipe,
      'swipeHorizontal': swipeHorizontal,
      'password': password,
      'nightMode': nightMode,
      'autoSpacing': autoSpacing,
      'pageFling': pageFling,
      'pageSnap': pageSnap,
      'defaultPage': defaultPage,
      'fitPolicy': fitPolicy.toString(),
      'preventLinkNavigation': preventLinkNavigation,
      'backgroundColor': backgroundColor?.value,
      'onScroll': onScroll != null,
    };
  }

  Map<String, dynamic> updatesMap(_PDFViewSettings newSettings) {
    final Map<String, dynamic> updates = <String, dynamic>{};
    if (enableSwipe != newSettings.enableSwipe) {
      updates['enableSwipe'] = newSettings.enableSwipe;
    }
    if (pageFling != newSettings.pageFling) {
      updates['pageFling'] = newSettings.pageFling;
    }
    if (pageSnap != newSettings.pageSnap) {
      updates['pageSnap'] = newSettings.pageSnap;
    }
    if (preventLinkNavigation != newSettings.preventLinkNavigation) {
      updates['preventLinkNavigation'] = newSettings.preventLinkNavigation;
    }
    return updates;
  }
}

class PDFViewController {
  PDFViewController._(
    int id,
    PDFView widget,
  )   : _channel = MethodChannel('plugins.endigo.io/pdfview_$id'),
        _widget = widget {
    _settings = _PDFViewSettings.fromWidget(widget);
    _channel.setMethodCallHandler(_onMethodCall);
  }

  void dispose() {
    _channel.setMethodCallHandler(null);
    _widget = null;
  }

  final MethodChannel _channel;
  late _PDFViewSettings _settings;
  PDFView? _widget;

  Future<bool?> _onMethodCall(MethodCall call) async {
    final widget = _widget;
    if (widget == null) return null;

    switch (call.method) {
      case 'onRender':
        widget.onRender?.call(call.arguments['pages']);
        return null;
      case 'onPageChanged':
        widget.onPageChanged?.call(
          call.arguments['page'],
          call.arguments['total'],
        );
        return null;
      case 'onError':
        widget.onError?.call(call.arguments['error']);
        return null;
      case 'onPageError':
        widget.onPageError
            ?.call(call.arguments['page'], call.arguments['error']);
        return null;
      case 'onLinkHandler':
        widget.onLinkHandler?.call(call.arguments);
        return null;
      case 'onScroll':
        widget.onScroll?.call(
          call.arguments['x'],
          call.arguments['y'],
        );
        return null;
    }
    throw MissingPluginException(
        '${call.method} was invoked but has no handler');
  }

  Future<int?> getPageCount() async {
    final int? pageCount = await _channel.invokeMethod('pageCount');
    return pageCount;
  }

  Future<int?> getCurrentPage() async {
    final int? currentPage = await _channel.invokeMethod('currentPage');
    return currentPage;
  }

  Future<bool?> setPage(int page) async {
    final bool? isSet =
        await _channel.invokeMethod('setPage', <String, dynamic>{
      'page': page,
    });
    return isSet;
  }

  Future<Map<String, double>?> getPosition() async {
    final Map<dynamic, dynamic>? position =
        await _channel.invokeMethod('getPosition');
    return position?.cast<String, double>();
  }

  Future<bool?> setPosition(double x, double y) async {
    return _channel.invokeMethod('setPosition', <String, dynamic>{
      'x': x,
      'y': y,
    });
  }

  /// قابلیت بهبودیافته: تمام تصاویر را به همراه فرمت اصلی آن‌ها استخراج می‌کند.
  /// خروجی لیستی از اشیاء `PDFImage` است.
  Future<List<PDFImage>?> extractImages() async {
    final List<dynamic>? imagesData =
        await _channel.invokeMethod('extractImages');
    if (imagesData == null) {
      return null;
    }
    return imagesData.map((imageData) => PDFImage.fromMap(imageData)).toList();
  }

  Future<void> _updateWidget(PDFView widget) async {
    _widget = widget;
    await _updateSettings(_PDFViewSettings.fromWidget(widget));
  }

  Future<void> _updateSettings(_PDFViewSettings setting) async {
    final Map<String, dynamic> updateMap = _settings.updatesMap(setting);
    if (updateMap.isEmpty) {
      return null;
    }
    _settings = setting;
    return _channel.invokeMethod('updateSettings', updateMap);
  }
}
