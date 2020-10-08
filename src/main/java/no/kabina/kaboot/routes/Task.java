package no.kabina.kaboot.routes;

import no.kabina.kaboot.orders.TaxiOrder;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import java.util.HashSet;
import java.util.Set;

@Entity
public class Task { // a leg of a route
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Long id;

    private int stand; // go to

    public Task(int stand) {
        this.stand = stand;
    }

    @OneToMany
    @JoinColumn(name = "task_id")
    private Set<TaxiOrder> items = new HashSet<TaxiOrder>();

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name="route_id", nullable=true)
    private Route route;

    @OneToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name="next_id", nullable=true)
    private Task next;  // next task in route

    public Long getId() {
        return id;
    }

    public int getStand() {
        return stand;
    }
}
