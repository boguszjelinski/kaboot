package no.kabina.kaboot.stats;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Stat {

  @Id
  private String name;

  private int intVal;

  protected Stat() { }

  /** constructor.

   * @param name key
   * @param intVal integer
   */
  public Stat(String name, int intVal) {
    this.name = name;
    this.intVal = intVal;
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

}
