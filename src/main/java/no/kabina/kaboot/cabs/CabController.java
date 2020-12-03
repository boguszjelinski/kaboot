package no.kabina.kaboot.cabs;

import java.util.Optional;
import no.kabina.kaboot.utils.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class CabController {
  private Logger logger = LoggerFactory.getLogger(CabController.class);

  private CabRepository repository;

  public CabController(CabRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/cabs/{id}")
  public Cab one(@PathVariable int id, Authentication auth) {
    logger.info("GET cab=" + id);
    //Long cabId = AuthUtils.getUserId(auth, "ROLE_CUSTOMER"); both roles should access
    // TODO: more authorisation ?
    Cab c = repository.findById(id);
    c.setItems(null); // omit this in JSON
    return c;
  }

  @PutMapping(value = "/cabs/{id}", consumes = "application/json")
  public String updateCab(@PathVariable Long id, @RequestBody Cab cab, Authentication auth) {
    logger.info("PUT cab={}", id);
    cab.setId(id);
    Long usrId = AuthUtils.getUserId(auth, "ROLE_CAB");
    if (usrId.longValue() != cab.getId().longValue()) { // now it is that simple - cab_id == usr_id
      return null;
    }
    Optional<Cab> prev = repository.findById(cab.getId());
    if (prev.isEmpty()) {
      return null;  // we cannot update a nonexisting object
    }
    repository.save(cab);
    return "OK";
  }

  // TODO: temporary, should not be allowed for ROLE_CAB
  @PostMapping(value="/cabs/") // , consumes = "application/json"boot
  public Cab insertCab(@RequestBody CabPOJO cab, Authentication auth) {
    logger.info("POST cab");
    Cab c = new Cab(cab.getLocation(), cab.getStatus());
    return repository.save(c);
  }
}
