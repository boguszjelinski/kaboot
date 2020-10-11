package no.kabina.kaboot.orders;

import no.kabina.kaboot.utils.AuthUtils;
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
        return service.saveTaxiOrder(order, AuthUtils.getUserId(auth, "ROLE_CUSTOMER"));
    }
}