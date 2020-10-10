package no.kabina.kaboot.orders;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TaxiOrderController {

    private final TaxiOrderRepository repository;
    private final TaxiOrderService service;

    public TaxiOrderController(TaxiOrderRepository repository, TaxiOrderService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping("/orders/{id}")
    public String one(@PathVariable int id) {
        TaxiOrder taxiOrder = repository.findById(id);
        if (taxiOrder == null) {
            return "Not found";
        }
        return taxiOrder.toString();
    }

    @GetMapping("/orders")
    public List<TaxiOrder> all() {
        return repository.findAll();
    }

    //  curl -d '{"fromStand":0, "toStand": 1, "maxWait":1, "maxLoss": 30, "shared": true}' -H 'Content-Type: application/json' http://localhost:8080/orders
    @PostMapping(value="/orders", consumes = "application/json")
    public TaxiOrder newTaxiOrder(@RequestBody TaxiOrderPOJO newTaxiOrder, Authentication auth) {
        TaxiOrder order = new TaxiOrder(newTaxiOrder.fromStand, newTaxiOrder.toStand,
                        newTaxiOrder.maxWait, newTaxiOrder.maxLoss, newTaxiOrder.shared, TaxiOrder.OrderStatus.RECEIVED);
        return service.saveTaxiOrder(order, getUserId(auth, "ROLE_CUSTOMER"));
    }

    public Long getUserId(Authentication authentication, String mustBeRole) {
        String usrName = authentication.getName();
        if (mustBeRole == null || usrName == null) {
            return -1L;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (mustBeRole.equals(authority.getAuthority())) { // maybe irrelevant - we have SecurityConfig for this
                switch (mustBeRole) {
                    case "ROLE_CUSTOMER":
                        return Long.parseLong(usrName.substring("cust".length()));
                    case "ROLE_CAB":
                        return Long.parseLong(usrName.substring("cab".length()));
                }
            }
        }
        return -1L;
    }
}