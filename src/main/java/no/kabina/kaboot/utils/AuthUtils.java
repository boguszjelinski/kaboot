package no.kabina.kaboot.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class AuthUtils {

  private AuthUtils() {} // hiding default public

  /**
  * A simplification of authentication - we get user id from the user name.

  * @param authentication a bean
  * @param mustBeRole a required role for a controller
  * @return user id
  */
  public static Long getUserId(Authentication authentication, String mustBeRole) {
    String usrName = authentication.getName();
    if (mustBeRole == null || usrName == null) {
      return -1L;
    }
    for (GrantedAuthority authority : authentication.getAuthorities()) {
      if (mustBeRole.equals(authority.getAuthority())) {
        // maybe irrelevant - we have SecurityConfig for this
        switch (mustBeRole) {
          case "ROLE_CUSTOMER":
            return Long.parseLong(usrName.substring("cust".length()));
          case "ROLE_CAB":
            return Long.parseLong(usrName.substring("cab".length()));
          case "ROLE_ADMIN":
            return Long.parseLong(usrName.substring("adm".length()));
          default:
            return -1L;
        }
      }
    }
    return -1L;
  }
}
