package io.github.marouane4142.maros;

import java.util.*;

/** Pure chronology rules, independently executable on a desktop JVM. */
public final class RecentPolicy {
    public static final long RETENTION=30L*86400000;
    public static class Record {
        public String title="",subtitle="",imageUri="",intentUri="",packageName="",id="",seriesId="",episode="",source="provider";
        public long position,duration,lastSeen;
        public boolean watchNext,completed,observed;
        public int nextType;
    }
    public static long timestamp(long value){return value>1000000000L&&value<100000000000L?value*1000:value;}
    public static boolean recent(long value,long now){return value>0&&value<=now+300000&&value>=now-RETENTION;}
    public static boolean completed(long pos,long duration){return duration>180000&&pos>0&&(pos*1.0/duration>=.95||duration-pos<=45000);}
    public static boolean continuation(int type,long pos,long duration,long when,long now){return type==0&&recent(when,now)&&pos>=15000&&!completed(pos,duration);}
    public static String normal(String value){return value==null?"":value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+","").trim();}
    public static String seriesKey(Record r){return r.packageName+"|"+(!r.seriesId.isEmpty()?r.seriesId:normal(r.title));}
    public static <T extends Record> List<T> chronological(List<T> input,long now,int perService,int maximum){
        List<T> sorted=new ArrayList<>();for(T r:input)if(recent(r.lastSeen,now)&&!r.title.trim().isEmpty())sorted.add(r);
        Collections.sort(sorted,(a,b)->{int time=Long.compare(b.lastSeen,a.lastSeen);return time!=0?time:Integer.compare(a.observed?0:1,b.observed?0:1);});
        Set<String> series=new HashSet<>(),contents=new HashSet<>();Map<String,Integer> counts=new HashMap<>();List<T> out=new ArrayList<>();
        for(T r:sorted){String key=seriesKey(r),content=r.packageName+"|"+normal(r.title)+"|"+normal(r.episode);int n=counts.containsKey(r.packageName)?counts.get(r.packageName):0;if(series.contains(key)||contents.contains(content)||n>=perService)continue;series.add(key);contents.add(content);counts.put(r.packageName,n+1);out.add(r);if(out.size()>=maximum)break;}return out;
    }
}
