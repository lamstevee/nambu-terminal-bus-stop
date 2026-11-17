package kr.homebound.nambu;
import android.app.*;
import android.content.*;
import android.os.Build;
import java.time.*;
public class ScheduleReceiver extends BroadcastReceiver {
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static int[] times(Context c) { var p=Prefs.get(c); return new int[]{SchedulePolicy.minute(p.getString("commuteStart","06:00")),SchedulePolicy.minute(p.getString("commuteEnd","10:00")),SchedulePolicy.minute(p.getString("homeStart","18:00")),SchedulePolicy.minute(p.getString("homeEnd","24:00"))}; }
    static boolean always(Context c) { return "always".equals(Prefs.get(c).getString("activation","commute")); }
    static boolean inWindow(Context c) { int[] t=times(c); return SchedulePolicy.active(always(c),t[0],t[1],t[2],t[3],ZonedDateTime.now(SEOUL)); }
    static String direction(Context c) { int[] t=times(c); return SchedulePolicy.direction(Prefs.get(c).getString("direction","auto"),t[0],t[1],t[2],t[3],ZonedDateTime.now(SEOUL)); }
    static long interval(Context c) { int[] t=times(c);int minutes=always(c) ? 1440 : t[1]-t[0]+t[3]-t[2];return Math.max(60000L,(long)Math.ceil(minutes*60000.0/900/10000)*10000); }
    static String summary(Context c) { var p=Prefs.get(c); return always(c) ? "24시간" : p.getString("commuteStart","06:00")+"–"+p.getString("commuteEnd","10:00")+" / "+p.getString("homeStart","18:00")+"–"+p.getString("homeEnd","24:00"); }
    static PendingIntent alarm(Context c, String action, int code) { return PendingIntent.getBroadcast(c, code, new Intent(c, ScheduleReceiver.class).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); }
    static boolean schedule(Context c) {
        Proximity.sync(c,false);
        AlarmManager manager = c.getSystemService(AlarmManager.class);
        manager.cancel(alarm(c,"STOP",2)); manager.cancel(alarm(c,"START",1));
        if (!Prefs.get(c).getBoolean("enabled", false)) return false;
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) return false;
        long next=SchedulePolicy.nextBoundary(times(c),ZonedDateTime.now(SEOUL));
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarm(c,"START",1));
        return true;
    }
    @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (!"PAUSE".equals(action) && !"STOP".equals(action)) { PendingResult pending=goAsync();Proximity.sync(c,true,pending::finish); }
        if ("STOP".equals(action)) { c.stopService(new Intent(c, ArrivalService.class)); schedule(c); return; }
        if ("PAUSE".equals(action)) { Prefs.get(c).edit().putBoolean("enabled", false).apply(); c.stopService(new Intent(c, ArrivalService.class)); schedule(c); return; }
        if ("START".equals(action) && Prefs.get(c).getBoolean("enabled", false)) {
            if(inWindow(c)) { try { c.startForegroundService(new Intent(c, ArrivalService.class)); } catch (RuntimeException ignored) {} }
            else c.stopService(new Intent(c,ArrivalService.class));
        }
        // Boot/time changes restore alarms only; never start a service from a boot broadcast.
        schedule(c);
    }
}
