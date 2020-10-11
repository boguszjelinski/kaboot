package no.kabina.kaboot.routes;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.orders.TaxiOrder;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Route {

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Long id;

    private RouteStatus status;

    protected Route() { }

    public Route(RouteStatus status) {
        this.status = status;
    }

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name="cab_id", nullable=true)
    private Cab cab;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
  //  @JoinColumn(name = "route_id")
    private Set<Task> tasks = new HashSet<>();

    @OneToMany // many customers that can ride within one route (pool)
    @JoinColumn(name = "route_id")
    private Set<TaxiOrder> orders = new HashSet<>();

    public enum RouteStatus {
        ASSIGNED,  // not confirmed, initial status
        ACCEPTED,  // plan accepted by customer, waiting for the cab
        REJECTED,  // proposal rejected by customer(s)
        ABANDONED, // cancelled after assignment but before 'PICKEDUP'
        COMPLETE
    }

    public Long getId() {
        return id;
    }

    public RouteStatus getStatus() {
        return status;
    }
    public void setTasks(Set<Task> tasks) { this.tasks = tasks; }
    public Set<Task> getTasks() { return tasks; }
}
