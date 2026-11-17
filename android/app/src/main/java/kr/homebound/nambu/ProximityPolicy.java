package kr.homebound.nambu;

/** Shared entry suppression rule; no coordinates or location history are persisted. */
final class ProximityPolicy {
    static final long COOLDOWN_MS = 15 * 60_000L;
    static boolean shouldAlert(boolean enabled, boolean active, boolean wasInside, long lastAlert, long now) {
        return enabled && active && !wasInside && (lastAlert == 0 || now - lastAlert >= COOLDOWN_MS);
    }
    static String guidance(String group, String direction) {
        boolean commute = "commute".equals(direction);
        if ("nambu".equals(group)) return commute ? "남부터미널역 · 3호선 상행, 경복궁 방면을 확인하세요." : "남부터미널.한일시멘트 · 461 / 641 / 4319, 한국제약바이오협회 방면을 확인하세요.";
        if ("bio".equals(group)) return commute ? "461 / 641 / 4319 · 남부터미널 방면을 확인하세요." : "한국제약바이오협회 하차 지점 근처입니다.";
        return commute ? "경복궁역 도착 지점 근처입니다." : "3호선 하행 · 남부터미널, 오금 방면을 확인하세요.";
    }
}
