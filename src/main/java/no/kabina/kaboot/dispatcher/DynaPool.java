import static java.lang.StrictMath.abs;
import java.util.Arrays;
import java.util.Random;
import java.util.HashMap;
//import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.List;

// recursion deep down, n=500, 54s, Count: 348496095
// dyna: Count n=500, count=(25825, 712277, 14161374), 8s
// no duplicates: Count n=500, count=(25825, 513849, 4467560), 19s

public class DynaPool {
    final int MAX_CUST = 600;
    final int MAX_LEV = 4;
    final int MAX_TRIP = 4;
    final int MAX_STAND = 50;
    final double MAX_LOSS = 1.1;
    int count=0;

    int[] from = new int[MAX_CUST];
    int[] to = new int[MAX_CUST];
    Random rand = new Random(10L);
    List<Branch>[] node = /*(ArrayList<Branch>[])*/ new ArrayList[MAX_LEV-1];

    public static void main(String[] args) {
        DynaPool p = new DynaPool();
        p.initMem();
        p.genDemand();
        p.storeLeaves();
        p.dive(0);
        p.raport();
        //p.removeDuplicates();
    }

    private void raport() {
        System.out.println("Count leaves: " + node[MAX_LEV-2].size());
        System.out.println("Count between: " + node[MAX_LEV-3].size());
        System.out.println("Count root: " + node[MAX_LEV-4].size());
        //System.out.println("Number of elements: " + myMap2.size());
    }

    private void initMem() {
        for (int i=0; i<MAX_LEV-1; i++) 
            node[i] = new ArrayList<>(); 
    }

    public void dive(int lev) {
        if (lev > MAX_LEV-3) return; // last two levels are "leaves"

        dive(lev + 1);
        long time1 = System.currentTimeMillis();
        
        for (int c = 0; c < MAX_CUST; c++) {
            for (Branch b : node[lev+1]) { // we iterate over product of the stage further in the tree: +1
                if (!isFoundInBranchOrTooLong(c, b)) { // one of two in 'b' is dropped off earlier
                    storeBranch(lev, c, b);
                }
            }
        }
        // removing duplicates which come from lev+1
        long time2 = System.currentTimeMillis();
        System.out.println("Finding branches: " + (time2-time1)/1000);
        rmDuplicates(lev);
        long time3 = System.currentTimeMillis();
        System.out.println("Rm duplicates: " + (time3-time2)/1000);
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

    private void rmDuplicates(int lev) {
        Branch[] arr = node[lev].toArray(new Branch[0]);
        if (arr == null || arr.length<2) return;

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
                    key += copiedArray[j] + j<copiedArray.length-1 ? "-" : "";
                }
                arr[i].key = key;
                list.add(arr[i]);
            }
        
        node[lev] = list;
    }

   /* private boolean isSame(Branch a, Branch b) {
        for (int i=0; i < a.dropoff.length; i++) 
          if (a.dropoff[i] != b.dropoff[i])
            return false;
        return true;
    }
*/
    public HashMap<String, Branch> removeDuplicates() {
        int inPool = MAX_LEV;
        long time1 = System.currentTimeMillis();
        Branch[] arr = node[0].toArray(new Branch[0]);

        if (arr == null) {
          return null;
        }
        Arrays.sort(arr);
        long time2 = System.currentTimeMillis();
        System.out.println("Sorting took: " + (time2-time1)/1000);
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
        long time3 = System.currentTimeMillis();
        System.out.println("Rm duplicates took: " + (time3-time2)/1000);
        // just collect non-duplicated pool plans
        HashMap<String, Branch> ret = new HashMap<>();
        String key= null;
        for (i = 0; i < arr.length; i++) {
          if (arr[i].cost != -1) {
            Arrays.sort(arr[i].dropoff);
            key="";
            //for (j=0; j<arr[i].dropoff.length; j++)
            //    key += arr[i].dropoff[j] + "-";
            key = arr[i].dropoff[0] + "-" + arr[i].dropoff[1] + "-" 
                    + arr[i].dropoff[2] + "-" + arr[i].dropoff[3];
            ret.put(key, arr[i]);
          }
        }
        long time4 = System.currentTimeMillis();
        System.out.println("Creating hash: " + (time4-time3)/1000);
        return ret;
    }

    public boolean isFound(Branch[] arr, int i, int j) {
        for (int x = 0; x < arr[j].dropoff.length; x++) { 
          for (int y = 0; y < arr[i].dropoff.length; y++) {
            if (arr[j].dropoff[x] == arr[i].dropoff[y]) {
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
}
