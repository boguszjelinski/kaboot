package no.kabina.kaboot.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StatService {

  StatRepository statRepo;
  HashMap<String, List<Long>> avgElements;

  StatService(StatRepository repo) {
    this.statRepo = repo;
    avgElements = new HashMap<>(); // String, List<Long>
  }

    /**
     *
     * @param key
     * @param value
     * @return
     */
  public Stat updateMaxIntVal(String key, int value) {
    Stat stat = statRepo.findByName(key);
    if (stat == null) {
      Stat s = new Stat(key, value, 0);
      return statRepo.save(s);
    }
    if (value > stat.getIntVal()) {
      stat.setIntVal(value);
      return statRepo.save(stat);
    } else {
      return stat;
    }
  }

  public Stat addToIntVal(String key, int value) {
    Stat stat = statRepo.findByName(key);
    if (stat == null) {
      Stat s = new Stat(key, value, 0);
      return statRepo.save(s);
    }
    stat.setIntVal(stat.getIntVal() + value);
    return statRepo.save(stat);
  }

  public Stat updateIntVal(String key, int value) {
    Stat stat = statRepo.findByName(key);
    if (stat == null) {
      Stat s = new Stat(key, value, 0);
      return statRepo.save(s);
    }
    stat.setIntVal(value);
    return statRepo.save(stat);

  }

    /**
     *
     * @param key
     * @return
     */
  public Stat incrementIntVal(String key) {
    Stat stat = statRepo.findByName(key);
    if (stat == null) {
      Stat s = new Stat(key, 1, 0);
      return statRepo.save(s);
    }
    stat.setIntVal(stat.getIntVal() + 1);
    return statRepo.save(stat);
  }

  public void addAverageElement(String key, Long time) {
    List<Long> list = avgElements.get(key);
    if (list == null) {
      list = new ArrayList<>();
    }
    list.add(time);
    avgElements.put(key, list);
  }

  public int countAverage(String key) {
    List<Long> list = avgElements.get(key);
    if (list == null) {
      return 0;
    }
    int sum = list.stream().mapToInt(Long::intValue).sum();
    if (list.isEmpty()) {
      return -1;
    } else {
      return sum / list.size();
    }
  }

  public void updateMaxAndAvgTime(String key, long startTime) {
    long endTime = System.currentTimeMillis();
    int totalTime = (int) ((endTime - startTime) / 1000F);

    addAverageElement("avg_" + key, (long) totalTime);
    updateMaxIntVal("max_" + key, totalTime);
  }

  public void updateMaxAndAvgStats(String key, int val) {
    addAverageElement("avg_" + key, (long) val);
    updateMaxIntVal("max_" + key, val);
  }
}
