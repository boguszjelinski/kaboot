package no.kabina.kaboot.stats;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class StatService {

  StatRepository statRepo;

  private List<Long> pickupTime; // duration, to count the average

  StatService(StatRepository repo) {
    this.statRepo = repo;
    pickupTime = new ArrayList<>();
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

  public Stat updateAvgIntVal(String key, int value) {
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

  public void addPickupTime(Long time) {
    pickupTime.add(time);
  }

  public int countAvgPickupTime() {
    int sum = pickupTime.stream().mapToInt(Long::intValue).sum();
    if (pickupTime.size() == 0) {
      return -1;
    } else {
      return sum / pickupTime.size();
    }
  }
}
