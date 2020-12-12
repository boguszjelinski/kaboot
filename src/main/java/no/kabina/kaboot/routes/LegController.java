package no.kabina.kaboot.routes;

import java.util.Optional;
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

  public LegController(LegRepository legRepo) {
    this.legRepo = legRepo;
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
    if (usrId.longValue() != l.getRoute().getCab().getId()) { // now it is that simple - cab_id == usr_id
      return null;
    }
    l.setStatus(leg.getStatus());
    legRepo.save(l);
    return "OK";
  }
}
