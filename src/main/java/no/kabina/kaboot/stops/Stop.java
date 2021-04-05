package no.kabina.kaboot.stops;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class Stop {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  private String no;
  private String name;
  private String type;
  private String bearing;
  private double latitude;
  private double longitude;

  public Stop() {}

  /**
   *
   * @param id
   * @param no
   * @param name
   * @param type
   * @param bearing
   * @param latitude
   * @param longitude
   */
  public Stop(Long id, String no, String name, String type, String bearing, double latitude, double longitude) {
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

  public String getBearing() {
    return bearing;
  }

  public void setBearing(String bearing) {
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
