public class Cab {
    public Cab(int i, int l, ApiClient.CabStatus s) {
        this.id = i;
        this.location = l;
        this.status = s;
    }
    public int id;
    public int location;
    public ApiClient.CabStatus status;
    public void setId(int id) { this.id = id; }
    public void setLocation(int l) { this.location = l; }
    public void setStatus(ApiClient.CabStatus s) { this.status = s; }
}
