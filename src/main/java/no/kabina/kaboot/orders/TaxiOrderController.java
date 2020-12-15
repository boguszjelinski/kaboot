package no.kabina.kaboot.orders;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import no.kabina.kaboot.dispatcher.SchedulerService;
import no.kabina.kaboot.stats.StatService;
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

  private final Logger logger = LoggerFactory.getLogger(TaxiOrderController.class);
  private static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
  private final TaxiOrderRepository repository;
  private final TaxiOrderService service;
  private final StatService statSrvc;

  public TaxiOrderController(TaxiOrderRepository repository, TaxiOrderService service, StatService statSrvc) {
    this.repository = repository;
    this.service = service;
    this.statSrvc = statSrvc;
  }

  @GetMapping("/orders/{id}")
  public TaxiOrder one(@PathVariable int id, Authentication auth) {
    logger.info("GET order={}", id);

    Long custId = AuthUtils.getUserId(auth, ROLE_CUSTOMER);
    TaxiOrder to = repository.findById(id);
    if (to == null) {
      logger.warn("No such order: {}", id);
      return null;
    }
    if (to.getCustomer() == null || !to.getCustomer().getId().equals(custId)) {
      logger.warn("Customer {} not allowed to see order {}", custId, id);
      return null;
    }
    //Route r = to.getRoute(); // don't be lazy
    // TASK: authorisation - customer can only see its own orders
    if (to.getRoute() != null) {
      to.getRoute().setLegs(null); // too much detail
      // but we need 'cab' from route
    }
    if (to.getLeg() != null) {
      to.getLeg().setRoute(null); // too much detail
    }
    return to;
  }

  //  curl -d '{"fromStand":0, "toStand": 1, "maxWait":1, "maxLoss": 30, "shared": true}' -H 'Content-Type: application/json' http://localhost:8080/orders
  @PostMapping(value = "/orders", consumes = "application/json")
  public TaxiOrder newTaxiOrder(@RequestBody TaxiOrderPojo newTaxiOrder, Authentication auth) {
    // TASK: should fail if another order is RECEIVED
    logger.info("POST order");
    Long custId = AuthUtils.getUserId(auth, ROLE_CUSTOMER);
    if (custId == null) {
      return null; // not authorised
    }
    if (newTaxiOrder.fromStand == newTaxiOrder.toStand) { // a joker
      return null;
    }
    TaxiOrder order = new TaxiOrder(newTaxiOrder.fromStand, newTaxiOrder.toStand,
                    newTaxiOrder.maxWait, newTaxiOrder.maxLoss, newTaxiOrder.shared, TaxiOrder.OrderStatus.RECEIVED);
    order.setEta(-1);
    order.setMaxWait(20);
    order.setInPool(false);
    return service.saveTaxiOrder(order, custId);
  }

  /** goal is to update status
   *
   * @param id
   * @param newTaxiOrder
   * @param auth
   * @return
   */
  @PutMapping(value="/orders/{id}", consumes = "application/json")
  public String updateTaxiOrder(@PathVariable Long id, @RequestBody TaxiOrderPojo newTaxiOrder, Authentication auth) {
    logger.info("PUT order={}, status={}", id, newTaxiOrder.getStatus());
    Long usrId = AuthUtils.getUserId(auth, ROLE_CUSTOMER);
    if (usrId == null) {
      return null; // not authorised
    }
    Optional<TaxiOrder> ord = repository.findById(id);
    if (ord.isEmpty()) {
      return null;
    }
    Duration duration = Duration.between(ord.get().getRcvdTime(), LocalDateTime.now());
    if (newTaxiOrder.status == TaxiOrder.OrderStatus.PICKEDUP) {
      statSrvc.addAverageElement(SchedulerService.AVG_ORDER_PICKUP_TIME, duration.getSeconds());
    } else if (newTaxiOrder.status == TaxiOrder.OrderStatus.COMPLETE) {
      statSrvc.addAverageElement(SchedulerService.AVG_ORDER_COMPLETE_TIME, duration.getSeconds());
    }
    ord.get().setStatus(newTaxiOrder.getStatus()); // we care only about status for now
    logger.debug("Updating order={}, status={}", ord.get().getId(), ord.get().getStatus());
    service.updateTaxiOrder(ord.get());
    return "OK";
  }
}
