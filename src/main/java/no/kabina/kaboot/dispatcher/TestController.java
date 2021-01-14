package no.kabina.kaboot.dispatcher;

import no.kabina.kaboot.utils.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

  private final DispatcherService service;
  private final Logger logger = LoggerFactory.getLogger(TestController.class);

  public TestController(DispatcherService service) {
    this.service = service;
  }

  /**
   *  just to test scheduler
   * @param auth authentication
   * @return OK
   */
  @GetMapping("/schedulework")
  public String one(Authentication auth) {
    Long id = AuthUtils.getUserId(auth, "ROLE_ADMIN");
    if (id != -1) {
      logger.info("Manual scheduler execution");
      service.findPlan(true);
      return "OK";
    }
    return "NOK";
  }
}
