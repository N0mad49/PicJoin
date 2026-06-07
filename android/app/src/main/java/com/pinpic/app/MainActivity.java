package com.pinpic.app;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.getcapacitor.BridgeActivity;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public class MainActivity extends BridgeActivity {

    private ActivityResultLauncher<String> filePickerLauncher;
    private String pendingFileCallbackId;
    private volatile String pendingFilesJson = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                this::onFilesPicked);

        WebView webView = getBridge().getWebView();
        if (webView != null) {
            webView.addJavascriptInterface(new NativeSaver(), "NativeBridge");
        }
    }

    private void onFilesPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty() || pendingFileCallbackId == null) return;

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < uris.size(); i++) {
            try {
                Uri uri = uris.get(i);
                String mime = getContentResolver().getType(uri);
                InputStream is = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                is.close();
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                String name = "image_" + i + ".jpg";
                String dispName = uri.getLastPathSegment();
                if (dispName != null) name = dispName;

                if (i > 0) json.append(",");
                json.append("{\"name\":\"").append(escape(name))
                        .append("\",\"type\":\"").append(mime != null ? escape(mime) : "image/jpeg")
                        .append("\",\"data\":\"").append(b64).append("\"}");
                Log.d("PinPic", "File " + i + ": " + name + " " + (mime != null ? mime : "?") + " " + b64.length() + " chars");
            } catch (Exception e) {
                Log.e("PinPic", "File " + i + " error", e);
            }
        }
        json.append("]");
        pendingFilesJson = json.toString();
        Log.d("PinPic", "Files ready, total JSON length: " + pendingFilesJson.length());

        // Signal JS with a tiny evaluateJavascript call (not the full data)
        String cbId = pendingFileCallbackId;
        pendingFileCallbackId = null;
        WebView wv = getBridge().getWebView();
        if (wv != null) {
            String jsCall = "window.__nativeFilesReady('" + escape(cbId) + "')";
            wv.post(() -> wv.evaluateJavascript(jsCall, null));
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
                            Environment.DIRECTORY_PICTURES + "/PinPhotograph");

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
                            "PinPhotograph");
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
            pendingFileCallbackId = callbackId;
            pendingFilesJson = null;
            Log.d("PinPic", "pickImages called: " + callbackId);
            runOnUiThread(() -> filePickerLauncher.launch("image/*"));
        }

        @JavascriptInterface
        public String getPendingFilesJson() {
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
