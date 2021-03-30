import static java.lang.StrictMath.abs;
import java.util.Arrays;
import java.util.Random;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

// recursion deep down, n=500, 54s, Count: 348496095
// dyna: Count n=500, count=(25825, 712277, 14161374), 8s
// no duplicates: Count n=500, count=(25825, 513849, 4467560), 19s

public class DynaPoolPoC {
    final int MAX_CUST = 75;
    final int MAX_LEV = 4;
    final int MAX_TRIP = 4;
    final int MAX_STAND = 50;
    final double MAX_LOSS = 50;
    int count=0;

    int[] from = new int[MAX_CUST];
    int[] to = new int[MAX_CUST];
    Random rand = new Random(10L);
    List<Branch>[] node = new ArrayList[MAX_LEV-1];
    List<Branch>[] nodeP = new ArrayList[MAX_LEV-1];
    HashMap<String, Branch> map = new HashMap<>();

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        DynaPoolPoC p = new DynaPoolPoC();
        p.initMem();
        p.genDemand();
        p.storeLeaves();
        p.storePickUpLeaves();
        p.dive(0);
        p.diveP(0);
        long time1 = System.currentTimeMillis();
        p.raport();
        p.raportP();
        
        List<Pool> ret = p.mergeTrees();
        
        long time2 = System.currentTimeMillis();
        System.out.println("Time of merge: " + (time2-time1)/1000 + "s");
        System.out.println("Size after merge: " + ret.size());

        ret = p.removeDuplicates(ret);
        
        long time3 = System.currentTimeMillis();
        System.out.println("Time of rm duplicates: " + (time3-time2)/1000 + "s");
        System.out.println("Total time: " + (double)((time3-start)/1000.0) + "s");
        System.out.println("Size after removing duplicates: " + ret.size());
        //System.out.println("Pools not valid: " + p.poolIsValid(ret));
    }

    private void raport() {
        System.out.println("Count leaves: " + node[MAX_LEV-2].size());
        System.out.println("Count between: " + node[MAX_LEV-3].size());
        System.out.println("Count root: " + map.size());
        //System.out.println("Number of elements: " + myMap2.size());
    }

    private void raportP() {
        System.out.println("Count leaves: " + nodeP[MAX_LEV-2].size());
        System.out.println("Count between: " + nodeP[MAX_LEV-3].size());
        System.out.println("Count root: " + nodeP[MAX_LEV-4].size());
        //System.out.println("Number of elements: " + myMap2.size());
    }

    private void initMem() {
        for (int i=0; i<MAX_LEV-1; i++) {
            node[i] = new ArrayList<>(); 
            nodeP[i] = new ArrayList<>(); 
        }
    }

    public void dive(int lev) {
        if (lev > MAX_LEV-3) return; // last two levels are "leaves"

        dive(lev + 1);
        
        for (int c = 0; c < MAX_CUST; c++) {
            for (Branch b : node[lev+1]) { // we iterate over product of the stage further in the tree: +1
                if (!isFoundInBranchOrTooLong(c, b)) { // one of two in 'b' is dropped off earlier
                    storeBranch(lev, c, b);
                }
            }
        }
        // removing duplicates which come from lev+1
        node[lev] = rmDuplicates(node[lev], lev, true); // true - make hash
    }

    private void storeBranch(int lev, int c, Branch b) {
        int[] drops = new int[MAX_LEV-lev];
        drops[0] = c;
        for(int j=0; j<MAX_LEV-lev-1; j++) // further stage has one passenger less: -1
            drops[j+1] = b.dropoff[j];
        
        Branch b2 = new Branch(c + "-" + b.key, // no sorting as we have to remove lev+1 duplicates eg. 1-4-5 and 1-5-4
                               cost(to[c], to[b.dropoff[0]]) + b.cost, 
                               drops);
        node[lev].add(b2);
    }

    private void storeLeaves() {
        for (int c = 0; c < MAX_CUST; c++) 
            for (int d = 0; d < MAX_CUST; d++) 
                if (c != d && 
                    cost(to[c], to[d]) < cost(from[d], to[d]) * (100.0+MAX_LOSS)/100.0) {
                        int[] drops = new int[2];
                        drops[0] = c;
                        drops[1] = d;
                        String key = c>d? d+"-"+c : c+"-"+d;
                        Branch b = new Branch(key, cost(to[c], to[d]), drops);
                        node[MAX_LEV-2].add(b);
        }
    }

    private void storePickUpLeaves() {
        for (int c = 0; c < MAX_CUST; c++) 
            for (int d = 0; d < MAX_CUST; d++) 
                if (c != d && 
                    cost(from[c], from[d]) < cost(from[d], to[d]) * (100.0+MAX_LOSS)/100.0) {
                        int[] pickup = new int[2];
                        pickup[0] = c;
                        pickup[1] = d;
                        String key = c>d? d+"-"+c : c+"-"+d;
                        Branch b = new Branch(key, cost(from[c], from[d]), pickup);
                        nodeP[MAX_LEV-2].add(b);
        }
    }

    public void diveP(int lev) {
        if (lev > MAX_LEV-3) return; // last two levels are "leaves"

        diveP(lev + 1);
        
        for (int c = 0; c < MAX_CUST; c++) {
            for (Branch b : nodeP[lev+1]) { // we iterate over product of the stage further in the tree: +1
                if (!isFoundInBranchOrTooLongP(c, b)) { // one of two in 'b' is dropped off earlier
                    storeBranchP(lev, c, b);
                }
            }
        }
        // removing duplicates which come from lev+1
        nodeP[lev] = rmDuplicates(nodeP[lev], lev, false);
    }

    private void storeBranchP(int lev, int c, Branch b) {
        int[] pickups = new int[MAX_LEV-lev];
        pickups[0] = c;
        for(int j=0; j<MAX_LEV-lev-1; j++) // further stage has one passenger less: -1
            pickups[j+1] = b.dropoff[j];
        
        Branch b2 = new Branch(c + "-" + b.key, // no sorting as we have to remove lev+1 duplicates eg. 1-4-5 and 1-5-4
                               cost(from[c], from[b.dropoff[0]]) + b.cost, 
                               pickups);
        nodeP[lev].add(b2);
    }

    public boolean isFoundInBranchOrTooLong(int c, Branch b) {
        for (int i=0; i<b.dropoff.length; i++)
            if (b.dropoff[i] == c) return true; // current passenger is in the branch below -> reject that combination
        // now checking if anyone in the branch does not lose too much with the pool
        int wait = cost(to[c], to[b.dropoff[0]]);
        
        for (int i=0; i<b.dropoff.length; i++) {
            if (wait > cost(from[b.dropoff[i]], to[b.dropoff[i]]) * (100.0+MAX_LOSS)/100.0 ) 
                return true;
            if (i+1 < b.dropoff.length)
                wait += cost(to[b.dropoff[i]], to[b.dropoff[i+1]]); 
        }
        return false;
    }

    public boolean isFoundInBranchOrTooLongP(int c, Branch b) {
        for (int i=0; i<b.dropoff.length; i++)
            if (b.dropoff[i] == c) return true; // current passenger is in the branch below -> reject that combination
        // now checking if anyone in the branch does not lose too much with the pool
        int wait = cost(from[c], from[b.dropoff[0]]);
        
        for (int i=0; i<b.dropoff.length; i++) {
            if (wait > cost(from[b.dropoff[i]], to[b.dropoff[i]]) * (100.0+MAX_LOSS)/100.0 ) 
                return true;
            if (i+1 < b.dropoff.length)
                wait += cost(from[b.dropoff[i]], from[b.dropoff[i+1]]); 
        }
        return false;
    }

    private List<Branch> rmDuplicates(List<Branch> node, int lev, boolean makeHash) {
        Branch[] arr = node.toArray(new Branch[0]);
        if (arr == null || arr.length<2) return null;

        // removing duplicates from the previous stage
        // TODO: there might be more duplicates than one at lev==1 or 0 !!!!!!!!!!!!!
        Arrays.sort(arr);
        
        for (int i = 0; i < arr.length-1; i++) {
            if (arr[i].cost == -1) { // this -1 marker is set below
                continue;
            }
            if (arr[i].key.equals(arr[i+1].key)) {
                if (arr[i].cost > arr[i+1].cost)
                    arr[i].cost = -1; // to be deleted
                else arr[i+1].cost = -1;
            }
        }
        // removing but also recreating the key - must be sorted
        
        List<Branch> list = new ArrayList<>();
        
        for (int i = 0; i < arr.length; i++) 
            if (arr[i].cost != -1) {
                String key = arr[i].key;
                if (lev>0 || !makeHash) { // do not sort, it also means - for lev 0 there will be MAX_LEV maps for the same combination, which is great for tree merging 
                    // do sort if lev==0 in pick-up tree (!makeHash)
                    int[] copiedArray = Arrays.copyOf(arr[i].dropoff, arr[i].dropoff.length);
                    Arrays.sort(copiedArray);
                    key="";
                    for (int j = 0; j < copiedArray.length; j++) {
                        key += copiedArray[j];
                        if (j < copiedArray.length-1) key += "-";
                    }
                    arr[i].key = key;
                }
                if (lev == 0 && makeHash) map.put(key, arr[i]);
                else list.add(arr[i]);
            }
        return list;
    }

    private List<Pool> mergeTrees() {
        List<Pool> ret = new ArrayList<>();

        for (Branch p : nodeP[0]) {
            // there might be as many as MAX_LEV hash keys (drop-off plans) for this pick-up plan
            for (int k=0; k<MAX_LEV; k++) {
                String key = genHashKey(p, k);

                Branch d = map.get(key); // get drop-offs for that key
                if (d==null) continue; // drop-off was not acceptable
                // checking if drop-off still acceptable with that pick-up fase
                boolean tooLong= false;
                int i=0;
                int wait = p.cost + cost(from[p.dropoff[p.dropoff.length-1]], to[d.dropoff[i]]);
                while(true) {
                    if (wait > cost(from[d.dropoff[i]], to[d.dropoff[i]]) * (100.0+MAX_LOSS)/100.0) {
                        tooLong = true;
                        break;
                    }
                    if (i == d.dropoff.length -1) break;
                    wait += cost(to[d.dropoff[i]], to[d.dropoff[i+1]]);
                    i++;
                }
                if (tooLong) continue;
                // a viable plan -> merge pickup and dropoff
                int[] both = new int[p.dropoff.length + d.dropoff.length];
                i=0;
                for (; i<p.dropoff.length; i++) both[i] = p.dropoff[i];
                // TODO: Use "Arrays.copyOf", "Arrays.asList", "Collections.addAll" or "System.arraycopy" instead.
                for (; i<p.dropoff.length+d.dropoff.length; i++) both[i] = d.dropoff[i-p.dropoff.length];
                int cost = p.cost + cost(from[p.dropoff[p.dropoff.length-1]], from[d.dropoff[0]]) + d.cost;
                ret.add(new Pool(cost, both));
            }
        }
        return ret;
    }

    private String genHashKey(Branch p, int k) {
        int [] tab = new int[MAX_LEV];
        tab[0] = -1; // -1 will always come first during sorting, 
        // now the other three passengers
        for (int i=0, j=1; i<MAX_LEV; i++) {
            if (i==k) continue; // we have this one, see above
            tab[j++] = p.dropoff[i];
        }
        Arrays.sort(tab);
        tab[0] = p.dropoff[k];

        String key = "";
        for (int j = 0; j < tab.length; j++) {
            key += tab[j];
            if (j < tab.length-1) key += "-";
        }
        return key;
    }

    public List<Pool> removeDuplicates(List<Pool> list) {
        int inPool = MAX_LEV;
        Pool[] arr = list.toArray(new Pool[0]);

        if (arr == null) {
          return null;
        }
        Arrays.sort(arr);

        // removing duplicates
        int i = 0, j = 0;
        for (i = 0; i < arr.length; i++) {
          if (arr[i].cost == -1) { // this -1 marker is set below
            continue;
          }
          for (j = i + 1; j < arr.length; j++) {
            if (arr[j].cost != -1 // not invalidated; this check is for performance reasons
                    && isFound(arr, i, j)) {
              arr[j].cost = -1; // duplicated; we remove an element with greater costs (list is pre-sorted)
            }
          }
        }
        
        // just collect non-duplicated pool plans
        List<Pool> ret = new ArrayList<>();

        for (i = 0; i < arr.length; i++) {
          if (arr[i].cost != -1) {
            ret.add(arr[i]);
          }
        }
        return ret;
    }

    public boolean isFound(Pool[] arr, int i, int j) {
        for (int x = 0; x < arr[j].stops.length; x++) { 
          for (int y = 0; y < arr[i].stops.length; y++) {
            if (arr[j].stops[x] == arr[i].stops[y]) {
              return true;
            }
          }
        }
        return false;
    }

    public void genDemand() {
        for (int i=0; i<MAX_CUST; i++) {
             from[i] = i%45 == MAX_STAND ? 0 : i%45;
             to[i] = Math.min((i+1)%45, MAX_CUST-1);
            //from[i] = rand.nextInt(MAX_STAND);
            //to[i] = randomTo(from[i], MAX_STAND);
        }
    }

    public int cost(int from, int to) {
        return abs(from - to);
    }

    private int randomTo(int from, int maxStand) {
        int diff = rand.nextInt(MAX_TRIP * 2) - MAX_TRIP;
        if (diff == 0) diff = 1;
        int to = 0;
        if (from + diff > maxStand -1 ) to = from - diff;
        else if (from + diff < 0) to = 0;
        else to = from + diff;
        return to;
    }

    class Branch implements Comparable<Branch> {
        public String key; // used to remove duplicates and search in hashmap
        public int cost;
        public int[] dropoff; // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing 

        Branch (String key, int cost, int[] drops){
            this.key = key;
            this.cost = cost;
            this.dropoff = drops;
        }

        @Override
        public int compareTo(Branch pool) {
          return this.key.compareTo(pool.key);        }
      
        @Override
        public boolean equals(Object pool) {
          if (pool == null || this.getClass() != pool.getClass()) {
            return false;
          }
          return this.key.equals(((Branch) pool).key);
        }
      
        @Override
        public int hashCode() {
            int result = (int) (dropoff[0] ^ (dropoff[0] >>> 32));
            result = 31 * result + cost;
            result = 31 * result + dropoff[1];
            return result;
        }
      
    }
    class Pool implements Comparable<Pool> {
        public int cost;
        public int[] stops; // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing 

        Pool (int cost, int[] stops){
            this.cost = cost;
            this.stops = stops;
        }

        @Override
        public int compareTo(Pool pool) {
          return this.cost - pool.cost;
        }
      
        @Override
        public boolean equals(Object pool) {
          if (pool == null || this.getClass() != pool.getClass()) {
            return false;
          }
          return this.cost == ((Pool) pool).cost;
        }
      
        @Override
        public int hashCode() {
            int result = (int) (stops[0] ^ (stops[0] >>> 32));
            result = 31 * result + cost;
            result = 31 * result + stops[1];
            return result;
        }
    }
    
    private int poolIsValid(List<Pool> pool) {
        
        // first checking if acceptably long
        int failCount = 0;
        for (Pool e : pool) {
            boolean isOk = true;
            int cost = 0;
            int j=0;
            for (; j < e.stops.length/2 -1; j++) {  // pickup
                cost += cost(from[e.stops[j]], from[e.stops[j+1]]);
            }
            cost += cost(from[e.stops[j]], to[e.stops[j+1]]);
            j++;
            for (;j < e.stops.length -1; j++) {
                if (cost > cost(from[e.stops[j]], to[e.stops[j]]) * (100.0+MAX_LOSS)/100.0) {
                    isOk = false;
                    break;
                }
                cost += cost(to[e.stops[j]], to[e.stops[j+1]]);
            }
            if (cost > cost(from[e.stops[j]], to[e.stops[j]]) * (100.0+MAX_LOSS)/100.0) {
                isOk = false;
            }
            if (!isOk) failCount++;
        }

        // if a passenger does not appear in two pools
        Pool[] arr = pool.toArray(new Pool[0]);
        boolean found = false;
        for (int i = 0; i < arr.length && !found; i++) 
            for (int j = i + 1; j < arr.length && !found; j++) 
              if (isFound(arr, i, j)) found = true;
        if (found) failCount = -1;
        return failCount;
    }
}
