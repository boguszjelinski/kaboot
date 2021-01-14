package no.kabina.kaboot.dispatcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import no.kabina.kaboot.orders.TaxiOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ExternPool {
  private final Logger logger = LoggerFactory.getLogger(ExternPool.class);

  @Value("${kaboot.extern-pool.cmd}")
  private String cmd;

  @Value("${kaboot.extern-pool.input}")
  private String inputFile;

  @Value("${kaboot.extern-pool.output}")
  private String outputFile;

  @Value("${kaboot.extern-pool.threads}")
  private int numbOfThreads;

  private HashMap<Long, TaxiOrder> orders;

  public ExternPool() {
    orders = new HashMap<>();
  }

  /**
   * for testing
   */
  public ExternPool(String cmd, String inputFile, String outputFile, int numbOfThreads) {
    orders = new HashMap<>();
    this.cmd = cmd;
    this.inputFile = inputFile;
    this.outputFile = outputFile;
    this.numbOfThreads = numbOfThreads;
  }

  /**
  * Threaded Pool discoverer
  * @param dem demand
  * @param inPool number of passnegers in pool
  * @return pool proposal
  */
  public PoolElement[] findPool(TaxiOrder[] dem, int inPool) {
    for (TaxiOrder o : dem) {
      orders.put(o.getId(), o);
    }
    writeInput(dem);
    // findpool 4 8 pool-in.csv 100 out.csv
    // findpool pool-size threads demand-file-name rec-number output-file
    Process p = null;
    try {
      p = Runtime.getRuntime().exec(cmd + " " + inPool + " " + numbOfThreads + " "
                                                            + inputFile + " " + dem.length + " " +  outputFile);
    } catch (IOException e) {
      e.printStackTrace();
    }
    try {
      p.waitFor();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return readOutput(inPool);
  }

  private PoolElement[] readOutput(int inPool) {
    List<PoolElement> list = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(outputFile))) {
      String line = null;
      while ((line = reader.readLine()) != null) {
        String[] data = line.split(",");
        TaxiOrder[] custs = new TaxiOrder[inPool + inPool]; // pickups + dropoffs
        for (int i = 0; i < inPool + inPool; i++) {
          custs[i] = orders.get(Long.parseLong(data[i]));
          if (custs[i] == null) {
            logger.warn("Order not found in temporary memory: " + data[i]);
          }
        }
        list.add(new PoolElement(custs, inPool, 0)); // cost is only used for sorting in old routins
      }
    } catch (IOException e) {
      logger.warn("missing output from solver");
      return new PoolElement[0];
    }
    return list.toArray(new PoolElement[0]);
  }

  private void writeInput(TaxiOrder[] demand) {
    try (FileWriter fr = new FileWriter(new File(inputFile))) {
      for (TaxiOrder o : demand) {
        fr.write(o.id + "," + o.fromStand + "," + o.toStand
                    + "," + o.getMaxWait() + "," + o.getMaxLoss() + ",\n");
      }
    } catch (IOException ioe) {
      logger.warn("IOE: {}", ioe.getMessage());
    }
  }
}
