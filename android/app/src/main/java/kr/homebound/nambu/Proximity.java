package kr.homebound.nambu;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Proximity {
    static final String CHANNEL = "homebound_proximity";
    private static String registered = "";
    private static boolean updating;
    static boolean precise(Context c) { return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    static boolean background(Context c) { return Build.VERSION.SDK_INT < 29 || c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    static boolean available(Context c) { return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(c) == ConnectionResult.SUCCESS; }
    static JSONObject places(Context c) throws Exception {
        try (var in = c.getAssets().open("places.json"); var out = new java.io.ByteArrayOutputStream()) { byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8)); }
    }
    static PendingIntent pending(Context c) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(c, 300, new Intent(c, ProximityReceiver.class).setAction("kr.homebound.nambu.GEOFENCE"), flags);
    }
    static void channels(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "정류장·역 300m 진입 알림", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("설정한 시간에 출퇴근 경로의 정류장·역 근처에 도착하면 알립니다.");
        ch.enableVibration(true); c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }
    static void sync(Context context, boolean force) { sync(context,force,()->{}); }
    @SuppressLint("MissingPermission")
    static synchronized void sync(Context context, boolean force, Runnable complete) {
        Context c = context.getApplicationContext(); var prefs = Prefs.get(c);
        boolean wanted = prefs.getBoolean("enabled", false) && prefs.getBoolean("proximityEnabled", false);
        if (updating) { complete.run();return; }
        GeofencingClient client = LocationServices.getGeofencingClient(c);
        if (!wanted || !precise(c) || !background(c) || !available(c)) {
            registered = "";
            var clear=prefs.edit();for(String key:prefs.getAll().keySet())if(key.startsWith("geo_inside_") || key.startsWith("geo_alert_inside_"))clear.remove(key);clear.apply();
            if (available(c)) client.removeGeofences(pending(c));
            prefs.edit().putString("proximityStatus", !wanted ? "꺼짐" : !available(c) ? "Google Play 서비스 필요" : "정확한 위치와 항상 허용 권한 필요").apply();
            complete.run();return;
        }
        String identity = ScheduleReceiver.direction(c) + ":" + ScheduleReceiver.inWindow(c);
        if (!force && identity.equals(registered)) {complete.run();return;}
        try {
            channels(c); JSONArray points = places(c).getJSONArray("points"); ArrayList<Geofence> fences = new ArrayList<>();
            for (int i = 0; i < points.length(); i++) {
                JSONObject p = points.getJSONObject(i);
                if (p.has("direction") && !p.getString("direction").equals(ScheduleReceiver.direction(c))) continue;
                fences.add(new Geofence.Builder().setRequestId(p.getString("id")).setCircularRegion(p.getDouble("lat"), p.getDouble("lng"), 300)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE).setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                    .setNotificationResponsiveness(30_000).build());
            }
            updating = true;
            // Remove obsolete direction-specific points before adding the new registration.
            client.removeGeofences(pending(c)).addOnCompleteListener(removed -> {
                try {
                client.addGeofences(new GeofencingRequest.Builder().setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER).addGeofences(fences).build(), pending(c))
                    .addOnSuccessListener(v -> { synchronized (Proximity.class) { registered = identity; updating = false; } prefs.edit().putString("proximityStatus", "300m 감지 등록됨" + (ScheduleReceiver.inWindow(c) ? "" : " · 활성화 시간 대기")).apply(); complete.run();sync(c,false); })
                    .addOnFailureListener(e -> { synchronized (Proximity.class) { registered = ""; updating = false; } prefs.edit().putString("proximityStatus", "위치 감지 등록 실패 · 위치 설정 및 Play 서비스를 확인하세요.").apply();complete.run(); });
                }catch(RuntimeException e){synchronized(Proximity.class){registered="";updating=false;}prefs.edit().putString("proximityStatus","위치 권한을 다시 확인하세요.").apply();complete.run();}
            });
        } catch (Exception e) { updating = false; prefs.edit().putString("proximityStatus", "위치 감지 설정 실패").apply();complete.run(); }
    }
    static void popup(Context c, String group, boolean test) {
        if (!c.getSystemService(NotificationManager.class).areNotificationsEnabled()) return;
        channels(c); String direction = ScheduleReceiver.direction(c);
        String name = "nambu".equals(group) ? "남부터미널역·정류장" : "bio".equals(group) ? "한국제약바이오협회 정류장" : "경복궁역";
        String body = ProximityPolicy.guidance(group, direction);
        String arrivals = TransitApi.cachedArrivalText(direction);
        if (("nambu".equals(group) && "home".equals(direction)) || ("bio".equals(group) && "commute".equals(direction))) body += "\n" + arrivals;
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 301, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(c, CHANNEL).setSmallIcon(kr.homebound.nambu.R.drawable.ic_bus)
            .setContentTitle((test ? "[테스트] " : "") + name + " 300m 근처")
            .setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(pi)
            .setCategory(Notification.CATEGORY_REMINDER).setAutoCancel(true).setTimeoutAfter(300_000).build();
        c.getSystemService(NotificationManager.class).notify(test ? 309 : 300 + Arrays.asList("palace", "nambu", "bio").indexOf(group), notification);
    }
}
