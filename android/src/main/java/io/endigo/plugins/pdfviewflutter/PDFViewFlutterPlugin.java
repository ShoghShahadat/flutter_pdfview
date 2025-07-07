package io.endigo.plugins.pdfviewflutter;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

public class PDFViewFlutterPlugin implements FlutterPlugin, MethodCallHandler {
    private MethodChannel channel;
    private Context context;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        // ثبت فکتوری برای ویجت PDFView
        binding
                .getPlatformViewRegistry()
                .registerViewFactory("plugins.endigo.io/pdfview", new PDFViewFactory(binding.getBinaryMessenger()));

        // کانال متد برای ابزارهای استاتیک
        channel = new MethodChannel(binding.getBinaryMessenger(), "plugins.endigo.io/pdfview_tools");
        context = binding.getApplicationContext();
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case "extractImages":
                extractImages(call, result);
                break;
            case "generateThumbnail":
                generateThumbnail(call, result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void extractImages(MethodCall call, final Result result) {
        final String filePath = call.argument("filePath");
        final byte[] pdfData = call.argument("pdfData");

        FlutterPDFView.extractImagesInternal(filePath, pdfData, context, new Result() {
            @Override
            public void success(Object o) {
                result.success(o);
            }

            @Override
            public void error(String s, String s1, Object o) {
                result.error(s, s1, o);
            }

            @Override
            public void notImplemented() {
                result.notImplemented();
            }
        });
    }

    private void generateThumbnail(MethodCall call, final Result result) {
        final String filePath = call.argument("filePath");
        final byte[] pdfData = call.argument("pdfData");
        final int quality = call.argument("quality");
        final int page = 0; // همیشه از صفحه اول تصویر بندانگشتی تولید می‌شود

        new Thread(() -> {
            final Handler handler = new Handler(Looper.getMainLooper());
            try {
                PDDocument document;
                if (filePath != null) {
                    document = PDDocument.load(new File(filePath));
                } else if (pdfData != null) {
                    document = PDDocument.load(pdfData);
                } else {
                    handler.post(() -> result.error("NoSource", "PDF source not available for thumbnail.", null));
                    return;
                }

                PDFRenderer renderer = new PDFRenderer(document);
                Bitmap bitmap = renderer.renderImage(page, 1, Bitmap.Config.ARGB_8888); // scale = 1

                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, quality, stream);
                byte[] byteArray = stream.toByteArray();
                bitmap.recycle(); // آزاد کردن حافظه
                document.close();

                String base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP);

                Map<String, Object> imageData = new HashMap<>();
                imageData.put("data", base64);
                imageData.put("width", bitmap.getWidth());
                imageData.put("height", bitmap.getHeight());

                handler.post(() -> result.success(imageData));
            } catch (IOException e) {
                handler.post(() -> result.error("ThumbnailError", "Failed to generate thumbnail.", e.toString()));
            }
        }).start();
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (channel != null) {
            channel.setMethodCallHandler(null);
        }
        context = null;
    }
}
