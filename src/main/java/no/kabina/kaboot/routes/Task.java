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

import java.util.HashSet;
import java.util.Set;

@Entity
public class Task { // a leg of a route
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Long id;

    private int stand; // go to


    private int order; // place in line; ID of the next would be better, but we don't have this id while creating 'entities' in JPA

    public Task(int stand, int order) {
        this.stand = stand;
        this.order = order;
    }

    @OneToMany
    @JoinColumn(name = "task_id")
    private Set<TaxiOrder> items = new HashSet<>();

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name="route_id", nullable=true)
    private Route route;

    public Long getId() {
        return id;
    }

    public int getStand() {
        return stand;
    }

    public int getOrder() {
        return order;
    }
}
