package kr.homebound.nambu;
import java.time.*;
public class SchedulePolicyCheck {
 static ZonedDateTime at(String time){return ZonedDateTime.parse("2026-10-08T"+time+"+09:00");}
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[]args){
  for(String time:new String[]{"06:00:00","09:59:59","18:00:00","23:59:59"})check(SchedulePolicy.active(false,360,600,1080,1440,at(time)));
  for(String time:new String[]{"05:59:59","10:00:00","17:59:59","00:00:00"})check(!SchedulePolicy.active(false,360,600,1080,1440,at(time)));
  check(SchedulePolicy.active(true,360,600,1080,1440,at("12:00:00")));
  check(SchedulePolicy.direction("auto",360,600,1080,1440,at("08:00:00")).equals("commute"));
  check(SchedulePolicy.direction("auto",360,600,1080,1440,at("19:00:00")).equals("home"));
  check(SchedulePolicy.direction("home",360,600,1080,1440,at("08:00:00")).equals("home"));
  check(SchedulePolicy.valid("06:00","10:00","18:00","24:00"));check(!SchedulePolicy.valid("10:00","06:00","18:00","24:00"));
  check(SchedulePolicy.nextBoundary(new int[]{360,600,1080,1440},at("06:00:00"))==at("10:00:00").toInstant().toEpochMilli());
  check(SchedulePolicy.nextBoundary(new int[]{360,600,1080,1440},at("23:59:59"))==ZonedDateTime.parse("2026-10-09T00:00:00+09:00").toInstant().toEpochMilli());
  System.out.println("Android schedule checks passed.");
 }
}
