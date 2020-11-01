package no.kabina.kaboot.cabs;

import java.util.Optional;
import no.kabina.kaboot.utils.AuthUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CabController {

  private CabRepository repository;

  public CabController(CabRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/cabs/{id}")
  public Cab one(@PathVariable int id, Authentication auth) {
    //Long cabId = AuthUtils.getUserId(auth, "ROLE_CUSTOMER");
    // TODO: more authorisation ?
    return repository.findById(id);
  }

  @PutMapping(value="/cabs/{id}", consumes = "application/json")
  public Cab updateCab(@PathVariable Long id, @RequestBody Cab cab, Authentication auth) {
    cab.setId(id);
    Long usrId = AuthUtils.getUserId(auth, "ROLE_CAB");
    if (usrId.longValue() != cab.getId().longValue()) { // now it is that simple - cab_id == usr_id
      return null;
    }
    Optional<Cab> prev = repository.findById(cab.getId());
    if (prev.isEmpty()) {
      return null;  // we cannot update a nonexisting object
    }
    return repository.save(cab);
  }
}
