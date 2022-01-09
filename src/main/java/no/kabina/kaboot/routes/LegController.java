package no.kabina.kaboot.routes;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.orders.TaxiOrderRepository;
import no.kabina.kaboot.utils.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegController {
  private final Logger logger = LoggerFactory.getLogger(LegController.class);
  private final LegRepository legRepo;
  private final TaxiOrderRepository orderRepo;

  public LegController(LegRepository legRepo, TaxiOrderRepository orderRepo) {
    this.legRepo = legRepo;
    this.orderRepo = orderRepo;
  }

  /** mainly to mark COMPLETED and to bill the customer
   * @param id
   * @param leg
   * @param auth
   * @return
   */
  @PutMapping(value = "/legs/{id}", consumes = "application/json")
  public String updateLeg(@PathVariable Long id, @RequestBody LegPojo leg, Authentication auth) {
    Leg l = null;
    logger.info("PUT leg={}", id);
    Optional<Leg> o = legRepo.findById(id);
    if (o.isPresent()) {
      l = o.get();
    } else {
      return null;
    }
    Long usrId = AuthUtils.getUserId(auth, "ROLE_CAB");
    if (l.getRoute() == null || l.getRoute().getCab() == null
        || usrId.longValue() != l.getRoute().getCab().getId()) { // now it is that simple - cab_id == usr_id
      return null;
    }
    l.setStatus(leg.getStatus());
    if (leg.getStatus().equals(Route.RouteStatus.STARTED)) {
      l.setStarted(LocalDateTime.now());
      updateStartedOrders(l.getRoute().getOrders(), l.getFromStand());
    } else if (leg.getStatus().equals(Route.RouteStatus.COMPLETED)) {
      l.setCompleted(LocalDateTime.now());
      updateCompletedOrders(l.getRoute().getOrders(), l.getToStand());
    }
    legRepo.save(l);
    return "OK";
  }

  private void updateStartedOrders(Set<TaxiOrder> orders, int from) {
    if (orders == null) {
      return;
    }
    for (TaxiOrder o : orders) {
      if (o.getFromStand() == from) {
        o.setStarted(LocalDateTime.now());
        orderRepo.save(o);
      }
    }
  }

  private void updateCompletedOrders(Set<TaxiOrder> orders, int to) {
    if (orders == null) {
      return;
    }
    for (TaxiOrder o : orders) {
      if (o.getToStand() == to) {
        o.setCompleted(LocalDateTime.now());
        orderRepo.save(o);
      }
    }
  }
}
