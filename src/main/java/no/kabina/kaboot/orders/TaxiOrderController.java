package no.kabina.kaboot.orders;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TaxiOrderController {

    private final TaxiOrderRepository repository;

    public TaxiOrderController(TaxiOrderRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/orders/{id}")
    public String one(@PathVariable int id) {
        TaxiOrder taxiOrder = repository.findById(id);
        return taxiOrder.toString();
    }

    @GetMapping("/orders")
    public List<TaxiOrder> all() {
        return repository.findAll();
    }

    //  curl -d '{"fromStand":0, "toStand": 1, "maxWait":1, "maxLoss": 30, "shared": true}' -H 'Content-Type: application/json' http://localhost:8080/orders
    @PostMapping(value="/orders", consumes = "application/json")
    public TaxiOrder newTaxiOrder(@RequestBody TaxiOrderPOJO newTaxiOrder) {
        TaxiOrder order = new TaxiOrder(newTaxiOrder.fromStand,
                newTaxiOrder.toStand, newTaxiOrder.maxWait, newTaxiOrder.maxLoss, newTaxiOrder.shared);
        return repository.save(order);
    }

}