import io.github.marouane4142.maros.RecentPolicy;
import java.util.*;
public class RecentPolicyQA {
    static int checks=0;static void check(boolean result,String name){checks++;if(!result)throw new AssertionError(name);}
    static RecentPolicy.Record r(String pkg,String title,long date,String series){RecentPolicy.Record r=new RecentPolicy.Record();r.packageName=pkg;r.title=title;r.lastSeen=date;r.seriesId=series;return r;}
    public static void main(String[] args){long now=1793232000000L;
        check(!RecentPolicy.continuation(2,600000,1800000,now,now),"new release is not watched");
        check(!RecentPolicy.continuation(1,600000,1800000,now,now),"next episode is not watched");
        check(!RecentPolicy.continuation(3,600000,1800000,now,now),"watchlist is not watched");
        check(!RecentPolicy.continuation(0,600000,1800000,0,now),"missing date rejected");
        check(!RecentPolicy.continuation(0,600000,1800000,now-31L*86400000,now),"stale rejected");
        check(!RecentPolicy.continuation(0,600000,1800000,now+3600000,now),"future rejected");
        check(!RecentPolicy.continuation(0,1000,1800000,now,now),"preview rejected");
        check(!RecentPolicy.continuation(0,1770000,1800000,now,now),"completed rejected for continue");
        check(RecentPolicy.continuation(0,600000,1800000,now,now),"real partial dated accepted");
        check(RecentPolicy.timestamp(now/1000)==now,"seconds normalized");
        check(RecentPolicy.timestamp(now)==now,"millis retained");
        List<RecentPolicy.Record> records=Arrays.asList(r("netflix","A older",now-5000,"a"),r("prime","B",now-2000,"b"),r("crunchy","C",now-3000,"c"),r("netflix","A newer",now-1000,"a"),r("prime","no date",0,""),r("prime","future",now+3600000,""));
        List<RecentPolicy.Record> out=RecentPolicy.chronological(records,now,2,12);
        check(out.size()==3,"duplicates and invalid dates removed");
        check(out.get(0).title.equals("A newer")&&out.get(1).title.equals("B")&&out.get(2).title.equals("C"),"cross-service chronology");
        check(RecentPolicy.chronological(records,now,2,2).size()==2,"global cap");
        List<RecentPolicy.Record> same=Arrays.asList(r("a","1",now,"1"),r("a","2",now-1,"2"),r("a","3",now-2,"3"));
        check(RecentPolicy.chronological(same,now,2,12).size()==2,"per-service cap");
        RecentPolicy.Record observation=r("a","Observed",now,"one");observation.observed=true;out=RecentPolicy.chronological(Arrays.asList(r("a","Provider",now,"one"),observation),now,2,12);
        check(out.size()==1&&out.get(0).observed,"observed tie preferred");
        check(!RecentPolicy.completed(60000,120000),"short tracks are not completed videos");
        System.out.println("PASS: "+checks+" chronology assertions");
    }
}
