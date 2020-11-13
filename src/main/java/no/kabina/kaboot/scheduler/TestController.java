package no.kabina.kaboot.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import no.kabina.kaboot.utils.AuthUtils;

@RestController
public class TestController {

  private Logger logger = LoggerFactory.getLogger(TestController.class);
  private final SchedulerService service;

  public TestController(SchedulerService service) {
    this.service = service;
  }

  @GetMapping("/schedulework")
  public String one(Authentication auth) {
    Long id = AuthUtils.getUserId(auth, "ROLE_ADMIN");
    service.findPlan();
    return "OK";
  }
}
