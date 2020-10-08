package no.kabina.kaboot.routes;

import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.customers.Customer;
import no.kabina.kaboot.orders.TaxiOrder;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
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

    @OneToMany
    @JoinColumn(name = "route_id")
    private Set<Task> items = new HashSet<Task>();

    @OneToMany // many customers that can ride within one route (pool)
    @JoinColumn(name = "route_id")
    private Set<TaxiOrder> orders = new HashSet<TaxiOrder>();

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
}
