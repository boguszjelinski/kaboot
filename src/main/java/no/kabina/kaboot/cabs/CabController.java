package no.kabina.kaboot.cabs;

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
public class CabController {
  private final Logger logger = LoggerFactory.getLogger(CabController.class);

  private final CabRepository repository;

  public CabController(CabRepository repository) {
    this.repository = repository;
  }

  /**
   *  Getting cab
   * @param id ID
   * @param auth authentication service
   * @return Cab
   */
  @GetMapping("/cabs/{id}")
  public Cab one(@PathVariable int id, Authentication auth) {
    logger.info("GET cab_id={}", id);
    //Long cabId = AuthUtils.getUserId(auth, "ROLE_CUSTOMER"); both roles should access
    // TASK: more authorisation ?
    Cab c = repository.findById(id);
    if (c == null) {
      return null;
    }
    c.setOrders(null); // omit this in JSON
    return c;
  }

  /**
   *  Setting cab's status and location
   * @param id ID
   * @param auth authentication service
   * @param cabIn CabPojo
   * @return status
   */
  @PutMapping(value = "/cabs/{id}", consumes = "application/json")
  public String updateCab(@PathVariable Long id, @RequestBody CabPojo cabIn, Authentication auth) {
    logger.info("PUT cab_id={}, location={}, status={}", id, cabIn.getLocation(), cabIn.getStatus());

    Long usrId = AuthUtils.getUserId(auth, "ROLE_CAB");
    if (usrId.longValue() != id) { // now it is that simple - cab_id == usr_id
      return null;
    }
    Optional<Cab> prev = repository.findById(id);
    if (prev.isEmpty()) {
      return null;  // we cannot update a nonexisting object
    }
    Cab cab = prev.get();
    cab.setStatus(cabIn.getStatus());
    cab.setLocation(cabIn.getLocation());
    repository.save(cab);
    return "OK";
  }

  // TASK: temporary, should not be allowed for ROLE_CAB
  @PostMapping(value = "/cabs/") // , consumes = "application/json"boot
  public Cab insertCab(@RequestBody CabPojo cab, Authentication auth) {
    Long usrId = AuthUtils.getUserId(auth, "ROLE_CAB");
    if (usrId == -1) {
      logger.warn("Not authorised");
      return null;
    }
    logger.info("POST cab_id={},", usrId);
    return repository.save(new Cab(cab.getLocation(), cab.getName(), cab.getStatus()));
  }
}
