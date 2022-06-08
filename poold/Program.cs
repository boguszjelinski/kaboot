using System.Globalization;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

class Program
{
    private const int MAXSTOPS = 6000; // TASK: hardcode
    private const int MAXORDERS = 1000;
    private const int MAXCABS = 1000;
    private const int MAXINPOOL = 4;
    private const int MAXORDID = MAXINPOOL * 2;
    private const int MAXNODE = MAXINPOOL + MAXINPOOL - 1;
    private const int MAXANGLE = 120;

    private const NumberStyles styles = NumberStyles.Float;
    private static CultureInfo culture = new("en-US", false);

    private static int ordersCount;
    private static int cabsCount;

    struct Stop {
        public int id;
        public short bearing;
        public double longitude;
        public double latitude;
    }
    struct Order {
        public int id;
        public int fromStand;
        public int toStand;
        public short maxWait;
        public short maxLoss;
        public short distance;
    };
    struct Cab {
        public int id;
        public int location;
    };

    private static List<Branch>[] node = new List<Branch>[MAXNODE];

    class Branch
    {
        [JsonIgnore]
        public string? key { get; set; } // used to remove duplicates and search in hashmap

        [JsonIgnore]
        public short cost { get; set; }

        [JsonIgnore]
        public byte outs { get; set; } // BYTE, number of OUT nodes, so that we can guarantee enough IN nodes

        [JsonIgnore]
        public short ordNumb { get; set; } // it is in fact ord number *2; length of vectors below - INs & OUTs
        public int[] ids { get; set; } // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing
        public char[] acts { get; set; }

        [JsonIgnore]
        public int[] ordIDsSorted { get; set; }

        [JsonIgnore]
        public char[] ordActionsSorted { get; set; }
        public int cab { get; set; }

        public Branch() {
            ids = new int[MAXORDID]; // we could get rid of it to gain on memory (key stores this too); but we would lose time on parsing
            acts = new char[MAXORDID];
            ordIDsSorted = new int [MAXORDID];
            ordActionsSorted = new char[MAXORDID];
            cost = 0;
            outs = 0;
            ordNumb = 0;
            cab = -1;
        }
    };
    
    private const string stopsFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\db\\stops-Budapest-import.csv";
    private const string ordersFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\orders.csv";
    private const string cabsFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\cabs.csv";
    private const string outFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\pools.csv";
    private const string flagFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\flag.txt";
    private const string exitFileName = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\exit.txt";    

    private static Stop[] stops = new Stop[MAXSTOPS];
    private static Order[] orders = new Order[MAXORDERS];
    private static Cab[] cabs = new Cab[MAXCABS];
    private static short[,] distance = new short[MAXSTOPS, MAXSTOPS];
    private static int stopsNumb = 0;

    private static string docPath = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
    //private static StreamWriter logFile = new StreamWriter(Path.Combine(docPath, "WriteLines.txt"));

    static void Main(string[] args)
    {
        int numbThreads = 10;
        short[] inPool = new short[] { 4, 3, 2 };
        stopsNumb = ReadStops(stopsFileName);
        InitDistance(stopsNumb);

        if (File.Exists(flagFileName)) {
            ordersCount = ReadOrders(ordersFileName);
            File.Delete(ordersFileName);
            cabsCount = ReadCabs(cabsFileName);
            File.Delete(cabsFileName);
            DateTime startTime = DateTime.Now;
            Console.Write("START\n");
            Console.Write("Orders: {0:D}, cabs: {1:D}\n", ordersCount, cabsCount);
            string json = "[";
            for (int i = 0; i < inPool.Length; i++) {
                (bool, string) ret = FindPool(inPool[i], numbThreads, json);
                if (!ret.Item1) break; // no more cabs
                json = ret.Item2;
            }
            File.Delete(flagFileName);
            json = json.Remove(json.Length - 1); // remove last comma
            json += "]";
            File.WriteAllText(outFileName, json);
            Console.WriteLine("Duration" + DateTime.Now.Subtract(startTime));
        }
        return;
    }

    static int ReadStops(string fileName) {
        int i = 0;
        using (var reader = new StreamReader(fileName)) {
            while (!reader.EndOfStream) {
                string? line = reader.ReadLine();
                if (line == null) break;
                if (line.StartsWith("id")) continue; // skip header
                string[] values = line.Split(','); // TASK: check length
                stops[i].id = Int32.Parse(values[0]);
                stops[i].latitude = Double.Parse(values[3], styles, culture);
                stops[i].longitude = Double.Parse(values[4], styles, culture);
                stops[i].bearing = Int16.Parse(values[5]);
                i++;
            }
        }
        return i;
    }
    static int ReadCabs(string fileName) {
        int i = 0;
        using (var reader = new StreamReader(fileName)) {
            while (!reader.EndOfStream) {
                string? line = reader.ReadLine();
                if (line == null) break;
                string[] values = line.Split(',');
                cabs[i].id = Int32.Parse(values[0]);
                cabs[i].location = Int16.Parse(values[1]);
                i++;
            }
        }
        return i;
    }
    static int ReadOrders(string fileName) {
        int i = 0;
        using (var reader = new StreamReader(fileName)) {

            while (!reader.EndOfStream) {
                string? line = reader.ReadLine();
                if (line == null) break;
                string[] values = line.Split(',');
                orders[i].id = Int32.Parse(values[0]);
                orders[i].fromStand = Int32.Parse(values[1]);
                orders[i].toStand = Int32.Parse(values[2]);
                orders[i].maxWait = Int16.Parse(values[3]);
                orders[i].maxLoss = Int16.Parse(values[4]);
                orders[i].distance = Int16.Parse(values[5]);
                i++;
            }
        }
        return i;
    }

    // Pool service
    static (bool, string) FindPool(short inPool, int numbThreads, string json) {
        if (inPool > MAXINPOOL)
            return (true, json);
        for (int i = 0; i < MAXNODE; i++)
            node[i] = new List<Branch>();

        Dive(0, inPool, numbThreads);
        (bool, string) ret = RmFinalDuplicatesAreThereAnyCabs(json, inPool);
        Console.WriteLine("FINAL: inPool: {0:D}, found pools: {1:D}\n", inPool, node[0].Count);
        return ret;
    }

    static void Dive(int lev, short inPool, int numbThreads)
    {
        if (lev > inPool + inPool - 3)
        { // lev >= 2*inPool-2, where -2 are last two levels
            StoreLeaves(lev);
            return; // last two levels are "leaves"
        }
        Dive(lev + 1, inPool, numbThreads);

        int chunk = ordersCount / numbThreads;
        if (numbThreads * chunk < ordersCount) numbThreads++; // last thread will be the reminder of division

        Thread[] tasks = new Thread[numbThreads];
        List<Branch> []ret = new List<Branch>[numbThreads];

        for (int t = 0; t < numbThreads; t++)
        { // TASK: allocated orders might be spread unevenly -> count non-allocated and devide chunks ... evenly
            int _t = t;
            int _chunk = chunk;
            int _lev = lev;
            short _inPool = inPool;
        
            tasks[t] = new Thread(() => {
                            ret[_t] = Iterate(_t, _chunk, _lev, _inPool);
                        });
            tasks[t].Start();
        }
        for (int t = 0; t < numbThreads; t++) {
            tasks[t].Join();
            node[lev].AddRange(ret[t]);
        }

        // removing duplicates which come from lev+1,
        // as for that level it does not matter which order is better in stages towards leaves
        // Comment: it reduces the node size (=faster next nodes) but it takes time, more time than the gain
        //RmDuplicates(lev);
    }

    static void StoreLeaves(int lev) {
        for (int c = 0; c < ordersCount; c++)
          if (orders[c].id != -1)
            for (int d = 0; d < ordersCount; d++)
              if (orders[d].id != -1) {
                // to situations: <1in, 1out>, <1out, 2out>
                if (c == d) {
                    // IN and OUT of the same passenger, we don't check bearing as they are probably distant stops
                    AddBranch(c, d, 'i', 'o', 1, lev);
                }
                else if (distance[orders[c].toStand, orders[d].toStand]
                            < distance[orders[d].fromStand, orders[d].toStand] * (100.0 + orders[d].maxLoss) / 100.0
                        && BearingDiff(stops[orders[c].toStand].bearing, stops[orders[d].toStand].bearing) < MAXANGLE
                ) {
                    // TASK - this calculation above should be replaced by a redundant value in taxi_order - distance * loss
                    AddBranch(c, d, 'o', 'o', 2, lev);
                }
              } 
    }

    static int BearingDiff(int a, int b) {
        int r = (a - b) % 360;
        if (r < -180.0) r += 360;
        else if (r >= 180.0) r -= 360;
        return Math.Abs(r);
    }

    static void AddBranch(int id1, int id2, char dir1, char dir2, byte outs, int lev) {
        Branch br = new();

        if (id1 < id2 || (id1 == id2 && dir1 == 'i'))
        {
            br.key = String.Format("{0:D}{1:G}{2:D}{3:G}", id1, dir1, id2, dir2);
            br.ordIDsSorted[0] = id1;
            br.ordIDsSorted[1] = id2;
            br.ordActionsSorted[0] = dir1;
            br.ordActionsSorted[1] = dir2;
        }
        else if (id1 > id2 || id1 == id2)
        {
            br.key = String.Format("{0:D}{1:G}{2:D}{3:G}", id2, dir2, id1, dir1);
            br.ordIDsSorted[0] = id2;
            br.ordIDsSorted[1] = id1;
            br.ordActionsSorted[0] = dir2;
            br.ordActionsSorted[1] = dir1;
        }
        br.cost = distance[orders[id1].toStand, orders[id2].toStand];
        br.outs = outs;
        br.ids[0] = id1;
        br.ids[1] = id2;
        br.acts[0] = dir1;
        br.acts[1] = dir2;
        br.ordNumb = 2;

        node[lev].Add(br);
    }

    private static List<Branch> Iterate(int t, int chunk, int lev, short inPool) {
        List<Branch> branches = new();
        int size = (t + 1) * chunk;
        if (size > ordersCount) size = ordersCount;
  
        for (int ordId = t * chunk; ordId < size; ordId++) 
          if (orders[ordId].id != -1) // not allocated in previous search (inPool+1)
            foreach (Branch branch in node[lev + 1]) 
              if (branch.cost != -1) {
                 // we iterate over product of the stage further in the tree: +1      
                 // store IfNotFoundDeeperAndNotTooLong
                 StoreBranchIf(branch, branches, lev, ordId, inPool);
              }
        return branches;
    }

    private static void StoreBranchIf(Branch br, List<Branch> branches, int lev, int ordId, short inPool)
    {
        // two situations: c IN and c OUT
        // c IN has to have c OUT in level+1, and c IN cannot exist in level + 1
        // c OUT cannot have c OUT in level +1
        bool inFound = false;
        bool outFound = false;

        for (int i = 0; i < br.ordNumb; i++) {
            if (br.ids[i] == ordId) {
                if (br.acts[i] == 'i')
                    inFound = true;
                else
                    outFound = true;
                // current passenger is in the branch below
            }
        }
        // now checking if anyone in the branch does not lose too much with the pool
        // c IN
        int nextStop = br.acts[0] == 'i'
                        ? orders[br.ids[0]].fromStand : orders[br.ids[0]].toStand;
        if (!inFound
            && outFound
            && !IsTooLong(distance[orders[ordId].fromStand, nextStop], br)
            // TASK? if the next stop is OUT of passenger 'c' - we might allow bigger angle
            && BearingDiff(stops[orders[ordId].fromStand].bearing, stops[nextStop].bearing) < MAXANGLE
            ) 
            StoreBranch(br, branches, 'i', lev, ordId, inPool);

        // c OUT
        if (lev > 0 // the first stop cannot be OUT
            && br.outs < inPool // numb OUT must be numb IN
            && !outFound // there is no such OUT later on
            && !IsTooLong(distance[orders[ordId].toStand, nextStop], br)
            && BearingDiff(stops[orders[ordId].toStand].bearing, stops[nextStop].bearing) < MAXANGLE
            ) 
            StoreBranch(br, branches, 'o', lev, ordId, inPool);
    }
    
    private static void StoreBranch(Branch srcBr, List<Branch> branches, char action, int lev, int ordId, int inPool) {

        Branch br = new();
        br.ordNumb = (short) (inPool + inPool - lev);
        br.ids[0] = ordId;
        br.acts[0] = action;
        br.ordIDsSorted[0] = ordId;
        br.ordActionsSorted[0] = action;

        for (int j = 0; j < br.ordNumb - 1; j++)
        { // further stage has one passenger less: -1
            br.ids[j + 1] = srcBr.ids[j];
            br.acts[j + 1] = srcBr.acts[j];
            br.ordIDsSorted[j + 1] = srcBr.ids[j];
            br.ordActionsSorted[j + 1] = srcBr.acts[j];
        }
        br.key = string.Format("{0:D}{1:G}", ordId, action) + srcBr.key;

        br.cost = (short)(distance[action == 'i' ? orders[ordId].fromStand : orders[ordId].toStand,
                          srcBr.acts[0] == 'i' ? orders[srcBr.ids[0]].fromStand : orders[srcBr.ids[0]].toStand]
                  + srcBr.cost);
        br.outs = action == 'o' ? (byte)(srcBr.outs + 1) : srcBr.outs;

        branches.Add(br);
    }

    private static bool IsTooLong(int wait, Branch br) {
        for (int i = 0; i < br.ordNumb; i++) {
            if (wait > orders[br.ids[i]].distance //distance[orders[srcBr.ordIDs[i]].fromStand][orders[srcBr.ordIDs[i]].toStand] 
                      * (100.0 + orders[br.ids[i]].maxLoss) / 100.0) return true;
            if (br.acts[i] == 'i' && wait > orders[br.ids[i]].maxWait) return true;
            if (i + 1 < br.ordNumb)
                wait += distance[br.acts[i] == 'i' ? orders[br.ids[i]].fromStand : orders[br.ids[i]].toStand,
                                 br.acts[i + 1] == 'i' ? orders[br.ids[i + 1]].fromStand : orders[br.ids[i + 1]].toStand];
        }
        return false;
    }

    // removing duplicates from the previous stage
    // TASK: there might be more duplicates than one at lev==1 or 0 !!!!!!!!!!!!!
    private static void RmDuplicates(int lev) {
        if (lev==0 || node[lev].Count < 2) return;

        node[lev] = node[lev].OrderBy(b => b.key).ToList(); // sort by key, not cost

        Branch[] arr = node[lev].ToArray();

        for (int i = 0; i < arr.Length - 1; i++) { // -1 as there is i+1 below
            if (arr[i].cost == -1)
            { // this -1 marker is set below
                continue;
            }
            if (arr[i].key == arr[i + 1].key) { // the same key, which costs less?
                if (arr[i].cost > arr[i + 1].cost)
                    arr[i].cost = -1; // to be deleted
                else {
                    arr[i + 1].cost = -1;
                    i++; //just skip the 'if' in the next iteration
                }
            }
        }
        List<Branch> list = new();

        // recreating the key for the next level - the first element was unsorted, will go to the correct place now
        if (lev > 0) // we don't need the keys any more after we have generated LEV==0
        {
            int offset, c;
            short len;
            char action;
            
            foreach (Branch br in arr) 
              if (br.cost != -1) {
                len = br.ordNumb; // that may be CONST for a level?
                c = br.ordIDsSorted[0];
                action = br.ordActionsSorted[0];
                // TODO: dumb sorting, maybe try bisection like bisect() in Python? or just qsort?
                for (int j = 1; j < len; j++) {
                    if (br.ordIDsSorted[j] == c) {
                        offset = 0;
                        if (br.ordActionsSorted[j] == 'o')
                        { // that means that branch.ordActionsSorted[0] == 'i'
                            offset = 1; // you should put 'c' (which is IN) in position j - 1
                        }
                        for (int k = 0; k < j - offset; k++) {
                            br.ordIDsSorted[k] = br.ordIDsSorted[k + 1];
                            br.ordActionsSorted[k] = br.ordActionsSorted[k + 1];
                        }
                        br.ordIDsSorted[j - offset] = c;
                        br.ordActionsSorted[j - offset] = action;
                        break;
                    }
                    else if (br.ordIDsSorted[j] > c) {
                        for (int k = 0; k < j - 1; k++) {
                            br.ordIDsSorted[k] = br.ordIDsSorted[k + 1];
                            br.ordActionsSorted[k] = br.ordActionsSorted[k + 1];
                        }
                        br.ordIDsSorted[j - 1] = c;
                        br.ordActionsSorted[j - 1] = action;
                        break;
                    }
                }
                // regen key based on a sorted list
                br.key = GenKey(br);

                list.Add(br);
              }
        }
    }

    private static string GenKey(Branch b) {
        StringBuilder buf = new();
        for (int i = 0; i < b.ordIDsSorted.Length; i++) {
            buf.Append(b.ordIDsSorted[i]).Append(b.ordActionsSorted[i]);
        }
        return buf.ToString();
    }

    // returnes false if there is no free cab any longer
    // and a string that should be written to a file as response
    private static (bool, string) RmFinalDuplicatesAreThereAnyCabs(string json, int inPool) {
        int cabIdx = -1;
        int from;
        int distCab;
        String jsonEnt = null;
        if (node[0].Count < 1) return (true, json); // we have not allocated any cab, smaller pools will

        node[0] = node[0].OrderBy(b => b.cost).ToList();
        Branch[] arr = node[0].ToArray();

        for (int i = 0; i < arr.Length; i++) {
            if (arr[i].cost == -1) continue; // not dropped earlier or (!) later below
            from = orders[arr[i].ids[0]].fromStand;
            cabIdx = FindNearestCab(from);
            if (cabIdx == -1)
            { // no more cabs
                Console.WriteLine("NO CAB");
                return (false, json);
            }
            distCab = distance[cabs[cabIdx].location, from];
            if (distCab == 0 // constraints inside pool are checked while "diving" in recursion
                    || AreConstraintsMet(arr[i], distCab)) {
                arr[i].cab = cabIdx; // not supply[cabIdx].id as it is faster to refereel->nce it in Boot (than finding IDs)
                cabs[cabIdx].location = -1; // cab allocated
                jsonEnt = JsonSerializer.Serialize(arr[i]);
                jsonEnt = jsonEnt.Replace("\"cab", "\"len\":" + inPool +",\"cab");
                json += jsonEnt + ",";
                // remove any further duplicates
                for (int j = i + 1; j < arr.Length; j++)
                    if (arr[j].cost != -1 && IsFound(arr[i], arr[j], inPool + inPool - 1)) // -1 as last action is always OUT
                        arr[j].cost = -1; // duplicated; we remove an element with greater costs (list is pre-sorted)
            }
            else arr[i].cost = -1; // constraints not met, mark as unusable
        }
        return(true, json);
    }

    static bool AreConstraintsMet(Branch el, int distCab) {
        // TASK: distances in pool should be stored to speed-up this check
        int dist = 0;
        Order o, o2;
        for (int i = 0; i < el.ordNumb; i++) {
            o = orders[el.ids[i]];
            if (el.acts[i] == 'i' && dist + distCab > o.maxWait) {
                return false;
            }
            if (el.acts[i] == 'o' && dist > (1 + o.maxLoss / 100.0) * o.distance)
            { // TASK: remove this calcul
                return false;
            }
            o2 = orders[el.ids[i + 1]];
            if (i < el.ordNumb - 1) {
                dist += distance[el.acts[i] == 'i' ? o.fromStand : o.toStand,
                                 el.acts[i + 1] == 'i' ? o2.fromStand : o2.toStand];
            }
        }
        return true;
    }

    static string SaveInJson(string json, Branch br) {
        cabs[br.cab].location = -1; // allocated
        json += "{" + '\u0022' + "cab" + '\u0022' + ":"+ br.cab + ","
                + '\u0022' + "len" + '\u0022' + ":" + (br.ordNumb / 2)
                + '\u0022' + "ids" + '\u0022' + ":[";
        for (int i = 0; i < br.ordNumb; i++) {
            json += string.Format("{0:D},", br.ids[i]);
            orders[br.ids[i]].id = -1; // allocated
        }
        json = json.Remove(json.Length - 1); // remove last comma
        json += "]," + '\u0022' + "acts" + '\u0022' + ":[";
        for (int i = 0; i < br.ordNumb; i++) 
            json += + '\u0022' + br.acts[i] + '\u0022' + ",";
        json = json.Remove(json.Length - 1); // remove last comma
        return json + "]},";
    }

    static bool IsFound(Branch br1, Branch br2, int size) {
        for (int x = 0; x < size; x++)
            if (br1.acts[x] == 'i')
                for (int y = 0; y < size; y++)
                    if (br2.acts[y] == 'i' && br2.ids[y] == br1.ids[x])
                        return true;
        return false;
    }

    static int FindNearestCab(int from) {
        int dist = 10000; // big enough
        int nearest = -1;
        for (int i = 0; i < cabsCount; i++) {
            if (cabs[i].location == -1) // allocated earlier to a pool
                continue;
            if (distance[cabs[i].location, from] < dist) {
                dist = distance[cabs[i].location, from];
                nearest = i;
            }
        }
        return nearest;
    }

    // Distance service
    private const double M_PI = 3.14159265358979323846;
    private const double m_pi_180 = M_PI / 180.0;
    private const double rev_m_pi_180 = 180.0 / M_PI;

    static double Deg2rad(double deg) { return (deg * m_pi_180); }
    static double Rad2deg(double rad) { return (rad * rev_m_pi_180); }

    // https://dzone.com/articles/distance-calculation-using-3
    static double Dist(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.Sin(Deg2rad(lat1)) * Math.Sin(Deg2rad(lat2)) + Math.Cos(Deg2rad(lat1))
                      * Math.Cos(Deg2rad(lat2)) * Math.Cos(Deg2rad(theta));
        dist = Math.Acos(dist);
        dist = Rad2deg(dist);
        dist *= 60 * 1.1515;
        dist *= 1.609344;
        return dist;
    }

    static void InitDistance(int size) {
        for (int i = 0; i < size; i++) {
            distance[i,i] = 0;
            for (int j = i + 1; j < size; j++) {
                double d = Dist(stops[i].latitude, stops[i].longitude, stops[j].latitude, stops[j].longitude);
                distance[stops[i].id, stops[j].id] = (short) d; // TASK: we might need a better precision - meters/seconds
                distance[stops[j].id, stops[i].id] = distance[stops[i].id, stops[j].id];
            }
        }
    }
}