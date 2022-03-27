package no.kabina.kaboot.stops;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class Stop {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  @Column //(nullable = true)
  private String no;

  private String name;

  @Column //(nullable = true)
  private String type;
  
  @Column //(nullable = true)
  private int bearing;
  
  private double latitude;
  private double longitude;

  public Stop() {}

  /** constructor.

   * @param id id
   * @param no some other key
   * @param name name
   * @param type type of stop
   * @param bearing heading
   * @param latitude lat
   * @param longitude long
   */
  public Stop(Long id, String no, String name, String type, int bearing, double latitude,
              double longitude) {
    this.id = id;
    this.no = no;
    this.name = name;
    this.type = type;
    this.bearing = bearing;
    this.latitude = latitude;
    this.longitude = longitude;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getNo() {
    return no;
  }

  public void setNo(String no) {
    this.no = no;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public int getBearing() {
    return bearing;
  }

  public void setBearing(int bearing) {
    this.bearing = bearing;
  }

  public double getLatitude() {
    return latitude;
  }

  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public void setLongitude(double longitude) {
    this.longitude = longitude;
  }
}
