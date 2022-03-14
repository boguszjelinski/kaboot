package no.kabina.kaboot.dispatcher;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.stops.Stop;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import static java.lang.Math.abs;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class PoolUtilTests {

    final int MAX_LOSS = 50;
    private TaxiOrder[] orders = genDemand(75, MAX_LOSS);
    private int [][] distances;
    private int [] bearing;
    private final int numbOfStands = 50;
    Random rand = new Random(10L);
    final int MAX_TRIP = 4;

    public PoolUtilTests() {
        distances = PoolUtil.setCosts(numbOfStands);
        bearing = new int[numbOfStands];
    }
    /*@Before
    public void before() {
    }
    */

    @Test
    public void testPool() {
        PoolUtil util = new PoolUtil(numbOfStands);
        PoolElement[] pool = util.findPool(orders, 3);
        assertThat(pool.length).isSameAs(20);
    }

    @Test
    public void testPool4() {
        PoolUtil util = new PoolUtil(numbOfStands);
        PoolElement[] pool = util.findPool(orders, 4);
        assertThat(pool.length).isSameAs(10);
        assertThat(poolIsValid(pool)).isSameAs(0);
    }

    @Test
    public void testDynaPoolV2_3() {
        DynaPool2 util = new DynaPool2(distances, bearing, 100); // 100 max angle
        PoolElement[] pool = util.findPool(genDemand(250, MAX_LOSS), 3);
        assertThat(pool.length).isSameAs(10); // TASK: one missing
        assertThat(poolIsValid(pool)).isSameAs(0);
    }

    @Test
    public void testDynaPoolV2_4() {
        DynaPool2 util = new DynaPool2(distances, bearing, 100); // 100 max angle
        PoolElement[] pool = util.findPool(genDemand(100, MAX_LOSS), 4);
        assertThat(pool.length).isSameAs(36); // TASK: one missing
        assertThat(poolIsValid(pool)).isSameAs(0);
    }

    @Test
    public void testBearingDiff() {
        assertThat(DynaPool2.bearingDiff(10, 21)).isEqualTo(11);
        assertThat(DynaPool2.bearingDiff(32, 20)).isEqualTo(12);
        assertThat(DynaPool2.bearingDiff(-5, -18)).isEqualTo(13);
        assertThat(DynaPool2.bearingDiff(-5, 9)).isEqualTo(14);
        assertThat(DynaPool2.bearingDiff(-175, 170)).isEqualTo(15);
        assertThat(DynaPool2.bearingDiff(-175, 5)).isEqualTo(180);
    }

    @Test
    public void testExternPool4() {
        ExternPool util = new ExternPool(); //"poold", "orders.csv","cabs.csv", "pools.csv", 8);
        PoolElement[] pool = null;

        pool = util.findPool(genDemand(200, MAX_LOSS), genCabs(200), false);
        assertThat(pool.length).isSameAs(62); // 14 when run from maven, not from Idea (which is what, not maven?)
        assertThat(poolIsValid(pool)).isSameAs(0);
    }

    Cab[] genCabs(int count) {
        List<Cab> list = new ArrayList<>();
        for (int i = 0; i<count; i++) {
            Cab c = new Cab();
            c.setId(Long.valueOf(i));
            c.setLocation(i % 50);
            list.add(c);
        }
        return list.toArray(new Cab[0]);
    }

    @Test
    public void testPoolElement() {
        PoolElement el = new PoolElement(null, 0, 0);
        el.setCust(null);
        el.setNumbOfCust(1);
        assertThat(el.getCust()).isNull();
        assertThat(el.getNumbOfCust()).isSameAs(1);
        assertThat(el.equals(null)).isNotEqualTo(true);
        assertThat(el.equals(new PoolElement(null, 0, 1))).isNotEqualTo(true);
        assertThat(el.hashCode()).isNotSameAs(-1);

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

    private int poolIsValid(PoolElement[] pool) {
        boolean valid = true;
        // first checking if acceptably long
        int failCount = 0;
        for (PoolElement e : pool) {
            boolean isOk = true;
            for (int c = 0; c < e.getNumbOfCust() && isOk; c++) {
                int cost = 0;
                int i = c;
                for (; i < e.getNumbOfCust()-1; i++) {  // pickup
                    cost += getDistance(e.getCust()[i].fromStand, e.getCust()[i + 1].fromStand);
                }
                cost += getDistance(e.getCust()[i].fromStand,
                        e.getCust()[i + 1].toStand);
                for (i++; i < 2 * e.getNumbOfCust() -1 && e.getCust()[c] != e.getCust()[i]; i++) {
                    cost += getDistance(e.getCust()[i].toStand, e.getCust()[i + 1].toStand);
                }
                if (cost > getDistance(e.getCust()[c].fromStand, e.getCust()[c].toStand)
                        * (100.0+MAX_LOSS)/100.0) {
                    isOk = false;
                }
            }
            if (!isOk) failCount++;
        }
        // check also if a passenger does not appear in two pools

        boolean isOk = true;
        for (int i = 0; i < pool.length && isOk; i++) {
            for (int j = i + 1; j < pool.length && isOk; j++) {
                if (PoolUtil.isFound(pool, i, j, pool[i].getNumbOfCust())) { // maybe not 100% kosher to use tested code in tests ...
                    isOk = false;
                }
            }
        }
        if (!isOk) failCount = -1;
        return failCount;
    }

    private int getDistance(int i, int j) {
        return abs(i-j);
    }

    public static TaxiOrder[] genDemand(int size, int maxLoss) {
        TaxiOrder[] orders = new TaxiOrder[size];
        for (int i = 0; i < orders.length; i++) {
            //int from = rand.nextInt(numbOfStands);
            orders[i] = new TaxiOrder(randData[i][0], // from  // i%45 == numbOfStands -1 ? 0 : i%45
                    randData[i][1], // randomTo(from, numbOfStands) // Math.min((i+1)%45
                    10,
                    maxLoss,
                    true,
                    TaxiOrder.OrderStatus.RECEIVED,
                    null);
            orders[i].setId((long)i);
        }
        return orders;
    }

    private static int [][] randData = {
        {13,12},{43,42},{46,47},{47,45},{31,29},{23,24},{41,44},{45,44},{36,34},{23,19},{43,46},{45,41},{35,36},{24,21},
        {8,5},{48,49},{22,25},{17,14},{10,11},{5,4},{39,37},{42,43},{18,15},{48,46},{41,38},{5,6},{21,23},{31,32},
        {8,11},{8,9},{10,11},{36,37},{36,38},{40,36},{49,48},{22,21},{18,16},{1,0},{13,9},{29,31},{33,36},{8,7},
        {38,34},{29,25},{48,49},{37,40},{7,10},{36,34},{23,19},{32,29},{13,16},{13,11},{21,24},{0,0},{31,27},{3,4},
        {14,10},{9,8},{27,24},{6,9},{38,40},{33,35},{19,20},{11,13},{44,45},{24,27},{6,5},{11,7},{4,2},{25,23},
        {45,43},{14,15},{44,41},{4,3},{18,15},{34,31},{20,21},{23,26},{8,7},{0,0},{12,9},{48,49},{24,25},{37,39},
        {31,32},{24,27},{9,6},{3,0},{24,20},{16,13},{10,9},{15,12},{20,19},{25,27},{3,4},{44,45},{5,1},{21,17},
        {30,26},{27,28},{4,7},{0,0},{23,25},{46,48},{46,47},{42,43},{1,0},{15,16},{9,8},{18,16},{3,0},{30,28},
        {43,45},{8,10},{3,5},{46,49},{47,44},{1,2},{18,21},{14,15},{14,16},{46,47},{29,30},{38,36},{4,0},{20,23},
        {37,40},{33,30},{23,20},{23,24},{2,0},{25,27},{36,35},{14,13},{31,32},{45,46},{45,46},{9,6},{18,16},{17,15},
        {43,40},{12,11},{21,23},{10,8},{35,37},{49,45},{3,2},{47,44},{15,11},{20,16},{36,35},{32,34},{46,47},{10,12},
        {48,45},{27,24},{36,35},{24,23},{41,42},{8,11},{12,8},{11,7},{44,41},{12,13},{5,6},{11,12},{49,45},{36,38},
        {15,11},{7,3},{29,25},{21,22},{6,5},{14,16},{34,31},{19,18},{28,29},{17,18},{39,40},{45,48},{22,23},{40,41},
        {20,18},{6,7},{10,11},{24,25},{29,31},{46,47},{15,12},{6,4},{33,29},{36,37},{15,17},{37,39},{1,2},{28,31},
        {22,23},{2,0},{19,22},{27,25},{8,6},{9,10},{36,35},{27,28},{6,5},{20,18},{20,19},{35,31},{44,45},{16,14},
        {0,3},{26,27},{41,38},{15,14},{24,22},{30,31},{27,24},{20,21},{10,12},{30,31},{49,48},{12,8},{45,44},{33,31},
        {14,10},{13,12},{6,7},{8,5},{48,49},{43,45},{10,9},{12,11},{23,25},{24,25},{46,48},{32,35},{36,37},{12,9},
        {45,41},{24,26},{9,10},{44,41},{29,30},{32,30},{16,13},{38,39},{26,28},{42,40},{27,28},{22,19},{36,37},{45,46},
        {0,1},{18,17},{23,24},{1,4},{13,14},{24,20},{10,7},{42,41},{7,5},{44,45},{20,17},{19,17},{36,38},{40,37},
        {31,33},{35,31},{21,20},{34,32},{2,3},{8,6},{37,33},{22,18},{21,22},{25,28},{38,35},{31,32},{24,25},{48,46},
        {46,49},{41,37},{10,9},{27,23},{2,1},{20,23},{12,10},{19,20},{21,22},{36,38},{44,40},{29,26},{49,48},{17,13},
        {20,22},{44,46},{49,46},{14,10},{11,12},{33,32},{46,45},{30,32},{15,16},{37,33},{28,24},{3,1},{16,19},{44,45},
        {10,11},{25,27},{2,3},{38,35},{16,18},{25,24},{47,48},{9,6},{39,40},{22,19},{20,21},{48,45},{22,24},{41,40},
        {24,20},{41,38},{30,31},{46,47},{12,10},{21,19},{43,44},{43,44},{36,32},{18,17},{41,43},{44,40},{10,11},{28,31},
        {35,32},{23,24},{33,35},{29,31},{42,38},{22,19},{31,27},{33,32},{35,36},{30,26},{40,36},{41,37},{34,37},{44,45},
        {37,35},{0,2},{28,29},{41,40},{22,23},{11,10},{10,9},{26,27},{11,14},{33,34},{5,4},{20,23},{45,42},{33,34},
        {23,21},{23,24},{30,31},{3,4},{26,22},{42,45},{36,32},{17,18},{46,42},{42,44},{17,18},{35,34},{47,49},{39,42},
        {3,0},{41,38},{1,0},{37,35},{32,29},{16,19},{13,12},{8,9},{8,9},{28,31},{5,6},{5,6},{36,33},{7,5},
        {1,4},{29,31},{22,21},{0,1},{10,11},{1,0},{41,40},{24,26},
    };
}
