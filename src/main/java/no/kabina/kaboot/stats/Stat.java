package no.kabina.kaboot.stats;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Stat {

  @Id
  private String name;

  private int intVal;
  private double dblVal;

  protected Stat() { }

  public Stat(String name, int intVal, double dblVal) {
    this.name = name;
    this.intVal = intVal;
    this.dblVal = dblVal;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getIntVal() {
    return intVal;
  }

  public void setIntVal(int intVal) {
    this.intVal = intVal;
  }

  public double getDblVal() {
    return dblVal;
  }

  public void setDblVal(double dblVal) {
    this.dblVal = dblVal;
  }
}
/*
// METRICS
	total_dropped
	total_pickup_time
	total_pickup_numb
	total_simul_time
	max_solver_time
	max_LCM_time
	total_LCM_used
	max_model_size
	max_solver_size
	max_POOL_time
	max_POOL_MEM_size
	max_POOL_size
	total_second_passengers
    INSERT INTO STAT (id, name, value) VALUES (0,"max_model_size",0);
 */