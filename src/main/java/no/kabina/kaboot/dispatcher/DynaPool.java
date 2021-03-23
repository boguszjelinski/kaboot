import static java.lang.StrictMath.abs;
import java.util.Random;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.List;

// recursion deep down, n=500, 54s, Count: 348496095

public class DynaPool {
    final int MAX_CUST = 500;
    final int MAX_LEV = 4;
    final int MAX_TRIP = 4;
    final int MAX_STAND = 50;
    final double MAX_LOSS = 1.3;
    int count=0;

    int[] dropoff = new int[MAX_LEV];
    int[] from = new int[MAX_CUST];
    int[] to = new int[MAX_CUST];
    Random rand = new Random(10L);
    //List<Branch> myMap = new ArrayList<>();
    Branch[][] myMap = new Branch[MAX_LEV-1][];
    int mapCount[] = new int[MAX_LEV-1];
    HashMap<String, Branch> myMap2 = new HashMap<String, Branch>();

    public static void main(String[] args) {
        DynaPool p = new DynaPool();
        p.genDemand();
        p.storeLeaves();
        p.dive(0);
        p.raport();
        
    }

    private void raport() {
        System.out.println("Count: " + myCount[MAX_LEV-2]);
        System.out.println("Count2: " + myCount[MAX_LEV-3]);
        //System.out.println("Number of elements: " + myMap2.size());
    }

    public void dive(int lev) {
        if (lev == MAX_LEV-2) { 
            // leaves have been computed
            // frankly - we will never land here as there is this check below
        }
        else {
            if (lev < MAX_LEV-3) { 
                dive(lev + 1);
            } 
            if (lev == MAX_LEV-3) { 
                for (int c = 0; c < MAX_CUST; c++) {
                    // dropoff[lev] = c;
                    // int wait = howLong(lev);
                    // if ( // 'c' is dropped off earlier
                    //     wait > cost(from[c], to[c]) * MAX_LOSS
                    //     || isFound(lev, c) //wait > cost(from[c], to[c]) * MAX_LOSS // 'c' loses too much
                    // ) {
                    //     continue;
                    // }
                    // iterate thru leaves
                    for(int i=0; i<mapCount[MAX_LEV-2]; i++) { // Entry<String, Branch> entry: myMap.entrySet()
                        Branch b = myMap[i];
                        //System.out.println(entry.getKey());
                        //Branch b = entry.getValue();
                        // checking if that leaf does not contain previous drop-offs
                        // again checking if fine for leaves

                        if (isFoundInBranchOrTooLong(c, b) // one of two in 'b' is dropped off earlier
                            //|| tooLongForBranch(c, b) // 'c' loses too much
                        ) {
                            continue;
                        }
                        // storing the branch as viable
                        int[] drops = new int[MAX_LEV-1];
                        drops[0] = c;
                        drops[1] = b.dropoff[0];
                        drops[2] = b.dropoff[1];
                        
                        Branch b2 = new Branch(cost(to[c], to[b.dropoff[0]]) + b.cost, drops);
                        //myMap[MAX_LEV-3][mapCount[MAX_LEV-3]++] = b2;
                    }
                }
            }
        }
    }

    private void storeLeaves() {
        myMap[MAX_LEV-2] = new Branch[50000];
        myMap[MAX_LEV-3] = new Branch[1000000];
        myCount[MAX_LEV-2] = 0;
        myCount[MAX_LEV-3] = 0;
        myCount[MAX_LEV-4] = 0;
        for (int c = 0; c < MAX_CUST; c++) 
            for (int d = 0; d < MAX_CUST; d++) 
                if (c != d && 
                    cost(to[c], to[d]) < cost(from[d], to[d]) * MAX_LOSS) {
                        int[] drops = new int[2];
                        drops[0] = c;
                        drops[1] = d;
                        Branch b = new Branch(cost(to[c], to[d]), drops);
                        String key = c + "-" + d;
                        myMap[MAX_LEV-2][mapCount[MAX_LEV-2]++] = b;
        }
        System.out.println("Number of leaves: " + mapCount);
    }

    public boolean isFound(int level, int c) {
        for (int l = 0; l < level; l++) {
          if (dropoff[l] == c) {
            return true;
          }
        }
        return false;
    }

    public boolean isFoundInBranchOrTooLong(int c, Branch b) {
        for (i=0; i<b.dropoff.length; i++)
            if (b.dropoff[i] == c) return true; // current passenger is in the branch below -> reject that combination
        // now checking if anyone in the branch does not lose too much with the pool
        int wait = cost(to[c], to[b.dropoff[0]]);
        
        for (i=0; i<b.dropoff.length; i++) {
            if (wait > cost(from[b.dropoff[i]], to[b.dropoff[i]]) * MAX_LOSS ) 
                return true;
            if (i+1 < b.dropoff.length)
                wait += cost(to[b.dropoff[i]], to[b.dropoff[i+1]]); 
        }
        return false;
    }

    public int howLong(int lev) {
        // even the drop-off way is longer than acceptable 
        // somwhere whe have to check pick-up & drop-off
        int sum = 0;
        for (int l = 0; l < lev-1; l++) {
           sum += cost(to[dropoff[l]], to[dropoff[l+1]]);
        }
        return sum; 
    }

    public boolean tooLongForBranch(int lev, int prevWait, Branch b) {
        // even the drop-off way is longer than acceptable 
        // somwhere whe have to check pick-up & drop-off
        int c = b.dropoff[0];
        int d = b.dropoff[1];
        if (prevWait + cost(to[dropoff[lev]], to[c]) 
                > cost(from[c], to[c]) * MAX_LOSS 
            ||
            prevWait + cost(to[dropoff[lev]], to[c]) + b.cost
                > cost(from[d], to[d]) * MAX_LOSS 
                ) {
                    return true;
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

    class Branch {
        public int cost;
        public int[] dropoff; // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing 

        Branch (int cost, int[] drops){
            this.cost = cost;
            this.dropoff = drops;
        }
        
    }
}
