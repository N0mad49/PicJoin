package com.pinpic.app;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BridgeActivity {

    private static final int FILE_PICKER_REQUEST = 9001;
    private String pendingCallbackId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = getBridge().getWebView();
        if (webView != null) {
            webView.addJavascriptInterface(new NativeBridge(), "NativeBridge");
        }
    }

    private void jsEval(final String js) {
        runOnUiThread(() -> {
            WebView wv = getBridge().getWebView();
            if (wv != null) wv.evaluateJavascript(js, null);
        });
    }

    private class NativeBridge {
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
                    FileOutputStream fos = new FileOutputStream(new File(dir, fileName));
                    fos.write(imageBytes);
                    fos.close();

                    Intent scan = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    scan.setData(Uri.fromFile(new File(dir, fileName)));
                    sendBroadcast(scan);
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }

        @JavascriptInterface
        public void pickImages(final String callbackId) {
            pendingCallbackId = callbackId;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        WebView wv = getBridge().getWebView();
                        if (wv != null) {
                            wv.evaluateJavascript(
                                "document.getElementById('android-bridge-status').textContent='Java: launching picker...';"
                                + "document.getElementById('android-bridge-status').style.background='#f0f';", null);
                        }
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        startActivityForResult(Intent.createChooser(intent, "选择图片"), FILE_PICKER_REQUEST);
                    } catch (Exception e) {
                        final String err = e.getMessage() != null ? e.getMessage().replace("'", "\\'").replace("\"", "\\\"") : "null";
                        WebView wv = getBridge().getWebView();
                        if (wv != null) {
                            wv.evaluateJavascript(
                                "document.getElementById('android-bridge-status').textContent='Java: " + err + "';"
                                + "document.getElementById('android-bridge-status').style.background='#c00';", null);
                        }
                    }
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_PICKER_REQUEST && resultCode == RESULT_OK && data != null) {
            jsEval("document.getElementById('android-bridge-status').textContent='Java: picker returned OK';document.getElementById('android-bridge-status').style.background='#0ff';");

            try {
                List<Uri> uris = new ArrayList<>();
                if (data.getClipData() != null) {
                    for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                        uris.add(data.getClipData().getItemAt(i).getUri());
                    }
                } else if (data.getData() != null) {
                    uris.add(data.getData());
                }

                if (uris.isEmpty() || pendingCallbackId == null) return;

                JSONArray files = new JSONArray();
                for (Uri uri : uris) {
                    byte[] bytes = readUriBytes(uri);
                    String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    JSONObject obj = new JSONObject();
                    obj.put("data", "data:image/jpeg;base64," + b64);
                    obj.put("name", uri.getLastPathSegment());
                    files.put(obj);
                }

                final String js = "window.__nativeFileResult('" + pendingCallbackId + "', " + files.toString() + ")";
                jsEval(js);
            } catch (Exception e) {
                e.printStackTrace();
                jsEval("document.getElementById('android-bridge-status').textContent='Java: error: " + e.getMessage().replace("'", "\\'") + "';document.getElementById('android-bridge-status').style.background='#c00';");
            }
            pendingCallbackId = null;
        } else if (requestCode == FILE_PICKER_REQUEST) {
            jsEval("document.getElementById('android-bridge-status').textContent='Java: picker cancelled/error, resultCode=" + resultCode + "';document.getElementById('android-bridge-status').style.background='#c00';");
        }
    }

    private byte[] readUriBytes(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }
}
