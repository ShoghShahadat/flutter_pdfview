package io.endigo.plugins.pdfviewflutter;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.link.LinkHandler;
import com.github.barteksc.pdfviewer.listener.OnErrorListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.listener.OnRenderListener;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.tom_roush.pdfbox.contentstream.PDFStreamEngine;
import com.tom_roush.pdfbox.contentstream.operator.Operator;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.io.IOUtils;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.platform.PlatformView;

public class FlutterPDFView implements PlatformView, MethodCallHandler {
    private final PDFView pdfView;
    private final MethodChannel methodChannel;
    private final LinkHandler linkHandler;
    private final Context context;

    private String filePath;
    private byte[] pdfData;

    @SuppressWarnings("unchecked")
    FlutterPDFView(Context context, BinaryMessenger messenger, int id, Map<String, Object> params) {
        this.context = context;
        pdfView = new PDFView(context, null);
        
        PDFBoxResourceLoader.init(context);

        final boolean preventLinkNavigation = getBoolean(params, "preventLinkNavigation");

        methodChannel = new MethodChannel(messenger, "plugins.endigo.io/pdfview_" + id);
        methodChannel.setMethodCallHandler(this);

        linkHandler = new PDFLinkHandler(context, pdfView, methodChannel, preventLinkNavigation);

        PDFView.Configurator config = null;
        if (params.get("filePath") != null) {
            this.filePath = (String) params.get("filePath");
            this.pdfData = null;
            config = pdfView.fromUri(getURI(this.filePath));
        } else if (params.get("pdfData") != null) {
            this.pdfData = (byte[]) params.get("pdfData");
            this.filePath = null;
            config = pdfView.fromBytes(this.pdfData);
        }

        Object backgroundColor = params.get("backgroundColor");
        if (backgroundColor != null) {
            int color = ((Number) backgroundColor).intValue();
            pdfView.setBackgroundColor(color);
        }

        if (config != null) {
            config
                    .enableSwipe(getBoolean(params, "enableSwipe"))
                    .swipeHorizontal(getBoolean(params, "swipeHorizontal"))
                    .password(getString(params, "password"))
                    .nightMode(getBoolean(params, "nightMode"))
                    .autoSpacing(getBoolean(params, "autoSpacing"))
                    .pageFling(getBoolean(params, "pageFling"))
                    .pageSnap(getBoolean(params, "pageSnap"))
                    .pageFitPolicy(getFitPolicy(params))
                    .enableAnnotationRendering(true)
                    .linkHandler(linkHandler)
                    .enableAntialiasing(false)
                    .enableDoubletap(true)
                    .defaultPage(getInt(params, "defaultPage"))
                    .onPageChange((page, total) -> {
                        Map<String, Object> args = new HashMap<>();
                        args.put("page", page);
                        args.put("total", total);
                        methodChannel.invokeMethod("onPageChanged", args);
                    })
                    .onError(t -> {
                        Map<String, Object> args = new HashMap<>();
                        args.put("error", t.toString());
                        methodChannel.invokeMethod("onError", args);
                    }).onPageError((page, t) -> {
                        Map<String, Object> args = new HashMap<>();
                        args.put("page", page);
                        args.put("error", t.toString());
                        methodChannel.invokeMethod("onPageError", args);
                    }).onRender((pages, pageWidth, pageHeight) -> {
                        Map<String, Object> args = new HashMap<>();
                        args.put("pages", pages);
                        methodChannel.invokeMethod("onRender", args);
                    })
                    .onScrollHandle(new com.github.barteksc.pdfviewer.scroll.ScrollHandle() {
                        @Override
                        public void setScroll(float position) {}

                        @Override
                        public void setupLayout(PDFView pdfView) {}

                        @Override
                        public void destroyLayout() {}

                        @Override
                        public void setPageNum(int pageNum) {}

                        @Override
                        public boolean isVisible() {
                            return false;
                        }

                        @Override
                        public void hideDelayed() {}

                        @Override
                        public void show() {}

                        @Override
                        public void hide() {}
                        
                        @Override
                        public void onScroll(float x, float y){
                             Map<String, Object> args = new HashMap<>();
                             args.put("x", (double) x);
                             args.put("y", (double) y);
                             methodChannel.invokeMethod("onScroll", args);
                        }
                    })
                    .load();
        }
    }

    @Override
    public View getView() {
        return pdfView;
    }

    @Override
    public void onMethodCall(MethodCall methodCall, Result result) {
        switch (methodCall.method) {
            case "pageCount":
                getPageCount(result);
                break;
            case "currentPage":
                getCurrentPage(result);
                break;
            case "setPage":
                setPage(methodCall, result);
                break;
            case "getPosition":
                getPosition(result);
                break;
            case "setPosition":
                setPosition(methodCall, result);
                break;
            case "extractImages":
                extractImagesInOrder(result); // استفاده از متد جدید و دقیق
                break;
            case "updateSettings":
                updateSettings(methodCall, result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    // ... (متدهای دیگر بدون تغییر باقی می‌مانند)
    void getPageCount(Result result) {
        result.success(pdfView.getPageCount());
    }

    void getCurrentPage(Result result) {
        result.success(pdfView.getCurrentPage());
    }

    void setPage(MethodCall call, Result result) {
        if (call.argument("page") != null) {
            int page = (int) call.argument("page");
            pdfView.jumpTo(page, true);
        }
        result.success(true);
    }

    void getPosition(Result result) {
        Map<String, Double> position = new HashMap<>();
        position.put("x", (double) pdfView.getCurrentXOffset());
        position.put("y", (double) pdfView.getCurrentYOffset());
        result.success(position);
    }

    void setPosition(MethodCall call, Result result) {
        if (call.argument("x") != null && call.argument("y") != null) {
            float x = ((Number) call.argument("x")).floatValue();
            float y = ((Number) call.argument("y")).floatValue();
            pdfView.moveTo(x, y, true);
        }
        result.success(true);
    }
    
    /**
     * متد جدید و بهبودیافته برای استخراج تصاویر به ترتیب بصری.
     * این متد از یک موتور پردازش استریم برای تضمین ترتیب صحیح استفاده می‌کند.
     */
    private void extractImagesInOrder(final Result result) {
        new Thread(() -> {
            final Handler handler = new Handler(Looper.getMainLooper());
            try {
                PDDocument document = null;
                if (filePath != null) {
                    document = PDDocument.load(new File(filePath));
                } else if (pdfData != null) {
                    document = PDDocument.load(pdfData);
                } else {
                    handler.post(() -> result.error("NoSource", "PDF source not available.", null));
                    return;
                }

                final List<Map<String, String>> imagesData = new ArrayList<>();
                ImageExtractor extractor = new ImageExtractor(imagesData);

                for (PDPage page : document.getPages()) {
                    extractor.processPage(page);
                }
                document.close();

                handler.post(() -> result.success(imagesData));

            } catch (IOException e) {
                handler.post(() -> result.error("ExtractionError", "Failed to extract images.", e.toString()));
            }
        }).start();
    }


    @SuppressWarnings("unchecked")
    private void updateSettings(MethodCall methodCall, Result result) {
        applySettings((Map<String, Object>) methodCall.arguments);
        result.success(null);
    }

    private void applySettings(Map<String, Object> settings) {
        for (String key : settings.keySet()) {
            switch (key) {
                case "enableSwipe":
                    pdfView.setSwipeEnabled(getBoolean(settings, key));
                    break;
                case "nightMode":
                    pdfView.setNightMode(getBoolean(settings, key));
                    break;
                case "pageFling":
                    pdfView.setPageFling(getBoolean(settings, key));
                    break;
                case "pageSnap":
                    pdfView.setPageSnap(getBoolean(settings, key));
                    break;
                case "preventLinkNavigation":
                    ((PDFLinkHandler) this.linkHandler).setPreventLinkNavigation(getBoolean(settings, key));
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void dispose() {
        methodChannel.setMethodCallHandler(null);
    }

    private boolean getBoolean(Map<String, Object> params, String key) {
        return params.containsKey(key) && (boolean) params.get(key);
    }

    private String getString(Map<String, Object> params, String key) {
        return params.containsKey(key) ? (String) params.get(key) : "";
    }

    private int getInt(Map<String, Object> params, String key) {
        return params.containsKey(key) ? (int) params.get(key) : 0;
    }

    private FitPolicy getFitPolicy(Map<String, Object> params) {
        String fitPolicy = getString(params, "fitPolicy");
        switch (fitPolicy) {
            case "FitPolicy.WIDTH":
                return FitPolicy.WIDTH;
            case "FitPolicy.HEIGHT":
                return FitPolicy.HEIGHT;
            case "FitPolicy.BOTH":
            default:
                return FitPolicy.BOTH;
        }
    }

    private Uri getURI(final String uri) {
        Uri parsed = Uri.parse(uri);
        if (parsed.getScheme() == null || parsed.getScheme().isEmpty()) {
            return Uri.fromFile(new File(uri));
        }
        return parsed;
    }
}

/**
 * یک موتور پردازش استریم سفارشی برای استخراج تصاویر به ترتیب رندر شدن.
 */
class ImageExtractor extends PDFStreamEngine {
    private final List<Map<String, String>> imagesData;

    ImageExtractor(List<Map<String, String>> imagesData) {
        this.imagesData = imagesData;
    }

    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        String operation = operator.getName();
        if ("Do".equals(operation)) {
            COSName objectName = (COSName) operands.get(0);
            PDXObject xobject = getResources().getXObject(objectName);

            if (xobject instanceof PDImageXObject) {
                PDImageXObject image = (PDImageXObject) xobject;
                
                InputStream rawBytesStream = image.getStream().getUnfilteredStream();
                byte[] imageBytes = IOUtils.toByteArray(rawBytesStream);
                rawBytesStream.close();
                
                String format = image.getSuffix();
                if (format == null) {
                    format = "unknown";
                }

                Map<String, String> imageData = new HashMap<>();
                imageData.put("format", format);
                imageData.put("data", Base64.encodeToString(imageBytes, Base64.DEFAULT));
                
                // جلوگیری از افزودن تصاویر تکراری (برخی PDFها یک تصویر را چند بار ارجاع می‌دهند)
                // این کار با مقایسه هش داده‌های تصویر انجام می‌شود تا از تکرار جلوگیری شود.
                // برای سادگی در اینجا، ما فقط بر اساس داده‌های base64 چک می‌کنیم.
                boolean alreadyExists = false;
                for(Map<String, String> existingImage : imagesData) {
                    if(existingImage.get("data").equals(imageData.get("data"))) {
                        alreadyExists = true;
                        break;
                    }
                }
                if(!alreadyExists) {
                    imagesData.add(imageData);
                }
            }
        } else {
            super.processOperator(operator, operands);
        }
    }
}
