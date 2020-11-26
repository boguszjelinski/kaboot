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
  public TaxiOrder one(@PathVariable int id, Authentication auth) {
    logger.info("GET order=" + id);
    Long cabId = AuthUtils.getUserId(auth, "ROLE_CUSTOMER");
    TaxiOrder to = repository.findById(id);
    if (to == null) {
      return null;
    }
      // TODO: authorisation - customer can only see its own orders
    if (to.getRoute() != null) {
      to.getRoute().setLegs(null); // too much detail
      // but we need 'cab' from route
    }
    if (to.getLeg() != null) {
      to.getLeg().setRoute(null); // too much detail
    }
    return to;
  }

  @GetMapping("/orders")
  public List<TaxiOrder> all() {
    logger.info("GET all orders");
    return repository.findAll();
  }

  //  curl -d '{"fromStand":0, "toStand": 1, "maxWait":1, "maxLoss": 30, "shared": true}' -H 'Content-Type: application/json' http://localhost:8080/orders
  @PostMapping(value="/orders", consumes = "application/json")
  public TaxiOrder newTaxiOrder(@RequestBody TaxiOrderPojo newTaxiOrder, Authentication auth) {
    // TODO: should fail if another order is RECEIVED
    logger.info("POST order");
    Long usr_id = AuthUtils.getUserId(auth, "ROLE_CUSTOMER");
    if (usr_id == null) {
      return null; // not authorised
    }
    if (newTaxiOrder.fromStand == newTaxiOrder.toStand) { // a joker
      return null;
    }
    TaxiOrder order = new TaxiOrder(newTaxiOrder.fromStand, newTaxiOrder.toStand,
                    newTaxiOrder.maxWait, newTaxiOrder.maxLoss, newTaxiOrder.shared, TaxiOrder.OrderStatus.RECEIVED);
    order.setEta(-1);
    order.setInPool(false);
    return service.saveTaxiOrder(order, usr_id);
  }

  /** mainly to update status
   *
   * @param id
   * @param newTaxiOrder
   * @param auth
   * @return
   */
  @PutMapping(value="/orders/{id}", consumes = "application/json")
  public TaxiOrder updateTaxiOrder(@PathVariable Long id, @RequestBody TaxiOrderPojo newTaxiOrder, Authentication auth) {
    logger.info("PUT order=" + id);
    Long usr_id = AuthUtils.getUserId(auth, "ROLE_CUSTOMER");
    if (usr_id == null) {
      return null; // not authorised
    }
    Optional<TaxiOrder> ord = repository.findById(id);
    if (ord == null || !ord.isPresent()) {
      return null;
    }
    ord.get().setId(id);
    ord.get().setStatus(newTaxiOrder.status);
    return service.updateTaxiOrder(ord.get(), usr_id);
  }
}
