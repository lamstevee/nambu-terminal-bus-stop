package kr.homebound.nambu;
import android.content.Context;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

final class TransitApi {
    static final String END = "한국제약바이오협회";
    static final String[] ROUTES = {"461", "641", "4319"};
    private static volatile String popupCache;
    private static volatile String popupDirection;
    static String cachedArrivalText(String direction) {
        try {
            String raw=popupCache; if(raw==null || !direction.equals(popupDirection)) return "앱을 열어 최신 버스 도착시간을 확인하세요.";
            JSONObject cached=new JSONObject(raw);long now=System.currentTimeMillis();
            if(now-cached.optLong("fetchedAt")>cached.optLong("staleAfterMs",135000)) return "앱을 열어 최신 버스 도착시간을 확인하세요.";
            JSONArray buses=cached.optJSONArray("buses");java.util.ArrayList<String> rows=new java.util.ArrayList<>();
            if(buses!=null)for(int i=0;i<buses.length();i++){JSONObject bus=buses.getJSONObject(i),a=bus.optJSONObject("first");long seconds=a==null ? -1 : (a.optLong("dueAt")-now)/1000;if(seconds>0)rows.add(bus.optString("number")+": "+("minute".equals(a.optString("precision")) ? "약 " : "")+seconds/60+"분 "+seconds%60+"초");}
            return ("demo".equals(cached.optString("mode")) ? "[가상 데이터] " : "최근 조회 · ")+ (rows.isEmpty() ? "최신 도착정보를 확인하세요." : String.join(" / ",rows));
        }catch(Exception e){return "앱을 열어 최신 버스 도착시간을 확인하세요.";}
    }
    private JSONObject publish(JSONObject data,String direction) { popupCache=data.toString();popupDirection=direction;return data; }
    @android.annotation.SuppressLint("StaticFieldLeak") // Only application context is retained; never an activity.
    private static TransitApi instance;
    static synchronized TransitApi get(Context c) { if (instance == null) instance = new TransitApi(c.getApplicationContext()); return instance; }
    private final Context context;
    private JSONArray legs;
    private long legsAt, busAt, subwayAt;
    private JSONObject lastBus, lastSubway;
    private String busError, subwayError;
    private String cachedDirection;
    static JSONObject trip(String direction) throws JSONException {
      boolean commute="commute".equals(direction);
      return new JSONObject().put("direction",direction).put("label",commute ? "출근" : "퇴근").put("origin",commute ? END : "남부터미널역(한일시멘트)")
        .put("destination",commute ? "남부터미널" : END).put("subwayLine",commute ? "상행" : "하행").put("subwayDirection",commute ? "경복궁 · 구파발/대화 방면" : "오금 방면")
        .put("subwayStations",new JSONArray(commute ? new String[]{"남부터미널","경복궁"} : new String[]{"경복궁","남부터미널"})).put("headwayMinutes",commute ? 6 : JSONObject.NULL);
    }
    private final long demoEpoch = System.currentTimeMillis();
    private TransitApi(Context c) { context = c; }
    synchronized void reset() { popupCache=null; legs = null; lastBus = null; lastSubway = null; busAt = 0; subwayAt = 0; busError = null; subwayError = null; }
    private String encode(String s) { try { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (UnsupportedEncodingException e) { throw new IllegalStateException(e); } }
    private String read(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("교통정보 서버 연결 실패 (" + connection.getResponseCode() + ")");
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192]; int n;
                while ((n = in.read(bytes)) != -1) { out.write(bytes, 0, n); if (out.size() > 2000000) throw new IOException("API 응답이 너무 큽니다."); }
                return out.toString("UTF-8");
            }
        } finally { connection.disconnect(); }
    }
    private JSONArray bus(String service, String method, String key, String... params) throws Exception {
        String decoded; try { decoded = key.contains("%") ? URLDecoder.decode(key, "UTF-8") : key; } catch (Exception e) { decoded = key; }
        StringBuilder url = new StringBuilder("http://ws.bus.go.kr/api/rest/" + service + "/" + method + "?serviceKey=" + encode(decoded) + "&resultType=json");
        for (int i = 0; i < params.length; i += 2) url.append('&').append(params[i]).append('=').append(encode(params[i + 1]));
        String raw = read(url.toString()); JSONObject root;
        if (raw.trim().startsWith("{")) { root = new JSONObject(raw); if (root.has("ServiceResult")) root = root.getJSONObject("ServiceResult"); }
        else {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = f.newDocumentBuilder().parse(new InputSource(new StringReader(raw)));
            root = new JSONObject(); NodeList headers = doc.getElementsByTagName("msgHeader");
            if (headers.getLength() > 0) root.put("msgHeader", xmlObject(headers.item(0)));
            JSONArray rows = new JSONArray(); NodeList items = doc.getElementsByTagName("itemList");
            for (int i = 0; i < items.getLength(); i++) rows.put(xmlObject(items.item(i)));
            root.put("msgBody", new JSONObject().put("itemList", rows));
        }
        JSONObject header = root.optJSONObject("msgHeader");
        if (header == null) throw new IOException("버스 API 인증 오류. 서비스키와 활용신청을 확인하세요.");
        String code = header.optString("headerCd");
        if (code.equals("4")) return new JSONArray();
        if (!code.equals("0")) throw new IOException("버스 API 오류 (" + code + "). 서비스키·활용신청·호출량을 확인하세요.");
        Object items = root.optJSONObject("msgBody") == null ? null : root.getJSONObject("msgBody").opt("itemList");
        return items instanceof JSONArray ? (JSONArray) items : items instanceof JSONObject ? new JSONArray().put(items) : new JSONArray();
    }
    private JSONObject xmlObject(Node node) throws JSONException {
        JSONObject object = new JSONObject(); NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) { Node n = children.item(i); if (n.getNodeType() == Node.ELEMENT_NODE) object.put(n.getNodeName(), n.getTextContent()); }
        return object;
    }
    private JSONArray resolve(String key,String direction) throws Exception {
        long now = System.currentTimeMillis();
        if (legs != null && now - legsAt < 86400000) return legs;
        var prefs = Prefs.get(context);
        String cache="legs_"+direction;
        if (prefs.contains(cache) && now - prefs.getLong(cache+"At", 0) < 86400000) { legs = new JSONArray(prefs.getString(cache, "[]")); legsAt = prefs.getLong(cache+"At", 0); if (legs.length() == 3) return legs; }
        String from=direction.equals("commute") ? END : "남부터미널",to=direction.equals("commute") ? "남부터미널" : END;
        JSONArray resolved = new JSONArray(); String originId = null;
        for (String number : ROUTES) {
            JSONArray routes = bus("busRouteInfo", "getBusRouteList", key, "strSrch", number); String routeId = null;
            for (int i = 0; i < routes.length(); i++) if (routes.getJSONObject(i).optString("busRouteNm").equals(number)) routeId = routes.getJSONObject(i).getString("busRouteId");
            if (routeId == null) throw new IOException(number + " 노선을 찾지 못했습니다.");
            JSONArray rows = bus("busRouteInfo", "getStaionByRoute", key, "busRouteId", routeId);
            ArrayList<JSONObject> sorted = new ArrayList<>(); for (int i = 0; i < rows.length(); i++) sorted.add(rows.getJSONObject(i));
            sorted.sort(Comparator.comparingInt(o -> o.optInt("seq")));
            JSONObject leg = null;
            for (int i = 0; i < sorted.size() && leg == null; i++) {
                JSONObject origin = sorted.get(i); if (!origin.optString("stationNm").contains(from)) continue;
                for (int j = i + 1; j < Math.min(i + 11, sorted.size()); j++) {
                    JSONObject station = sorted.get(j);
                    if (station.optString("stationNm").contains(from)) break;
                    if (station.optString("stationNm").contains(to)) {
                        JSONArray stops = new JSONArray(); for (int k = i; k <= j; k++) stops.put(sorted.get(k).optString("stationNm"));
                        leg = new JSONObject().put("number", number).put("routeId", routeId).put("origin", origin).put("stops", stops); break;
                    }
                    if (station.optString("transYn").equals("Y")) break;
                }
            }
            if (leg == null) throw new IOException(number+" "+to+" 방향 정류장을 확인하지 못했습니다.");
            String id = leg.getJSONObject("origin").optString("station");
            if (originId != null && !originId.equals(id)) throw new IOException("세 노선의 승차 정류장이 일치하지 않습니다.");
            originId = id; resolved.put(leg);
        }
        legs = resolved; legsAt = now; prefs.edit().putString(cache, legs.toString()).putLong(cache+"At", legsAt).apply(); return legs;
    }
    static JSONObject arrival(String message, long now) throws JSONException {
        JSONObject a = new JSONObject().put("message", message.isEmpty() ? "도착정보 없음" : message).put("dueAt", JSONObject.NULL).put("precision", "none");
        if (Pattern.compile("운행종료|출발대기|정보없|회차대기").matcher(message).find()) return a;
        if (Pattern.compile("곧 도착|곧도착|도착예정").matcher(message).find()) return a.put("precision", "soon");
        Matcher min = Pattern.compile("(\\d+)\\s*분").matcher(message), sec = Pattern.compile("(\\d+)\\s*초").matcher(message);
        boolean m = min.find(), s = sec.find();
        if (m || s) a.put("dueAt", now + ((m ? Long.parseLong(min.group(1)) * 60 : 0) + (s ? Long.parseLong(sec.group(1)) : 0)) * 1000).put("precision", s ? "second" : "minute");
        return a;
    }
    private JSONObject arrivals(String key,String direction) throws Exception {
        JSONArray legs = resolve(key,direction); JSONObject origin = legs.getJSONObject(0).getJSONObject("origin");
        String arsId = origin.optString("arsId", origin.optString("stationNo")); while (arsId.length() < 5) arsId = "0" + arsId;
        if (!arsId.matches("\\d{5}") || arsId.equals("00000")) throw new IOException("승차 정류장 ARS 번호가 없습니다.");
        JSONArray rows = bus("stationinfo", "getStationByUid", key, "arsId", arsId), buses = new JSONArray(); long now = System.currentTimeMillis();
        for (int i = 0; i < legs.length(); i++) {
            JSONObject leg = legs.getJSONObject(i), item = null;
            for (int j = 0; j < rows.length(); j++) {
                JSONObject r = rows.getJSONObject(j);
                if (r.optString("busRouteId").equals(leg.optString("routeId")) && r.optString("staOrd").equals(leg.getJSONObject("origin").optString("seq"))) { item = r; break; }
            }
            buses.put(new JSONObject().put("number", leg.getString("number")).put("direction",direction.equals("commute") ? "남부터미널" : END).put("stopCount", leg.getJSONArray("stops").length() - 1)
                .put("stops", leg.getJSONArray("stops")).put("first", arrival(item == null ? "" : item.optString("arrmsg1"), now)).put("second", arrival(item == null ? "" : item.optString("arrmsg2"), now)));
        }
        return new JSONObject().put("fetchedAt", now).put("arsId", arsId).put("origin", origin.optString("stationNm")).put("buses", buses);
    }
    private JSONObject subway(String key,String direction) throws Exception {
        if(key.matches("(?i)^https?:.*")) throw new IOException("지하철 입력칸에는 URL 대신 인증키 문자열만 입력하세요.");
        JSONArray stations = new JSONArray();
        for (String name : new String[]{"경복궁", "남부터미널"}) {
            JSONObject root = new JSONObject(read("http://swopenapi.seoul.go.kr/api/subway/" + encode(key.trim()) + "/json/realtimeStationArrival/0/20/" + encode(name)));
            JSONArray rows = root.optJSONArray("realtimeArrivalList");
            if (rows == null && !"INFO-200".equals(root.optJSONObject("errorMessage") == null ? "" : root.getJSONObject("errorMessage").optString("code"))) throw new IOException("지하철 API 키·호출량을 확인하세요.");
            JSONArray trains = new JSONArray();
            if (rows != null) for (int i = 0; i < rows.length() && trains.length() < 2; i++) {
                JSONObject r = rows.getJSONObject(i);
                if (r.optString("subwayId").equals("1003") && r.optString("updnLine").equals(direction.equals("commute") ? "상행" : "하행")) trains.put(new JSONObject().put("destination", r.optString("bstatnNm")).put("message", r.optString("arvlMsg2")).put("receivedAt", r.optString("recptnDt")).put("seconds", r.optInt("barvlDt")));
            }
            stations.put(new JSONObject().put("name", name).put("trains", trains));
        }
        return new JSONObject().put("fetchedAt", System.currentTimeMillis()).put("stations", stations);
    }
    synchronized JSONObject snapshot(boolean test, boolean includeSubway) throws Exception {
        var prefs = Prefs.get(context); String mode = prefs.getString("mode", "demo"), key = prefs.getString("busKey", ""), subwayKey = prefs.getString("subwayKey", "");
        String direction=ScheduleReceiver.direction(context); long interval=ScheduleReceiver.interval(context);
        if(!direction.equals(cachedDirection)) { reset(); cachedDirection=direction; }
        boolean active = test || ScheduleReceiver.inWindow(context); long now = System.currentTimeMillis();
        if (mode.equals("demo")) return publish(demo(active,test,direction).put("trip",trip(direction)).put("pollSeconds",interval/1000).put("staleAfterMs",Math.max(90000,interval*2+15000)),direction);
        JSONObject result = new JSONObject().put("trip",trip(direction)).put("pollSeconds",interval/1000).put("staleAfterMs",Math.max(90000,interval*2+15000)).put("mode", "live").put("active", active).put("testOverride", test).put("buses", new JSONArray());
        if (!active) return result;
        if (key.isEmpty()) return result.put("error", "설정에서 공공데이터포털 버스 서비스키를 입력하세요.");
        if (now - busAt >= interval) {
            busAt = now;
            try { lastBus = arrivals(key,direction); busError = null; } catch (Exception e) { busError = e instanceof IOException ? e.getMessage() : "버스 API 응답 오류. 키와 서비스 승인을 확인하세요."; }
        }
        if (includeSubway && !subwayKey.isEmpty() && now - subwayAt >= Math.max(90000,interval*2)) {
            subwayAt = now;
            try { lastSubway = subway(subwayKey,direction); subwayError = null; } catch (Exception e) { subwayError = "지하철 API 조회 실패. 실시간 지하철 인증키와 호출량을 확인하세요."; }
        }
        if (lastBus != null) { Iterator<String> keys = lastBus.keys(); while (keys.hasNext()) { String k = keys.next(); result.put(k, lastBus.get(k)); } }
        return publish(result.put("error", busError == null ? JSONObject.NULL : busError).put("subway", lastSubway == null ? JSONObject.NULL : lastSubway)
            .put("subwayError", subwayKey.isEmpty() ? "지하철 인증키를 입력하면 3호선 도착정보를 볼 수 있습니다." : subwayError == null ? JSONObject.NULL : subwayError),direction);
    }
    private JSONObject demo(boolean active, boolean test,String direction) throws JSONException {
        long now = System.currentTimeMillis(), base = demoEpoch + ((now - demoEpoch) / 300000) * 300000;
        JSONArray buses = new JSONArray(); int[] seconds = {234, 487, 652}, second = {780, 1090, 1345};
        if (active) for (int i = 0; i < 3; i++) buses.put(new JSONObject().put("number", ROUTES[i]).put("direction",direction.equals("commute") ? "남부터미널" : END)
            .put("first", new JSONObject().put("dueAt", base + seconds[i] * 1000L).put("precision", "second").put("message", "테스트용 가상 도착예측"))
            .put("second", new JSONObject().put("dueAt", base + seconds[i] * 1000L + 360000).put("precision", "second").put("message", "테스트용 가상 도착예측")));
        JSONArray stations = new JSONArray(); for (String name : new String[]{"경복궁", "남부터미널"}) stations.put(new JSONObject().put("name", name).put("trains", new JSONArray().put(new JSONObject().put("destination",direction.equals("commute") ? "구파발" : "오금").put("message","테스트용 가상 열차 · "+(direction.equals("commute") ? "상행" : "하행")))));
        return new JSONObject().put("mode", "demo").put("active", active).put("testOverride", test).put("fetchedAt", now).put("origin",direction.equals("commute") ? END : "남부터미널역(한일시멘트)")
            .put("buses", buses).put("subway", new JSONObject().put("fetchedAt", now).put("stations", stations));
    }
}
