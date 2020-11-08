package no.kabina.kaboot.orders;

import java.util.List;
import java.util.Optional;

import no.kabina.kaboot.utils.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaxiOrderController {

  private Logger logger = LoggerFactory.getLogger(TaxiOrderController.class);

  private final TaxiOrderRepository repository;
  private final TaxiOrderService service;

  public TaxiOrderController(TaxiOrderRepository repository, TaxiOrderService service) {
    this.repository = repository;
    this.service = service;
  }

  @GetMapping("/orders/{id}")
  public String one(@PathVariable int id, Authentication auth) {
    logger.info("GET order=" + id);
    Long cabId = AuthUtils.getUserId(auth, "ROLE_CUSTOMER");
    // TODO: authorisation - customer can only see its own orders
    TaxiOrder taxiOrder = repository.findById(id);
    if (taxiOrder == null) {
      return "Not found";
    }
    return taxiOrder.toString();
  }

  @GetMapping("/orders")
  public List<TaxiOrder> all() {
    logger.info("GET all orders");
    return repository.findAll();
  }

  //  curl -d '{"fromStand":0, "toStand": 1, "maxWait":1, "maxLoss": 30, "shared": true}' -H 'Content-Type: application/json' http://localhost:8080/orders
  @PostMapping(value="/orders", consumes = "application/json")
  public TaxiOrder newTaxiOrder(@RequestBody TaxiOrderPOJO newTaxiOrder, Authentication auth) {
    logger.info("POST order");
    if (newTaxiOrder.fromStand == newTaxiOrder.toStand) { // a joker
      return null;
    }
    TaxiOrder order = new TaxiOrder(newTaxiOrder.fromStand, newTaxiOrder.toStand,
                    newTaxiOrder.maxWait, newTaxiOrder.maxLoss, newTaxiOrder.shared, TaxiOrder.OrderStatus.RECEIVED);
    return service.saveTaxiOrder(order, AuthUtils.getUserId(auth, "ROLE_CUSTOMER"));
  }

  @PutMapping(value="/orders/{id}", consumes = "application/json")
  public TaxiOrder updateTaxiOrder(@PathVariable Long id, @RequestBody TaxiOrderPOJO newTaxiOrder, Authentication auth) {
    logger.info("PUT order=" + id);
    Optional<TaxiOrder> ord = repository.findById(id);

    if (ord == null || !ord.isPresent()) {
      return null;
    }
    ord.get().setId(id);
    return service.updateTaxiOrder(ord.get(), AuthUtils.getUserId(auth, "ROLE_CUSTOMER"));
  }
}
