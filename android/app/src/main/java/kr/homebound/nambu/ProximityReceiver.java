package kr.homebound.nambu;

import android.content.*;
import com.google.android.gms.location.*;
import org.json.*;
import java.util.*;

public class ProximityReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        GeofencingEvent event = GeofencingEvent.fromIntent(intent);
        if (event == null) return;
        var prefs = Prefs.get(c);
        if (event.hasError()) { prefs.edit().putString("proximityStatus", "위치 감지 오류 · 위치 설정을 확인하세요.").apply(); return; }
        if (!prefs.getBoolean("enabled", false) || !prefs.getBoolean("proximityEnabled", false)) return;
        try {
            JSONArray points = Proximity.places(c).getJSONArray("points");
            Set<String> entering = new HashSet<>(), changed = new HashSet<>(); var edit = prefs.edit();
            var triggering = event.getTriggeringGeofences(); if (triggering == null) return;
            boolean enter = event.getGeofenceTransition() == Geofence.GEOFENCE_TRANSITION_ENTER;
            if (!enter && event.getGeofenceTransition() != Geofence.GEOFENCE_TRANSITION_EXIT) return;
            for (Geofence fence : triggering) for (int i = 0; i < points.length(); i++) {
                JSONObject p = points.getJSONObject(i);
                if (!p.getString("id").equals(fence.getRequestId())) continue;
                if (p.has("direction") && !p.getString("direction").equals(ScheduleReceiver.direction(c))) continue;
                String group = p.getString("group"), id = p.getString("id");
                changed.add(group); if (enter) entering.add(group);
                edit.putBoolean("geo_inside_" + id, enter);
            }
            edit.apply(); long now = System.currentTimeMillis();
            for (String group : changed) {
                String stateKey = "geo_alert_inside_" + group + "_" + ScheduleReceiver.direction(c);
                if (entering.contains(group)) {
                    if (ProximityPolicy.shouldAlert(true, ScheduleReceiver.inWindow(c), prefs.getBoolean(stateKey, false), prefs.getLong("geo_last_" + group, 0), now)) {
                        Proximity.popup(c, group, false);
                        prefs.edit().putBoolean(stateKey, true).putLong("geo_last_" + group, now).apply();
                    }
                } else {
                    boolean stillInside = false;
                    for (int i = 0; i < points.length(); i++) { JSONObject p = points.getJSONObject(i); if (p.getString("group").equals(group) && (!p.has("direction") || p.getString("direction").equals(ScheduleReceiver.direction(c))) && prefs.getBoolean("geo_inside_" + p.getString("id"), false)) stillInside = true; }
                    if (!stillInside) prefs.edit().putBoolean(stateKey, false).apply();
                }
            }
        } catch (Exception ignored) { prefs.edit().putString("proximityStatus", "위치 알림 처리 실패").apply(); }
    }
}
