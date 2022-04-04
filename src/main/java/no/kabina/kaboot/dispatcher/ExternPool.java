package no.kabina.kaboot.dispatcher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ExternPool {
  private final Logger logger = LoggerFactory.getLogger(ExternPool.class);

  @Value("${kaboot.extern-pool.flag-file}")
  private String flagFile;

  @Value("${kaboot.extern-pool.orders-file}")
  private String demandFile;

  @Value("${kaboot.extern-pool.cabs-file}")
  private String supplyFile;

  @Value("${kaboot.extern-pool.output-file}")
  private String outputFile;

  DispatcherService dispatcherService;

  public ExternPool(DispatcherService dispatcherService) {
    this.dispatcherService = dispatcherService;
  }

  public void setDispatcherService(DispatcherService dispatcherService) {
    this.dispatcherService = dispatcherService;
  }

  /**
   * for testing
   */
  public ExternPool(String flagFile, String demandFile, String supplyFile, String outputFile) {
    this.flagFile = flagFile;
    this.demandFile = demandFile;
    this.supplyFile = supplyFile;
    this.outputFile = outputFile;
  }

  public ExternPool() {
    this.flagFile = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\flag.txt";
    this.demandFile = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\orders.csv";
    this.supplyFile = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\cabs.csv";
    this.outputFile = "C:\\Users\\dell\\TAXI\\GITLAB\\kaboot\\pools.csv";
  }

  /**
  * Threaded Pool discoverer
  * @param dem demand
  * @param cabs supply
  * @param update if DB is to be updated or just a unit test
  * @return pool proposal
  */
  public ExternPoolElement[] findExternPool(TaxiOrder[] dem, Cab[] cabs, boolean update) {
    if (dem == null || dem.length < 2 || cabs == null || cabs.length==0) {
      logger.info("Demand or supply empty");
      return new ExternPoolElement[0];
    }
    writeInput(demandFile, dem);
    writeCabs(supplyFile, cabs);

    try {
      Process p = Runtime.getRuntime().exec("runner " + flagFile); // TASK runner name in YML
      p.waitFor();
    } catch (IOException e) {
      logger.warn("IOException while calling poold: {}", e.getMessage());
    } catch (Exception e) {
      logger.warn("Exception while calling poold: {}", e.getMessage());
    }

    String json = readResponse(outputFile);
    deleteFile(outputFile);

    if (json.length() < 3) {
      logger.warn("Empty pool read from external pool");
      return new ExternPoolElement[0];
    }
    ObjectMapper om = new ObjectMapper();
    ExternPoolElement[] ret = null;
    try {
      ret = om.readValue(json, ExternPoolElement[].class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    if (ret == null) {
      logger.warn("External pool returned no result");
      return new ExternPoolElement[0];
    }
    return ret;
  }

  public PoolElement[] findPool(TaxiOrder[] dem, Cab[] cabs, boolean update) {
    List<PoolElement> list = new ArrayList<>();
    ExternPoolElement[] ret = findExternPool(dem, cabs, update);
    for (ExternPoolElement e : ret) {
      TaxiOrder[] cust = new TaxiOrder[e.len + e.len];
      for (int i = 0; i < e.ids.length; i++) {
        cust[i] = dem[e.ids[i]];
      }
      PoolElement pe = new PoolElement(cust, e.acts, e.len, 0);
      list.add(pe);
      if (update) {
        dispatcherService.assignPoolToCab(cabs[e.cab], pe);
        // remove the cab from list so that it cannot be allocated twice
        cabs[e.cab] = null;
      }
    }
    return list.toArray(new PoolElement[0]);
  }

  private void deleteFile(String fileName) {
    try {
      Files.delete(Paths.get(fileName));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void writeInput(String file, TaxiOrder[] demand) {
    try (FileWriter fr = new FileWriter(new File(file))) {
      for (TaxiOrder o : demand) {
        fr.write(o.id + "," + o.fromStand + "," + o.toStand
                    + "," + o.getMaxWait() + "," + o.getMaxLoss()+ "," + o.getDistance() + "\n");
      }
    } catch (IOException ioe) {
      logger.warn("IOE: {}", ioe.getMessage());
    }
  }

  private void writeCabs(String file, Cab[] supply) {
    try (FileWriter fr = new FileWriter(new File(file))) {
      for (Cab o : supply) {
        fr.write(o.getId() + "," + o.getLocation() + "\n");
      }
    } catch (IOException ioe) {
      logger.warn("IOE: {}", ioe.getMessage());
    }
  }

  private String readResponse(String fileName) {
    try {
      return new String(Files.readAllBytes(Paths.get(fileName)));
    } catch (IOException e) {
      e.printStackTrace();
      return "";
    }
  }

  public static class ExternPoolElement {
    public int cab;
    public int len;
    public int ids[];
    public char acts[];
  }
}
