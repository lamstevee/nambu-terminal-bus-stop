package kr.homebound.nambu;
public class ProximityPolicyCheck {
    static void check(boolean condition) {if(!condition)throw new AssertionError("proximity rule failed");}
    public static void main(String[] args){
        long now=10_000_000;
        check(ProximityPolicy.shouldAlert(true,true,false,0,now));
        check(!ProximityPolicy.shouldAlert(false,true,false,0,now));
        check(!ProximityPolicy.shouldAlert(true,false,false,0,now));
        check(!ProximityPolicy.shouldAlert(true,true,true,0,now));
        check(!ProximityPolicy.shouldAlert(true,true,false,now-899_999,now));
        check(ProximityPolicy.shouldAlert(true,true,false,now-900_000,now));
        check(ProximityPolicy.guidance("nambu","commute").contains("상행"));
        check(ProximityPolicy.guidance("nambu","home").contains("461"));
        check(ProximityPolicy.guidance("bio","commute").contains("남부터미널"));
        check(ProximityPolicy.guidance("bio","home").contains("하차"));
        check(ProximityPolicy.guidance("palace","home").contains("하행"));
        System.out.println("Proximity policy checks passed: windows, enabled, overlapping entry suppression, re-entry cooldown, direction guidance.");
    }
}
