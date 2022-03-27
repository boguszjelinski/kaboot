/*
 * Copyright 2020 Bogusz Jelinski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.kabina.kaboot.dispatcher;

import java.io.File;
import java.nio.file.Files;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LcmUtil {

  @Autowired
  private DistanceService distanceService;

  private static final Logger logger = LoggerFactory.getLogger(LcmUtil.class);

  public static final int SCHEDULING_DURATION = 2; // scheduler runs once a minute
  // + it takes one minute to compute + 1min to distribute the information
  public static final int BIG_COST = 250000;

  private LcmUtil() {} // hiding public
  
  /** Low Cost Method aka "greedy" - looks for lowest values in the matrix
  *
  * @param cost cost
  */
  public static LcmOutput lcm(int[][] cost, int howMany) { // 0 = no solver now
    if (howMany < 1) { // we would like to find at least one
      logger.warn("LCM asked to do nothing");
      return null;
    }
    int lcmMinVal = BIG_COST;
    int[][] costLcm = Arrays.stream(cost).map(int[]::clone).toArray(int[][]::new); // clone, not to change the parameter
    List<LcmPair> pairs = new ArrayList<>();
    for (int i = 0; i < howMany; i++) { // we need to repeat the search (cut off rows/columns) 'hoeMany' times
      lcmMinVal = BIG_COST;
      int smin = -1;
      int dmin = -1;
      // now find the minimal element in the whole matrix
      for (int s = 0; s < cost.length; s++) {
        for (int d = 0; d < cost.length; d++) {
          if (costLcm[s][d] < lcmMinVal) {
            lcmMinVal = costLcm[s][d];
            smin = s;
            dmin = d;
          }
        }
      }
      if (lcmMinVal == BIG_COST) {
        logger.info("LCM minimal cost is BIG_COST - no more interesting stuff here");
        break;
      }
      // binding cab to the customer order
      pairs.add(new LcmPair(smin, dmin));

      // removing the column from further search by assigning a big cost
      removeColsAndRows(costLcm, smin, dmin);
    }
    return new LcmOutput(pairs, lcmMinVal); // lcmMinVal==BIG_COST indicating that solver is of no use
  }

  /**
   * mark columns and rows as 'found', variable passed by reference !
   * @param costLcm matrix
   * @param smin cab index
   * @param dmin order index
   */
  public static void removeColsAndRows(int[][] costLcm, int smin, int dmin) {
    for (int s = 0; s < costLcm.length; s++) {
      costLcm[s][dmin] = BIG_COST;
    }
    // the same with the row
    for (int d = 0; d < costLcm.length; d++) {
      costLcm[smin][d] = BIG_COST;
    }
  }

  /**
   * create a cost matrix for LCM and solver
   * @param tmpDemand requests from customers
   * @param tmpSupply cabs available
   * @return matrix
   */
  public int[][] calculateCost(String inputFile, String outputFile, TaxiOrder[] tmpDemand, Cab[] tmpSupply) {
    int c;
    int d;
    int numbSupply = tmpSupply.length;
    int numbDemand = tmpDemand.length;
    int n = Math.max(numbSupply, numbDemand); // checking max size for unbalanced scenarios
    if (n == 0) {
      return new int[0][0];
    }
    int[][] cost = new int[n][n];
    // resetting cost table - all big
    for (c = 0; c < n; c++) {
      for (d = 0; d < n; d++) {
        cost[c][d] = BIG_COST;
      }
    }
    for (c = 0; c < numbSupply; c++) {
      for (d = 0; d < numbDemand; d++) {
        // check that cabs and customers are in range is done in getRidOfDistantCabs & Customers
        cost[c][d] = distanceService.getDistances()[tmpSupply[c].getLocation()][tmpDemand[d].fromStand];
      }
    }
    writeGlpkProg(inputFile, outputFile, n, cost);
    return cost;
  }

  private static void writeGlpkProg(String input, String output, int n, int[][] cost) {
    String body = "set I;\n"
            + "set J;\n"
            + "\n"
            + "param filename, symbolic := \"" + output + "\";\n"
            + "param c{i in I, j in J};\n"
            + "\n"
            + "var x{i in I, j in J} >= 0;\n"
            + "minimize cost: sum{i in I, j in J} c[i,j] * x[i,j];\n"
            + "\n"
            + "s.t. supply{i in I}: sum{j in J} x[i,j] = 1;\n"
            + "s.t. demand{j in J}: sum{i in I} x[i,j] = 1;\n"
            + "\n"
            + "solve;\n"
            + "\n"
            + "for {j in J} {\n"
            + "    for {i in I} {\n"
            + "        printf \"%d\\n\", x[i,j] >> filename;\n"
            + "    }\n"
            + "}\n"
            + "\n"
            + "data;\nset I := ";
    //+ "set I := 1 2 3 4;\n"
    //+ "set J := 1 2 3 4;\n"
    //+ "\n"
    //+ "param c :     1 2 3 4 :=\n"
    //+ "           1  5 1 9 100\n"
    //+ "           2  5 1 9 100 \n"
    //+ "           3  0 3 5 100\n"
    //+ "           4  5 8 0 100;\n"
    //+ "end;\n"


    try {
      Files.delete(Paths.get(output));
    } catch (IOException e) {
      e.printStackTrace();
    }

    try (FileWriter fr = new FileWriter(new File(input))) {
      fr.write(body);
      for (int c = 0; c < n; c++) {
        fr.write((c + 1) + " ");
      }
      fr.write(";\nset J := ");
      for (int c = 0; c < n; c++) {
        fr.write((c + 1) + " ");
      }
      fr.write(";\nparam c : ");
      for (int c = 0; c < n; c++) {
        fr.write((c + 1) + " ");
      }
      fr.write(":=\n");
      for (int c = 0; c < n; c++) {
        fr.write((c + 1) + " ");
        for (int d = 0; d < n; d++) {
          fr.write(cost[d][c] + " ");
        }
        if (c == n - 1) {
          fr.write(";\nend;\n");
        } else {
          fr.write("\n");
        }
      }
    } catch (IOException ioe) {
      logger.warn("IOE: {}", ioe.getMessage());
    }
  }

  /**
   * not reasonable to consider cabs, which nobody would accept
   * @param demand customers orders
   * @param supply cabs
   * @return cabs
   */
  public Cab[] getRidOfDistantCabs(TaxiOrder[] demand, Cab[] supply) {
    List<Cab> list = new ArrayList<>();
    for (Cab cab : supply) {
      for (TaxiOrder taxiOrder : demand) {
        int dst = distanceService.getDistances()[cab.getLocation()][taxiOrder.fromStand];
        if (dst + SCHEDULING_DURATION <= taxiOrder.getMaxWait()) {
          // great, we have at least one customer in range for this cab
          list.add(cab);
          break;
        }
      }
    }
    return list.toArray(new Cab[0]);
  }

  /**
   * not reasonable to consider customers without a cab in acceptable range
   * @param demand customers orders
   * @param supply cabs
   * @return orders
   */
  public TaxiOrder[] getRidOfDistantCustomers(TaxiOrder[] demand, Cab[] supply) {
    List<TaxiOrder> list = new ArrayList<>();
    for (TaxiOrder taxiOrder : demand) {
      for (Cab cab : supply) {
        int dst = distanceService.getDistances()[cab.getLocation()][taxiOrder.fromStand];
        if (dst + SCHEDULING_DURATION <= taxiOrder.getMaxWait()) { // SCHEDULING_DURATION is an average duration, at this stage we don't know how long it will take
          // TASK: maybe we could forecast SCHEDULING_DURAION based on demand size
          // great, we have at least one cab in range for this customer
          list.add(taxiOrder);
          break;
        }
      }
    }
    return list.toArray(new TaxiOrder[0]);
  }

  /**
   * find customer orders that have not been assigned by LCM
   * @param pairs lcm output
   * @param tempDemand demand sent to LCM
   * @return input to solver
   */
  public static TaxiOrder[] demandForSolver(List<LcmPair> pairs, TaxiOrder[] tempDemand) {
    List<TaxiOrder> solverDemand = new ArrayList<>();
    for (TaxiOrder o: tempDemand) {
      boolean found = false;
      for (LcmPair pair : pairs) {
        if (o.id.equals(tempDemand[pair.getClnt()].id)) {
          found = true;
          break;
        }
      }
      if (!found) {
        solverDemand.add(o);
      }
    }
    return solverDemand.toArray(new TaxiOrder[0]);
  }

  /**
   * find cabs that have not been assigned by LCM
   * @param pairs lcm output
   * @param tempSupply cabs sent to LCM
   * @return input to solver
   */
  public static Cab[] supplyForSolver(List<LcmPair> pairs, Cab[] tempSupply) {
    List<Cab> solverSupply = new ArrayList<>();
    for (Cab c : tempSupply) {
      boolean found = false;
      for (LcmPair pair : pairs) {
        if (c.getId().equals(tempSupply[pair.getCab()].getId())) {
          found = true;
          break;
        }
      }
      if (!found) {
        solverSupply.add(c);
      }
    }
    return solverSupply.toArray(new Cab[0]);
  }
}
