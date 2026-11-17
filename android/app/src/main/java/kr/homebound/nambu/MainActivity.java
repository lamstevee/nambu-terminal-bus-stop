package kr.homebound.nambu;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.webkit.*;
import android.view.*;
import org.json.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private WebView web;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xfff0f4f9); getWindow().setNavigationBarColor(0xfff0f4f9);
        web = new WebView(this); web.setBackgroundColor(0xfff0f4f9); web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"app.local".equals(uri.getHost()) || !"https".equals(uri.getScheme())) return null;
                String name = uri.getPath().equals("/") ? "index.html" : uri.getPath().substring(1);
                if (!java.util.Arrays.asList("index.html", "style.css", "app.js", "icon.svg").contains(name)) return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", java.util.Collections.emptyMap(), new java.io.ByteArrayInputStream(new byte[0]));
                try { String mime = name.endsWith("css") ? "text/css" : name.endsWith("js") ? "text/javascript" : name.endsWith("svg") ? "image/svg+xml" : "text/html"; return new WebResourceResponse(mime, "UTF-8", getAssets().open(name)); } catch (Exception e) { return null; }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if ("app.local".equals(request.getUrl().getHost())) return false;
                if ("https".equals(request.getUrl().getScheme())) { try { startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl())); } catch (RuntimeException ignored) {} }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android"); setContentView(web);
        web.setOnApplyWindowInsetsListener((view, insets) -> { view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets; });
        web.loadUrl("https://app.local/"); ScheduleReceiver.schedule(this);
    }
    final class Bridge {
        @JavascriptInterface public void request(int id, String action, String raw) {
            executor.execute(() -> {
                JSONObject response;
                try {
                    JSONObject data = new JSONObject(raw);
                    switch (action) {
                        case "snapshot": response = TransitApi.get(MainActivity.this).snapshot(data.optBoolean("test"), true); break;
                        case "save": Prefs.save(MainActivity.this, data); runOnUiThread(() -> { if(Prefs.get(MainActivity.this).getBoolean("enabled",false)) { if(ScheduleReceiver.inWindow(MainActivity.this)) startForegroundService(new Intent(MainActivity.this,ArrivalService.class)); else stopService(new Intent(MainActivity.this,ArrivalService.class)); } }); response = Prefs.config(MainActivity.this); break;
                        case "start":
                            Prefs.get(MainActivity.this).edit().putBoolean("enabled", true).apply();
                            runOnUiThread(() -> {
                                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
                                startForegroundService(new Intent(MainActivity.this, ArrivalService.class).putExtra("test", data.optBoolean("test")));
                                ScheduleReceiver.schedule(MainActivity.this);
                            }); response = Prefs.config(MainActivity.this); break;
                        case "stop": Prefs.get(MainActivity.this).edit().putBoolean("enabled", false).apply(); stopService(new Intent(MainActivity.this, ArrivalService.class)); ScheduleReceiver.schedule(MainActivity.this); response = Prefs.config(MainActivity.this); break;
                        case "alarm": runOnUiThread(() -> { if (Build.VERSION.SDK_INT >= 31) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))); }); response = Prefs.config(MainActivity.this); break;
                        case "battery": runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))); response = Prefs.config(MainActivity.this); break;
                        case "location": runOnUiThread(() -> {
                            if (!Proximity.precise(MainActivity.this)) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},4);
                            else if (!Proximity.background(MainActivity.this)) new AlertDialog.Builder(MainActivity.this).setTitle("백그라운드 위치 알림")
                                .setMessage("앱을 닫아도 역·정류장 300m 진입을 알리려면 앱 권한 → 위치에서 ‘항상 허용’을 선택하세요. 위치 좌표나 이동 기록은 서버로 전송하지 않습니다. 거절해도 도착정보는 이용할 수 있습니다.")
                                .setPositiveButton("위치 권한 설정",(d,w)-> { if(Build.VERSION.SDK_INT==29) requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},5); else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))); }).setNegativeButton("나중에",null).show();
                            else if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},3);
                            else { Proximity.sync(MainActivity.this,true); android.widget.Toast.makeText(MainActivity.this,"위치 알림 설정을 확인했습니다. 위치 알림과 알림 켜기도 활성화하세요.",android.widget.Toast.LENGTH_LONG).show(); }
                        }); response=Prefs.config(MainActivity.this); break;
                        case "popupSettings": runOnUiThread(() -> { Proximity.channels(MainActivity.this);startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,Proximity.CHANNEL)); }); response=Prefs.config(MainActivity.this);break;
                        case "proximityTest": Proximity.popup(MainActivity.this,java.util.Arrays.asList("nambu","bio","palace").contains(data.optString("group")) ? data.optString("group") : "nambu",true);response=Prefs.config(MainActivity.this);break;
                        default: response = Prefs.config(MainActivity.this);
                    }
                } catch (Exception e) { response = new JSONObject(); try { response.put("nativeFailure", true).put("error", "요청 처리 실패. 네트워크와 설정을 확인하세요."); } catch (JSONException ignored) {} }
                final String result = response.toString(); runOnUiThread(() -> { if (web != null) web.evaluateJavascript("window.nativeReply(" + id + "," + result + ")", null); });
            });
        }
    }
    @Override public void onResume() { super.onResume(); ScheduleReceiver.schedule(this); if (web != null) web.evaluateJavascript("window.refreshNativeConfig && window.refreshNativeConfig()", null); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) { super.onRequestPermissionsResult(requestCode, permissions, grantResults); Proximity.sync(this,true); if (web != null) web.evaluateJavascript("window.refreshNativeConfig && window.refreshNativeConfig()", null); }
    @Override public void onDestroy() { executor.shutdownNow(); if (web != null) { web.removeJavascriptInterface("Android"); web.destroy(); web = null; } super.onDestroy(); }
}
