package kr.homebound.nambu;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.RemoteViews;
import org.json.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

public class ArrivalService extends Service {
    private static final String CHANNEL = "homebound_arrivals";
    private static final int NOTIFICATION = 461641;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private JSONObject snapshot;
    private boolean test, fetching, destroyed;
    private long testUntil, lastFetch;
    private String previousState = "";
    private String currentDirection="";
    private PowerManager.WakeLock wakeLock;
    static void refresh(Context c) { c.startService(new Intent(c, ArrivalService.class).setAction("REFRESH")); }
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "퇴근길 도착정보", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("461 · 641 · 4319 버스 도착예측을 조용히 표시합니다."); channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Homebound:CommuteSession");
        wakeLock.setReferenceCounted(false);
        startForeground(NOTIFICATION, notification());
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (!Prefs.get(this).getBoolean("enabled", false)) { stopSelf(); return START_NOT_STICKY; }
        if (intent != null && !"REFRESH".equals(intent.getAction())) {
            test = intent.getBooleanExtra("test", false); testUntil = test ? SystemClock.elapsedRealtime() + 15 * 60 * 1000L : 0;
            Prefs.get(this).edit().putLong("testUntil", testUntil).apply();
        } else if (intent == null) { testUntil = Prefs.get(this).getLong("testUntil", 0); test = testUntil > SystemClock.elapsedRealtime(); }
        if (!test && !ScheduleReceiver.inWindow(this)) { stopSelf(); return START_NOT_STICKY; }
        long timeout = test ? 15 * 60 * 1000L : 25 * 60 * 60 * 1000L;
        // Continuous, user-visible commute monitoring needs timely refresh even with the screen off.
        wakeLock.acquire(Math.max(1000, timeout) + 60000);
        lastFetch = -ScheduleReceiver.interval(this); handler.removeCallbacks(tick); handler.post(tick); ScheduleReceiver.schedule(this);
        return START_STICKY;
    }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            if (!Prefs.get(ArrivalService.this).getBoolean("enabled", false) || (test ? SystemClock.elapsedRealtime() >= testUntil : !ScheduleReceiver.inWindow(ArrivalService.this))) { stopSelf(); return; }
            if(!wakeLock.isHeld()) wakeLock.acquire(25*60*60*1000L);
            String direction=ScheduleReceiver.direction(ArrivalService.this);
            if(!direction.equals(currentDirection)) { currentDirection=direction; snapshot=null; lastFetch=-ScheduleReceiver.interval(ArrivalService.this); previousState=""; }
            if (!fetching && SystemClock.elapsedRealtime() - lastFetch >= ScheduleReceiver.interval(ArrivalService.this)) fetch();
            String state = stateSignature();
            if (!state.equals(previousState)) { previousState = state; getSystemService(NotificationManager.class).notify(NOTIFICATION, notification()); }
            handler.postDelayed(this, 1000);
        }
    };
    private void fetch() {
        fetching = true; lastFetch = SystemClock.elapsedRealtime();
        executor.execute(() -> {
            JSONObject result;
            try { result = TransitApi.get(this).snapshot(test, false); }
            catch (Exception e) { result = new JSONObject(); try { result.put("error", "도착정보 조회 실패. 앱에서 설정을 확인하세요."); } catch (JSONException ignored) {} }
            JSONObject finalResult = result; handler.post(() -> {
                if (destroyed) return;
                JSONObject trip=finalResult.optJSONObject("trip");
                snapshot=trip==null || currentDirection.equals(trip.optString("direction")) ? finalResult : null;
                fetching = false; previousState = stateSignature();
                getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
            });
        });
    }
    private boolean stale() { return snapshot != null && "live".equals(snapshot.optString("mode")) && (snapshot.optLong("fetchedAt") == 0 || System.currentTimeMillis() - snapshot.optLong("fetchedAt") > snapshot.optLong("staleAfterMs",135000)); }
    private String stateSignature() {
        StringBuilder state = new StringBuilder(Boolean.toString(stale()));
        if (snapshot != null) { JSONArray buses = snapshot.optJSONArray("buses"); if (buses != null) for (int i = 0; i < buses.length(); i++) { JSONObject a = buses.optJSONObject(i).optJSONObject("first"); state.append(':').append(a != null && a.optLong("dueAt") > System.currentTimeMillis()); } }
        return state.toString();
    }
    private Notification notification() {
        boolean demo = "demo".equals(Prefs.get(this).getString("mode", "demo"));
        boolean commute="commute".equals(ScheduleReceiver.direction(this));
        String title=(demo ? "[시뮬레이션] " : "")+(commute ? "출근 · 한국제약바이오협회 → 남부터미널" : "퇴근 · 남부터미널 → 한국제약바이오협회");
        Notification.Builder builder = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(title).setContentText("461 · 641 · 4319 도착정보 조회 중")
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
            .addAction(new Notification.Action.Builder(null, "알림 중지", ScheduleReceiver.alarm(this, "PAUSE", 3)).build());
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.arrival_notification);
        views.setTextViewText(R.id.title, title);
        int[] timers = {R.id.timer461, R.id.timer641, R.id.timer4319}, states = {R.id.state461, R.id.state641, R.id.state4319};
        JSONArray buses = snapshot == null ? null : snapshot.optJSONArray("buses");
        for (int i = 0; i < 3; i++) {
            JSONObject bus = null;
            if (buses != null) for (int j = 0; j < buses.length(); j++) if (TransitApi.ROUTES[i].equals(buses.optJSONObject(j).optString("number"))) bus = buses.optJSONObject(j);
            JSONObject arrival = bus == null ? null : bus.optJSONObject("first");
            long due = arrival == null ? 0 : arrival.optLong("dueAt"), now = System.currentTimeMillis();
            boolean countdown = due > now && !stale();
            views.setViewVisibility(timers[i], countdown ? View.VISIBLE : View.GONE);
            views.setChronometerCountDown(timers[i], true);
            views.setChronometer(timers[i], SystemClock.elapsedRealtime() + Math.max(0, due - now), null, countdown);
            String label = countdown ? ("minute".equals(arrival.optString("precision")) ? "약 · 남은 시간" : "남은 시간") : stale() ? "갱신 지연 · 앱에서 확인" : due > 0 ? "도착예측 경과 · 갱신 대기" : arrival == null ? "도착정보 조회 중" : arrival.optString("message", "도착정보 없음");
            if (snapshot != null && snapshot.has("error") && !snapshot.isNull("error") && bus == null) label = "연결 확인 · 앱에서 설정";
            views.setTextViewText(states[i], label);
        }
        long fetchedAt = snapshot == null ? 0 : snapshot.optLong("fetchedAt");
        String time = fetchedAt == 0 ? "조회 중" : Instant.ofEpochMilli(fetchedAt).atZone(ScheduleReceiver.SEOUL).format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        views.setTextViewText(R.id.freshness, (test ? "15분 테스트 · " : ScheduleReceiver.summary(this)+" · ") + time + " 갱신 · 도착예측");
        builder.setStyle(new Notification.DecoratedCustomViewStyle()).setCustomBigContentView(views);
        // Collapsed notifications also name all routes; expanded notification has independent timers.
        StringBuilder compact = new StringBuilder();
        for (int i = 0; i < 3; i++) { if (i > 0) compact.append("  ·  "); compact.append(TransitApi.ROUTES[i]).append(" "); JSONObject a = null;
            if (buses != null) for (int j = 0; j < buses.length(); j++) if (TransitApi.ROUTES[i].equals(buses.optJSONObject(j).optString("number"))) a = buses.optJSONObject(j).optJSONObject("first");
            long seconds = a == null ? 0 : (a.optLong("dueAt") - System.currentTimeMillis() + 999) / 1000;
            compact.append(stale() ? "갱신 지연" : seconds > 0 ? ("minute".equals(a.optString("precision")) ? "약 " : "") + (seconds / 60) + "분" + (seconds % 60) + "초" : a == null ? "조회 중" : a.optLong("dueAt") > 0 ? "예측 경과" : a.optString("message"));
        }
        builder.setContentText(compact.toString()); return builder.build();
    }
    @Override public void onDestroy() { destroyed = true; handler.removeCallbacksAndMessages(null); executor.shutdownNow(); if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
