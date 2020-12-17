public class Cab {
    public Cab(int i, int l, Utils.CabStatus s) {
        this.id = i;
        this.location = l;
        this.status = s;
    }
    public int id;
    public int location;
    public Utils.CabStatus status;
    public void setId(int id) { this.id = id; }
    public void setLocation(int l) { this.location = l; }
    public void setStatus(Utils.CabStatus s) { this.status = s; }
}
