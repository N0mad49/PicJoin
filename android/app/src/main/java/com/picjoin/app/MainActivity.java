package com.picjoin.app;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BridgeActivity {

    private static final int PICK_IMAGES_REQUEST = 10001;
    private volatile String pendingFilesJson = null;
    private volatile boolean filesReady = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = getBridge().getWebView();
        if (webView != null) {
            webView.addJavascriptInterface(new NativeSaver(), "NativeBridge");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_IMAGES_REQUEST || resultCode != RESULT_OK || data == null) {
            return;
        }

        // Read files on background thread to avoid blocking UI
        executor.execute(() -> {
            StringBuilder json = new StringBuilder("[");
            int count = 0;

            try {
                if (data.getClipData() != null) {
                    // Multiple files selected
                    int size = data.getClipData().getItemCount();
                    for (int i = 0; i < size; i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        String fileData = readFileAsJson(uri, i);
                        if (fileData != null) {
                            if (count > 0) json.append(",");
                            json.append(fileData);
                            count++;
                        }
                    }
                } else if (data.getData() != null) {
                    // Single file
                    Uri uri = data.getData();
                    String fileData = readFileAsJson(uri, 0);
                    if (fileData != null) {
                        json.append(fileData);
                        count++;
                    }
                }
            } catch (Exception e) {
                Log.e("PinPic", "Error reading files", e);
            }

            json.append("]");
            String result = json.toString();
            Log.d("PinPic", "Read " + count + " files, JSON length=" + result.length());
            pendingFilesJson = result;
            filesReady = true;

            // Notify JS on main thread
            mainHandler.post(() -> {
                WebView wv = getBridge().getWebView();
                if (wv != null) {
                    wv.loadUrl("javascript:if(window.__nativeReady)window.__nativeReady()");
                }
            });
        });
    }

    private String readFileAsJson(Uri uri, int index) {
        try {
            String mime = getContentResolver().getType(uri);
            InputStream is = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            is.close();
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            String name = "image_" + index + ".jpg";
            String dispName = uri.getLastPathSegment();
            if (dispName != null) name = dispName;
            Log.d("PinPic", "File " + index + ": " + name + " base64=" + b64.length());

            return "{\"name\":\"" + escape(name) + "\",\"type\":\""
                    + (mime != null ? escape(mime) : "image/jpeg")
                    + "\",\"data\":\"" + b64 + "\"}";
        } catch (Exception e) {
            Log.e("PinPic", "File " + index + " read error", e);
            return null;
        }
    }

    private class NativeSaver {
        @JavascriptInterface
        public boolean saveImage(String base64Data, String fileName) {
            try {
                byte[] imageBytes = Base64.decode(base64Data, Base64.DEFAULT);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    values.put(MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/PicJoin");

                    Uri uri = getContentResolver().insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri != null) {
                        OutputStream out = getContentResolver().openOutputStream(uri);
                        if (out != null) {
                            out.write(imageBytes);
                            out.close();
                            return true;
                        }
                    }
                } else {
                    File dir = new File(
                            Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_PICTURES),
                            "PicJoin");
                    dir.mkdirs();
                    File file = new File(dir, fileName);
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(imageBytes);
                    fos.close();

                    Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    scan.setData(Uri.fromFile(file));
                    sendBroadcast(scan);
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        @JavascriptInterface
        public void pickImages(String callbackId) {
            pendingFilesJson = null;
            filesReady = false;
            Log.d("PinPic", "pickImages: " + callbackId);

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(intent, "选择图片"), PICK_IMAGES_REQUEST);
        }

        @JavascriptInterface
        public boolean checkFilesReady() {
            return filesReady;
        }

        @JavascriptInterface
        public String getPendingFilesJson() {
            filesReady = false;
            String r = pendingFilesJson;
            pendingFilesJson = null;
            return r;
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
