package no.kabina.kaboot.customers;

import no.kabina.kaboot.orders.TaxiOrder;

import javax.persistence.Entity;
import javax.persistence.GenerationType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;

import java.util.HashSet;
import java.util.Set;

// MariaDB
// mysql -u kabina -p -D kabina < customer-just-inserts.sql

@Entity
public class Customer {
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Long id;

    @OneToMany
    @JoinColumn(name = "customer_id")
    private Set<TaxiOrder> items = new HashSet<>();

    protected Customer() { }

    public Long getId() {
        return id;
    }
}
