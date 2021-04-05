public class Stop {
    public int id;

    public String no;
    public String name;
    public String type;
    public String bearing;
    public double latitude;
    public double longitude;

    public Stop(int id, String no, String name, String type, String bearing, double latitude, double longitude) {
        this.id = id;
        this.no = no;
        this.name = name;
        this.type = type;
        this.bearing = bearing;
        this.latitude = latitude;
        this.longitude = longitude;
      }
}