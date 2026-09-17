package tn.kmzapk.mesplacements.reports;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.AtomicFile;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.webkit.WebViewAssetLoader;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.HttpsURLConnection;

/** Read-only GitHub client. No GitHub token, repository write API, or entry editor. */
@SuppressWarnings("deprecation")
public final class MainActivity extends ComponentActivity {
    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";
    private static final String HOST = "appassets.androidplatform.net";
    private static final String BACKUP_URL = "https://api.github.com/repos/kamelzouari11/Kmz_APK/contents/MySharedFolder/mes_placements_backup.json";
    private static final int MAX_BACKUP = 900_000;
    private static final int MAX_PDF = 15_000_000;
    private WebView webView;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean pdfPending = new AtomicBoolean(false);
    private ActivityResultLauncher<Intent> pdfLauncher;
    private String pdfRequestId;

    @Override @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle state) {
        super.onCreate(state);
        applyOrientation();
        if (state != null) pdfRequestId = state.getString("pdfRequestId");
        pdfPending.set(pdfRequestId != null);
        pdfLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> handlePdfResult(result.getResultCode(), result.getData()));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        FrameLayout root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets padding = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(padding.left, padding.top, padding.right, padding.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        webView = new WebView(this);
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.setWebChromeClient(new WebChromeClient());
        WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost()) && uri.getPath() != null && uri.getPath().startsWith("/assets/")) {
                    WebResourceResponse response = assets.shouldInterceptRequest(uri);
                    if (response != null) return response;
                }
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", null, new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
        });
        webView.addJavascriptInterface(new ReportsBridge(), "AndroidReports");
        webView.loadUrl(HOME);
    }

    private void applyOrientation() {
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        setRequestedOrientation(tablet ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); applyOrientation(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("pdfRequestId", pdfRequestId); }

    private AtomicFile cache() { return new AtomicFile(new File(getFilesDir(), "github-backup.encrypted.json")); }
    private File pendingPdf() { return new File(getCacheDir(), "pending-report.pdf"); }
    private static byte[] readLimited(InputStream stream, int limit) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] chunk = new byte[8192]; int count;
        while ((count = stream.read(chunk)) != -1) {
            if (bytes.size() + count > limit) throw new Exception("Fichier trop volumineux.");
            bytes.write(chunk, 0, count);
        }
        return bytes.toByteArray();
    }
    private String download() throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(BACKUP_URL).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15_000); connection.setReadTimeout(25_000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/vnd.github.raw+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10");
            connection.setRequestProperty("User-Agent", "MesPlacements-Android-Reports");
            int status = connection.getResponseCode();
            if (status == 403 || status == 429) throw new Exception("GitHub limite temporairement les lectures. Utilisez la copie hors connexion ou réessayez plus tard.");
            if (status == 404) throw new Exception("Sauvegarde GitHub introuvable. Vérifiez qu’une sauvegarde a été faite depuis le PC et que le dépôt est public.");
            if (status != 200) throw new Exception("Lecture GitHub impossible (" + status + ").");
            try (InputStream stream = connection.getInputStream()) { return new String(readLimited(stream, MAX_BACKUP), StandardCharsets.UTF_8); }
        } finally { connection.disconnect(); }
    }

    private final class ReportsBridge {
        @JavascriptInterface public void request(String id, String action, String payload) {
            if (id == null || !id.matches("[0-9]{1,12}") || payload == null || payload.length() > 21_000_000) return;
            io.execute(() -> {
                try {
                    JSONObject params = new JSONObject(payload);
                    switch (action) {
                        case "fetchBackup": reply(id, download(), null); break;
                        case "readCache":
                            if (!cache().getBaseFile().exists()) { reply(id, JSONObject.NULL, null); break; }
                            try (InputStream input = cache().openRead()) { reply(id, new JSONObject(new String(readLimited(input, 1_800_000), StandardCharsets.UTF_8)), null); }
                            break;
                        case "writeCache":
                            String encrypted = params.getString("encrypted");
                            if (encrypted.getBytes(StandardCharsets.UTF_8).length > MAX_BACKUP) throw new Exception("Sauvegarde trop volumineuse.");
                            JSONObject envelope = new JSONObject(encrypted);
                            if (!"mesplacements.encrypted".equals(envelope.optString("application")) || envelope.optInt("version") != 1 || !envelope.has("ciphertext") || envelope.has("records")) throw new Exception("Seule une sauvegarde chiffrée peut être conservée.");
                            JSONObject stored = new JSONObject().put("encrypted", encrypted).put("downloadedAt", params.getString("downloadedAt"));
                            AtomicFile file = cache(); FileOutputStream output = null;
                            try { output = file.startWrite(); output.write(stored.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(output); }
                            catch (Exception error) { if (output != null) file.failWrite(output); throw new Exception("La copie hors connexion n’a pas pu être enregistrée."); }
                            reply(id, true, null); break;
                        case "savePdf": preparePdf(id, params); break;
                        default: throw new Exception("Opération non autorisée.");
                    }
                } catch (Exception error) { reply(id, null, error.getMessage() == null ? "Opération impossible. Vérifiez la connexion et réessayez." : error.getMessage()); }
            });
        }
    }

    private void preparePdf(String id, JSONObject params) throws Exception {
        if (!pdfPending.compareAndSet(false, true)) throw new Exception("Un enregistrement PDF est déjà en cours.");
        try {
            byte[] bytes = Base64.decode(params.getString("base64"), Base64.DEFAULT);
            if (bytes.length > MAX_PDF || bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) throw new Exception("PDF invalide ou trop volumineux.");
            try (FileOutputStream file = new FileOutputStream(pendingPdf())) { file.write(bytes); file.getFD().sync(); }
            String proposed = params.optString("filename", "mes-placements.pdf");
            final String filename = proposed.matches("[a-zA-Z0-9._-]+\\.pdf") ? proposed : "mes-placements.pdf";
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) { pendingPdf().delete(); pdfPending.set(false); return; }
                pdfRequestId = id;
                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf").putExtra(Intent.EXTRA_TITLE, filename);
                    pdfLauncher.launch(intent);
                } catch (Exception error) { pdfRequestId = null; pdfPending.set(false); pendingPdf().delete(); reply(id, null, "Le sélecteur de fichiers Android est indisponible."); }
            });
        } catch (Exception error) { pdfPending.set(false); pendingPdf().delete(); throw error; }
    }
    private void handlePdfResult(int result, Intent data) {
        final String id = pdfRequestId; pdfRequestId = null;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            pendingPdf().delete(); pdfPending.set(false);
            try { reply(id, new JSONObject().put("saved", false), null); } catch (Exception ignored) { }
            return;
        }
        final Uri destination = data.getData();
        io.execute(() -> {
            try (InputStream input = new java.io.FileInputStream(pendingPdf()); OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                if (output == null) throw new Exception("Destination inaccessible.");
                byte[] chunk = new byte[8192]; int count;
                while ((count = input.read(chunk)) != -1) output.write(chunk, 0, count);
                output.flush();
            } catch (Exception error) { reply(id, null, "Le PDF n’a pas pu être enregistré."); pendingPdf().delete(); pdfPending.set(false); return; }
            pendingPdf().delete(); pdfPending.set(false);
            try { reply(id, new JSONObject().put("saved", true), null); } catch (Exception ignored) { }
            runOnUiThread(() -> Toast.makeText(this, "PDF enregistré", Toast.LENGTH_LONG).show());
        });
    }
    private void reply(String id, Object value, String error) {
        if (id == null) return;
        try {
            JSONObject message = new JSONObject();
            if (error != null) message.put("error", error); else message.put("value", value == null ? JSONObject.NULL : value);
            String script = "window.__reportsNativeReply && window.__reportsNativeReply(" + JSONObject.quote(id) + "," + message + ")";
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) webView.evaluateJavascript(script, null); });
        } catch (Exception ignored) { }
    }
    @Override protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidReports");
            webView.destroy();
        }
        io.shutdownNow();
        super.onDestroy();
    }
}
