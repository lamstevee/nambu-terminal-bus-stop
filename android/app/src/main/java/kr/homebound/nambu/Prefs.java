package kr.homebound.nambu;
import android.app.*;
import android.content.*;
import android.os.Build;
import org.json.*;
final class Prefs {
    static android.content.SharedPreferences get(Context c) { return c.getSharedPreferences("homebound", Context.MODE_PRIVATE); }
    static JSONObject config(Context c) throws JSONException {
        var p = get(c);
        return new JSONObject().put("mode", p.getString("mode", "demo")).put("enabled", p.getBoolean("enabled", false))
            .put("activation",p.getString("activation","commute")).put("direction",p.getString("direction","auto"))
            .put("commuteStart",p.getString("commuteStart","06:00")).put("commuteEnd",p.getString("commuteEnd","10:00"))
            .put("homeStart",p.getString("homeStart","18:00")).put("homeEnd",p.getString("homeEnd","24:00"))
            .put("busKeySet", !p.getString("busKey", "").isEmpty()).put("subwayKeySet", !p.getString("subwayKey", "").isEmpty())
            .put("proximityEnabled",p.getBoolean("proximityEnabled",false)).put("preciseLocationGranted",Proximity.precise(c)).put("backgroundLocationGranted",Proximity.background(c))
            .put("proximityStatus",p.getString("proximityStatus","꺼짐"))
            .put("platform", "android").put("notificationsGranted", c.getSystemService(NotificationManager.class).areNotificationsEnabled())
            .put("exactAlarmGranted", Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms());
    }
    static void save(Context c, JSONObject data) {
        var p=get(c);
        String cs=data.optString("commuteStart",p.getString("commuteStart","06:00")),ce=data.optString("commuteEnd",p.getString("commuteEnd","10:00")),hs=data.optString("homeStart",p.getString("homeStart","18:00")),he=data.optString("homeEnd",p.getString("homeEnd","24:00"));
        if(!SchedulePolicy.valid(cs,ce,hs,he)) throw new IllegalArgumentException("출근 시작 < 출근 종료 ≤ 퇴근 시작 < 퇴근 종료 순서로 설정하세요.");
        String activation=data.optString("activation",p.getString("activation","commute")),direction=data.optString("direction",p.getString("direction","auto"));
        if(!java.util.Arrays.asList("commute","always").contains(activation) || !java.util.Arrays.asList("auto","commute","home").contains(direction)) throw new IllegalArgumentException("활성화 방식과 방향을 확인하세요.");
        var e = get(c).edit();
        if(data.has("proximityEnabled")) e.putBoolean("proximityEnabled",data.optBoolean("proximityEnabled"));
        e.putString("activation",activation).putString("direction",direction).putString("commuteStart",cs).putString("commuteEnd",ce).putString("homeStart",hs).putString("homeEnd",he);
        if (data.optString("mode").equals("demo") || data.optString("mode").equals("live")) e.putString("mode", data.optString("mode"));
        for (String key : new String[]{"busKey", "subwayKey"}) if (!data.optString(key).trim().isEmpty()) e.putString(key, data.optString(key).trim());
        if (data.optBoolean("clearKeys")) { e.remove("busKey"); e.remove("subwayKey"); }
        if(data.optBoolean("clearKeys") || !data.optString("busKey").trim().isEmpty()) { e.remove("legs_home"); e.remove("legs_commute"); }
        e.apply(); TransitApi.get(c).reset(); ScheduleReceiver.schedule(c);
    }
}
