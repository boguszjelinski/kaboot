import static java.lang.StrictMath.abs;
import java.util.Random;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.List;

// recursion deep down, n=500, 54s, Count: 348496095
// dyna: Count n=500, count=(25825, 712277, 14161374), 8s
public class DynaPool {
    final int MAX_CUST = 500;
    final int MAX_LEV = 4;
    final int MAX_TRIP = 4;
    final int MAX_STAND = 50;
    final double MAX_LOSS = 1.3;
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

        for (int c = 0; c < MAX_CUST; c++) {
            for(Branch b : node[lev+1]) { // we iterate over product of the stage further in the tree: +1
                if (!isFoundInBranchOrTooLong(c, b)) { // one of two in 'b' is dropped off earlier
                    storeBranch(lev, c, b);
                }
            }
        }
    }

    private void storeBranch(int lev, int c, Branch b) {
        int[] drops = new int[MAX_LEV-lev];
        drops[0] = c;
        for(int j=0; j<MAX_LEV-lev-1; j++) // further stage has one passenger less: -1
            drops[j+1] = b.dropoff[j];
        
        Branch b2 = new Branch(cost(to[c], to[b.dropoff[0]]) + b.cost, drops);
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
                        Branch b = new Branch(cost(to[c], to[d]), drops);
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
        public int cost;
        public int[] dropoff; // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing 

        Branch (int cost, int[] drops){
            this.cost = cost;
            this.dropoff = drops;
        }

        @Override
        public int compareTo(Branch pool) {
          return this.cost - pool.cost;
        }
      
        @Override
        public boolean equals(Object pool) {
          if (pool == null || this.getClass() != pool.getClass()) {
            return false;
          }
          return this.cost == ((Branch) pool).cost;
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
