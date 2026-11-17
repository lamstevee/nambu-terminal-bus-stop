package kr.homebound.nambu;
import java.time.*;
final class SchedulePolicy {
 static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
 static int minute(String time) { String[] v = time.split(":"); return Integer.parseInt(v[0])*60+Integer.parseInt(v[1]); }
 static int minute(ZonedDateTime now) { now=now.withZoneSameInstant(SEOUL); return now.getHour()*60+now.getMinute(); }
 static boolean active(boolean always,int cs,int ce,int hs,int he,ZonedDateTime now) { int m=minute(now); return always || m>=cs && m<ce || m>=hs && m<he; }
 static String direction(String selected,int cs,int ce,int hs,int he,ZonedDateTime now) {
  if (!"auto".equals(selected)) return selected;
  int m=minute(now); if(m>=cs && m<ce) return "commute"; if(m>=hs && m<he) return "home"; return m<720 ? "commute" : "home";
 }
 static boolean valid(String cs,String ce,String hs,String he) {
  String[] times={cs,ce,hs,he};
  for(int i=0;i<4;i++) if(!times[i].matches("(?:[01]\\d|2[0-3]):[0-5]\\d") && !((i==1 || i==3) && times[i].equals("24:00"))) return false;
  return minute(cs)<minute(ce) && minute(ce)<=minute(hs) && minute(hs)<minute(he);
 }
 static long nextBoundary(int[] minutes,ZonedDateTime now) {
  ZonedDateTime day=now.withZoneSameInstant(SEOUL).toLocalDate().atStartOfDay(SEOUL); long next=Long.MAX_VALUE;
  for(int m:minutes) { ZonedDateTime at=day.plusMinutes(m); if(!at.isAfter(now)) at=at.plusDays(1); next=Math.min(next,at.toInstant().toEpochMilli()); }
  return next;
 }
}
