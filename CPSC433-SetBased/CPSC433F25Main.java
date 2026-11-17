import java.nio.file.*;
import java.util.*;

public class CPSC433F25Main {
    static final Random rng = new Random();
    static final int POPULATION = 140, CHILDREN = 180, GENERATIONS = 1800, STAGNATION = 180, RESTARTS = 3;
    static final long TIME_LIMIT_MS = 15000;
    static final List<Slot> slots = new ArrayList<>();
    static final List<Item> items = new ArrayList<>();
    static final List<int[]> conflicts = new ArrayList<>(), pairs = new ArrayList<>();
    static final List<Pref> prefs = new ArrayList<>();
    static final Map<String,Integer> itemIds = new HashMap<>();
    static final Set<String> conflictKeys = new HashSet<>(), pairKeys = new HashSet<>();
    static final Map<Integer,Integer> fixed = new HashMap<>();
    static final Map<Integer,Set<String>> unwanted = new HashMap<>();
    static final List<String[]> rawConflicts = new ArrayList<>(), rawPairs = new ArrayList<>(), rawPrefs = new ArrayList<>(), rawFixed = new ArrayList<>(), rawUnwanted = new ArrayList<>();
    static int[] weights = new int[8];
    static long hardWeight;

    static class Slot {
        String day, time; int start, duration, max, min, alMax; boolean lecture;
        Slot(String[] x, boolean lec) {
            day=x[0]; time=x[1]; start=minutes(time); lecture=lec;
            duration=lec && day.equals("TU") ? 90 : !lec && day.equals("FR") ? 120 : 60;
            max=Integer.parseInt(x[2]); min=Integer.parseInt(x[3]); alMax=Integer.parseInt(x[4]);
        }
        String key(){return day+" "+time;}
        String display(){return day+", "+time;}
    }
    static class Item {
        String name, course; boolean lecture, al; int[] domain;
        Item(String[] x, boolean lec) {
            name=x[0]; lecture=lec; al=Boolean.parseBoolean(x[1]);
            String[] words=name.split("\\s+"); course=words[0]+" "+words[1];
        }
    }
    static class Pref {
        int item, slot, penalty;
        Pref(int a,int b,int c){item=a;slot=b;penalty=c;}
    }
    static class Candidate {
        int[] a; long soft, hard, score;
        Candidate(int[] values){a=values; evaluate(this);}
        String key(){return Arrays.toString(a);}
    }
    static int minutes(String time){String[] p=time.split(":");return Integer.parseInt(p[0])*60+Integer.parseInt(p[1]);}
    static int[] days(Slot s) {
        if(s.lecture) return s.day.equals("MO")?new int[]{0,2,4}:new int[]{1,3};
        if(s.day.equals("MO")) return new int[]{0,2};
        if(s.day.equals("TU")) return new int[]{1,3};
        return new int[]{4};
    }
    static boolean overlaps(int x,int y) {
        Slot a=slots.get(x), b=slots.get(y);
        if(a.start >= b.start+b.duration || b.start >= a.start+a.duration) return false;
        for(int d:days(a)) for(int e:days(b)) if(d==e)return true;
        return false;
    }
    static int slotFor(String day,String time,boolean lecture) {
        for(int i=0;i<slots.size();i++) {
            Slot s=slots.get(i);
            if(s.lecture==lecture && s.day.equals(day) && s.time.equals(time))return i;
        }
        return -1;
    }
    static String[] split(String line){return Arrays.stream(line.split(",")).map(String::trim).toArray(String[]::new);}
    static void addItem(String[] x,boolean lecture){
        int id=items.size();items.add(new Item(x,lecture));itemIds.put(x[0],id);
    }
    static void addConflict(int a,int b){
        if(a==b || a<0 || b<0)return;
        String key=Math.min(a,b)+":"+Math.max(a,b);
        if(conflictKeys.add(key))conflicts.add(new int[]{a,b});
    }
    static void addPair(int a,int b){
        if(a==b || a<0 || b<0)return;
        String key=Math.min(a,b)+":"+Math.max(a,b);
        if(pairKeys.add(key))pairs.add(new int[]{a,b});
    }
    static int id(String name){return itemIds.getOrDefault(name,-1);}
    static void parse(String filename) throws Exception {
        String section="";
        for(String original:Files.readAllLines(Path.of(filename))) {
            String line=original.trim(); if(line.isEmpty())continue;
            if(line.endsWith(":")){section=line.toLowerCase(Locale.ROOT);continue;}
            String[] p=split(line);
            switch(section){
                case "lecture slots:": slots.add(new Slot(p,true));break;
                case "tutorial slots:":slots.add(new Slot(p,false));break;
                case "lectures:":addItem(p,true);break;
                case "tutorials:":addItem(p,false);break;
                case "not compatible:":rawConflicts.add(p);break;
                case "unwanted:":rawUnwanted.add(p);break;
                case "preferences:":rawPrefs.add(p);break;
                case "pair:":rawPairs.add(p);break;
                case "partial assignments:":rawFixed.add(p);break;
            }
        }
        // The special assessment bookings are tutorial-type offerings fixed on TR 18:00.
        for(String[] special:new String[][]{{"351","851"},{"413","913"}}){
            boolean requested=items.stream().anyMatch(i->i.lecture && i.course.equals("CPSC "+special[0]));
            if(requested && id("CPSC "+special[1]+" TUT 01")<0) {
                addItem(new String[]{"CPSC "+special[1]+" TUT 01","false"},false);
                rawFixed.add(new String[]{"CPSC "+special[1]+" TUT 01","TU","18:00"});
            }
        }
        for(String[] p:rawConflicts)addConflict(id(p[0]),id(p[1]));
        for(String[] p:rawPairs)addPair(id(p[0]),id(p[1]));
        for(String[] p:rawUnwanted){int a=id(p[0]);if(a>=0)unwanted.computeIfAbsent(a,k->new HashSet<>()).add(p[1]+" "+p[2]);}
        for(String[] p:rawFixed){
            int a=id(p[0]); if(a<0)throw new IllegalArgumentException("Unknown fixed offering: "+p[0]);
            int s=slotFor(p[1],p[2],items.get(a).lecture);
            if(s<0)throw new IllegalArgumentException("Invalid or unavailable partial assignment: "+String.join(", ",p));
            if(fixed.containsKey(a)&&fixed.get(a)!=s)throw new IllegalArgumentException("Conflicting partial assignments: "+p[0]);
            fixed.put(a,s);
        }
        for(String[] p:rawPrefs){int a=id(p[2]);if(a<0)continue;
            int s=slotFor(p[0],p[1],items.get(a).lecture);
            if(s>=0)prefs.add(new Pref(a,s,Integer.parseInt(p[3])));
        }
        // Built-in conflicts between lectures and their associated tutorials.
        for(int a=0;a<items.size();a++)for(int b=a+1;b<items.size();b++){
            Item x=items.get(a),y=items.get(b);
            if(x.lecture!=y.lecture){
                Item lec=x.lecture?x:y, tut=x.lecture?y:x;
                if(tut.name.startsWith(lec.name+" TUT ")||tut.name.startsWith(lec.name+" LAB ") ||
                   (tut.course.equals(lec.course) && !tut.name.contains(" LEC ")))addConflict(a,b);
            }
            if(x.lecture && y.lecture && x.course.matches("[A-Z]+ 5\\d\\d") && y.course.matches("[A-Z]+ 5\\d\\d"))addConflict(a,b);
        }
        for(String[] special:new String[][]{{"351","851"},{"413","913"}}){
            int specialId=id("CPSC "+special[1]+" TUT 01");
            if(specialId>=0)for(int j=0;j<items.size();j++)if(items.get(j).course.equals("CPSC "+special[0]))addConflict(specialId,j);
        }
        for(int i=0;i<items.size();i++){
            Item a=items.get(i);List<Integer> options=new ArrayList<>();
            for(int s=0;s<slots.size();s++){
                Slot t=slots.get(s);
                if(t.lecture!=a.lecture || t.max<1 || (a.al && t.alMax<1))continue;
                if(fixed.containsKey(i)&&fixed.get(i)!=s)continue;
                if(unwanted.getOrDefault(i,Collections.emptySet()).contains(t.key()))continue;
                if(a.name.matches(".*\\bLEC 9\\d+.*") && t.start<18*60)continue;
                if(a.lecture && t.day.equals("TU") && t.start<750 && t.start+t.duration>660)continue;
                options.add(s);
            }
            if(options.isEmpty())throw new IllegalArgumentException("No legal slot available for: "+a.name);
            a.domain=options.stream().mapToInt(Integer::intValue).toArray();
        }
        long max=0;
        for(Slot s:slots)max+=(long)s.min*(s.lecture?weights[4]:weights[5])*weights[0];
        for(Pref p:prefs)max+=(long)p.penalty*weights[1];
        max+=(long)pairs.size()*weights[6]*weights[2];
        for(int a=0;a<items.size();a++)for(int b=a+1;b<items.size();b++)if(items.get(a).lecture&&items.get(b).lecture&&items.get(a).course.equals(items.get(b).course))max+=(long)weights[7]*weights[3];
        hardWeight=max+1;
    }
    static boolean contains(int[] choices,int x){for(int s:choices)if(s==x)return true;return false;}
    static void evaluate(Candidate c){
        int[] count=new int[slots.size()], alCount=new int[slots.size()];
        for(int i=0;i<items.size();i++){int s=c.a[i];count[s]++;if(items.get(i).al)alCount[s]++;}
        long hard=0, minimum=0, preference=0, pair=0, sections=0;
        for(int s=0;s<slots.size();s++){
            Slot slot=slots.get(s);
            hard+=Math.max(0,count[s]-slot.max)+Math.max(0,alCount[s]-slot.alMax);
            minimum+=(long)Math.max(0,slot.min-count[s])*(slot.lecture?weights[4]:weights[5]);
        }
        for(int[] edge:conflicts)if(overlaps(c.a[edge[0]],c.a[edge[1]]))hard++;
        for(Pref p:prefs)if(c.a[p.item]!=p.slot)preference+=p.penalty;
        for(int[] pairItems:pairs)if(!overlaps(c.a[pairItems[0]],c.a[pairItems[1]]))pair+=weights[6];
        for(int i=0;i<items.size();i++)for(int j=i+1;j<items.size();j++){
            Item a=items.get(i),b=items.get(j);
            if(a.lecture && b.lecture && a.course.equals(b.course) && overlaps(c.a[i],c.a[j]))sections+=weights[7];
        }
        c.hard=hard;c.soft=minimum*weights[0]+preference*weights[1]+pair*weights[2]+sections*weights[3];
        c.score=c.soft+hard*hardWeight;
    }
    static int choice(int[] a){return a[rng.nextInt(a.length)];}
    static int violationIfPlaced(int[] schedule,int item,int slot){
        int count=0,al=0;
        for(int i=0;i<schedule.length;i++)if(i!=item&&schedule[i]==slot){count++;if(items.get(i).al)al++;}
        Slot s=slots.get(slot); int violations=Math.max(0,count+1-s.max);
        if(items.get(item).al)al++;
        violations+=Math.max(0,al-s.alMax);
        for(int[] edge:conflicts){
            int other=edge[0]==item?edge[1]:edge[1]==item?edge[0]:-1;
            if(other>=0&&schedule[other]>=0&&overlaps(slot,schedule[other]))violations++;
        }
        return violations;
    }
    static Candidate randomCandidate(boolean greedy){
        int[] a=new int[items.size()];Arrays.fill(a,-1);
        if(!greedy){for(int i=0;i<a.length;i++)a[i]=choice(items.get(i).domain);return new Candidate(a);}
        List<Integer> order=new ArrayList<>();for(int i=0;i<a.length;i++)order.add(i);
        Collections.shuffle(order,rng);
        order.sort(Comparator.comparingInt((Integer i)->items.get(i).domain.length).thenComparingInt(i->-degree(i)));
        for(int i:order){int best=Integer.MAX_VALUE;List<Integer> options=new ArrayList<>();
            for(int s:items.get(i).domain){int penalty=violationIfPlaced(a,i,s);
                if(penalty<best){best=penalty;options.clear();}if(penalty==best)options.add(s);
            }
            a[i]=options.get(rng.nextInt(options.size()));
        }
        return new Candidate(a);
    }
    static int degree(int i){int result=0;for(int[] p:conflicts)if(p[0]==i||p[1]==i)result++;return result;}
    static Candidate tournament(List<Candidate> population){
        Candidate best=null;for(int k=0;k<4;k++){
            Candidate c=population.get(rng.nextInt(population.size()));
            if(best==null||c.score<best.score)best=c;
        }return best;
    }
    static Candidate offspring(List<Candidate> population){
        Candidate p=tournament(population);int[] a=p.a.clone();double op=rng.nextDouble();
        if(op<0.22){ // move
            int i=rng.nextInt(a.length);a[i]=choice(items.get(i).domain);
        }else if(op<0.37){ // same-kind swap
            int i=rng.nextInt(a.length),j=rng.nextInt(a.length);
            if(items.get(i).lecture==items.get(j).lecture && contains(items.get(i).domain,a[j]) && contains(items.get(j).domain,a[i])){
                int temp=a[i];a[i]=a[j];a[j]=temp;
            }
        }else if(op<0.57){ // lecture + associated tutorials cascade
            List<Integer> lectures=new ArrayList<>();for(int i=0;i<a.length;i++)if(items.get(i).lecture)lectures.add(i);
            if(!lectures.isEmpty()){
                int i=lectures.get(rng.nextInt(lectures.size()));a[i]=choice(items.get(i).domain);
                for(int j=0;j<a.length;j++){
                    Item t=items.get(j);
                    if(!t.lecture && (t.name.startsWith(items.get(i).name+" TUT ")||t.name.startsWith(items.get(i).name+" LAB ")) && overlaps(a[i],a[j])){
                        List<Integer> possible=new ArrayList<>();for(int s:t.domain)if(!overlaps(a[i],s))possible.add(s);
                        if(!possible.isEmpty())a[j]=possible.get(rng.nextInt(possible.size()));
                    }
                }
            }
        }else{ // crossover by course
            Candidate other=tournament(population);Map<String,Boolean> takeFirst=new HashMap<>();
            for(int i=0;i<a.length;i++){
                boolean first=takeFirst.computeIfAbsent(items.get(i).course,k->rng.nextBoolean());
                a[i]=first?p.a[i]:other.a[i];
            }
        }
        Candidate c=new Candidate(a);
        if(c.hard>0 && rng.nextDouble()<0.85)repair(c);
        return c;
    }
    static void repair(Candidate c){
        // Move an item involved in a conflict to a slot that reduces violations.
        for(int attempt=0;attempt<4 && c.hard>0;attempt++){
            List<Integer> bad=new ArrayList<>();
            for(int[] edge:conflicts)if(overlaps(c.a[edge[0]],c.a[edge[1]])){
                bad.add(edge[0]);bad.add(edge[1]);
            }
            int[] count=new int[slots.size()],al=new int[slots.size()];
            for(int i=0;i<c.a.length;i++){count[c.a[i]]++;if(items.get(i).al)al[c.a[i]]++;}
            for(int i=0;i<c.a.length;i++){
                Slot s=slots.get(c.a[i]);
                if(count[c.a[i]]>s.max || (items.get(i).al&&al[c.a[i]]>s.alMax))bad.add(i);
            }
            if(bad.isEmpty())return;
            Collections.shuffle(bad,rng);boolean improved=false;
            for(int item:bad){
                int old=c.a[item]; long oldHard=c.hard; Candidate best=c;
                for(int slot:items.get(item).domain){
                    if(slot==old)continue;
                    int[] moved=c.a.clone();moved[item]=slot;Candidate test=new Candidate(moved);
                    if(test.hard<best.hard || (test.hard==best.hard && test.score<best.score))best=test;
                }
                if(best.hard<oldHard){System.arraycopy(best.a,0,c.a,0,c.a.length);evaluate(c);improved=true;break;}
            }
            if(!improved)break;
        }
    }
    static List<Candidate> select(Collection<Candidate> candidates,int size){
        Map<String,Candidate> unique=new HashMap<>();for(Candidate c:candidates)unique.putIfAbsent(c.key(),c);
        List<Candidate> result=new ArrayList<>(unique.values());
        result.sort(Comparator.comparingLong((Candidate c)->c.score));
        if(result.size()>size)return new ArrayList<>(result.subList(0,size));
        return result;
    }
    static Candidate search(){
        List<Candidate> population=new ArrayList<>();Set<String> seen=new HashSet<>();
        int attempts=0;
        while(population.size()<POPULATION && attempts++<POPULATION*20){
            Candidate c=randomCandidate(attempts%4!=0);
            if(seen.add(c.key()))population.add(c);
        }
        if(population.isEmpty())return null;
        Candidate best=null;int stale=0,restarts=0;long start=System.currentTimeMillis();
        for(int generation=0;generation<GENERATIONS && System.currentTimeMillis()-start<TIME_LIMIT_MS;generation++){
            for(Candidate c:population)if(c.hard==0 && (best==null||c.soft<best.soft)){best=c;stale=0;}
            if(best!=null&&best.soft==0)break;
            if(stale>=STAGNATION){
                if(restarts++>=RESTARTS)break;
                population=select(population,Math.min(8,population.size()));
                seen.clear();for(Candidate c:population)seen.add(c.key());
                for(int tries=0;population.size()<POPULATION&&tries<POPULATION*30;tries++){
                    Candidate c=randomCandidate(tries%4!=0);if(seen.add(c.key()))population.add(c);
                }
                stale=0;
            }
            List<Candidate> pool=new ArrayList<>(population);
            for(int i=0;i<CHILDREN;i++)pool.add(offspring(population));
            population=select(pool,POPULATION);
            stale++;
        }
        for(Candidate c:population)if(c.hard==0 && (best==null||c.soft<best.soft))best=c;
        return best;
    }
    static void printResult(Candidate best){
        if(best==null){System.out.println("No valid solution found (search is not exhaustive).");return;}
        System.out.println("Eval-value: "+best.soft);
        List<Integer> order=new ArrayList<>();for(int i=0;i<items.size();i++)order.add(i);
        order.sort(Comparator.comparing(i->items.get(i).name));
        for(int i:order)System.out.printf("%-33s : %s%n",items.get(i).name,slots.get(best.a[i]).display());
    }
    public static void main(String[] args){
        if(args.length<9){System.err.println("Usage: java CPSC433F25Main input.txt wminfilled wpref wpair wsecdiff penlecturemin pentutorialmin pennotpaired pensection [seed]");return;}
        try{
            for(int i=0;i<8;i++)weights[i]=Integer.parseInt(args[i+1]);
            if(args.length>=10)rng.setSeed(Long.parseLong(args[9]));
            parse(args[0]);
            printResult(search());
        }catch(Exception ex){System.err.println("Error: "+ex.getMessage());}
    }
}
