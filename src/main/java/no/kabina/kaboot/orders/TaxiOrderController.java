package no.kabina.kaboot.orders;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.customers.Customer;
import no.kabina.kaboot.dispatcher.DispatcherService;
import no.kabina.kaboot.dispatcher.DistanceService;
import no.kabina.kaboot.stats.StatService;
import no.kabina.kaboot.utils.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin("http://localhost:3000")
@RestController
public class TaxiOrderController {

  private final Logger logger = LoggerFactory.getLogger(TaxiOrderController.class);
  private static final String ROLE_CUSTOMER = "ROLE_CUSTOMER";
  private final TaxiOrderRepository repository;
  private final TaxiOrderService service;
  private final StatService statSrvc;
  private final DistanceService distanceService;

  @Value("${kaboot.consts.max-trip}")
  private int maxTrip;

  public TaxiOrderController(TaxiOrderRepository repository, TaxiOrderService service, StatService statSrvc,
                              DistanceService distanceService) {
    this.repository = repository;
    this.service = service;
    this.statSrvc = statSrvc;
    this.distanceService = distanceService;
  }

  @GetMapping("/orders/{id}")
  public TaxiOrder one(@PathVariable int id, Authentication auth) {
    logger.info("GET order_id={}", id);

    Long custId = AuthUtils.getUserId(auth, ROLE_CUSTOMER);
    TaxiOrder to = repository.findById(id);
    if (to == null) {
      logger.warn("No such order: {}", id);
      return null;
    }
    if (to.getCustomer() == null || !to.getCustomer().getId().equals(custId)) {
      logger.warn("Customer {} not allowed to see order_id={}", custId, id);
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

  @GetMapping("/orders")
  public List<TaxiOrder> byCustomer(Authentication auth) {
    Long custId = AuthUtils.getUserId(auth, ROLE_CUSTOMER);
    if (custId == -1) {
      logger.info("GET orders not authorised");
      return null;
    }
    logger.info("GET orders for customer {}", custId);
    Customer customer = new Customer();
    customer.setId(custId);
    return repository.findByCustomerAndStatusNot(customer, TaxiOrder.OrderStatus.COMPLETED); // TASK: ABANDONED?
  }

  //  curl -v --user cust0:cust0 -d '{"fromStand":0, "toStand": 1, "maxWait":1, "maxLoss": 30, "shared": true}' -H 'Content-Type: application/json' http://localhost:8080/orders
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
    if (distanceService.getDistances()[newTaxiOrder.fromStand][newTaxiOrder.toStand] > maxTrip) {
      logger.info("POST order - requested trip too long");
      return null;
    }
    TaxiOrder order = new TaxiOrder(newTaxiOrder.fromStand, newTaxiOrder.toStand, newTaxiOrder.maxWait,
                      newTaxiOrder.maxLoss, newTaxiOrder.shared, TaxiOrder.OrderStatus.RECEIVED, newTaxiOrder.atTime);
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
    logger.info("PUT order_id={}, status={}", id, newTaxiOrder.getStatus());
    Long usrId = AuthUtils.getUserId(auth, ROLE_CUSTOMER);
    if (usrId == null) {
      return "Not authorised"; // not authorised
    }
    Optional<TaxiOrder> ord = repository.findById(id);
    if (ord.isEmpty()) {
      return "Not found";
    }
    TaxiOrder order = ord.get();
    Duration duration = Duration.between(order.getRcvdTime(), LocalDateTime.now());
    if (newTaxiOrder.status == TaxiOrder.OrderStatus.PICKEDUP) {
      statSrvc.addAverageElement(DispatcherService.AVG_ORDER_PICKUP_TIME, duration.getSeconds());
    } else if (newTaxiOrder.status == TaxiOrder.OrderStatus.COMPLETED) {
      statSrvc.addAverageElement(DispatcherService.AVG_ORDER_COMPLETE_TIME, duration.getSeconds());
    }
    order.setStatus(newTaxiOrder.getStatus()); // we care only about status for now
    if (order.getStatus() != TaxiOrder.OrderStatus.RECEIVED && order.getCab() == null) {
      if (order.getRoute() != null) {
        Cab cab = null;
        try {
          cab = order.getRoute().getCab();
        } catch (Exception e) {
          cab = null;
        }
        if (cab == null || cab.getId() == null) {
          logger.info("Updating order_id={}, Cab is still null when Route {} is not",
                      order.getId(), order.getRoute().getId());
        }
        order.setCab(cab);
      } else {
        logger.info("Updating order_id={}, not nice - order is ASSIGNED and Cab and Route are null",
                    order.getId());
      }
    }
    logger.debug("Updating order_id={}, status={}", order.getId(), order.getStatus());
    service.updateTaxiOrder(order);
    return "OK";
  }
}
