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

  /** constructor.

   * @param name key
   * @param intVal integer
   * @param dblVal double
   */
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
