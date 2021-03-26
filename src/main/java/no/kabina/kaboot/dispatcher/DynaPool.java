import static java.lang.StrictMath.abs;
import java.util.Arrays;
import java.util.Random;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

// recursion deep down, n=500, 54s, Count: 348496095
// dyna: Count n=500, count=(25825, 712277, 14161374), 8s
// no duplicates: Count n=500, count=(25825, 513849, 4467560), 19s

public class DynaPool {
    final int MAX_CUST = 200;
    final int MAX_LEV = 4;
    final int MAX_TRIP = 4;
    final int MAX_STAND = 50;
    final double MAX_LOSS = 1.2;
    int count=0;

    int[] from = new int[MAX_CUST];
    int[] to = new int[MAX_CUST];
    Random rand = new Random(10L);
    List<Branch>[] node = new ArrayList[MAX_LEV-1];
    List<Branch>[] nodeP = new ArrayList[MAX_LEV-1];
    HashMap<String, Branch> map = new HashMap<>();

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        DynaPool p = new DynaPool();
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
        System.out.println("Total time: " + (time3-start)/1000 + "s");
        System.out.println("Size after removing duplicates: " + ret.size());
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
                    cost(to[c], to[d]) < cost(from[d], to[d]) * MAX_LOSS) {
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
                    cost(from[c], from[d]) < cost(from[d], to[d]) * MAX_LOSS) {
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
            if (wait > cost(from[b.dropoff[i]], to[b.dropoff[i]]) * MAX_LOSS ) 
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
            if (wait > cost(from[b.dropoff[i]], to[b.dropoff[i]]) * MAX_LOSS ) 
                return true;
            if (i+1 < b.dropoff.length)
                wait += cost(from[b.dropoff[i]], from[b.dropoff[i+1]]); 
        }
        return false;
    }

    private List<Branch> rmDuplicates(List<Branch> node, int lev, boolean makeHash) {
        Branch[] arr = node.toArray(new Branch[0]);
        if (arr == null || arr.length<2) return null;

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
                int[] copiedArray = Arrays.copyOf(arr[i].dropoff, arr[i].dropoff.length);
                Arrays.sort(copiedArray);
                String key="";
                for (int j = 0; j < copiedArray.length; j++) {
                    key += copiedArray[j];
                    if (j < copiedArray.length-1) key += "-";
                }
                arr[i].key = key;
                if (lev == 0 && makeHash) map.put(key, arr[i]);
                else list.add(arr[i]);
            }
        return list;
    }

    private List<Pool> mergeTrees() {
        List<Pool> ret = new ArrayList<>();

        for (Branch p : nodeP[0]) {
            Branch d = map.get(p.key); // get drop-offs for that key
            if (d==null) continue; // drop-off was not acceptable
            // checking if drop-off still acceptable with that pick-up fase
            int wait = p.cost + cost(p.dropoff[p.dropoff.length-1], d.dropoff[0]);
            int i=0;
            boolean tooLong= false;
            while(true) {
                if (wait > cost(from[d.dropoff[i]], to[d.dropoff[i]]) * MAX_LOSS) {
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
            for (; i<p.dropoff.length+d.dropoff.length; i++) both[i] = d.dropoff[i-p.dropoff.length];
            int cost = p.cost + cost(p.dropoff[p.dropoff.length-1], d.dropoff[0]) + d.cost;
            ret.add(new Pool(cost, both));
        }
        return ret;
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
            from[i] = rand.nextInt(MAX_STAND);
            to[i] = randomTo(from[i], MAX_STAND);
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
}
