package no.kabina.kaboot.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public class AuthUtils {

    public static Long getUserId(Authentication authentication, String mustBeRole) {
        String usrName = authentication.getName();
        if (mustBeRole == null || usrName == null) {
            return -1L;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (mustBeRole.equals(authority.getAuthority())) { // maybe irrelevant - we have SecurityConfig for this
                switch (mustBeRole) {
                    case "ROLE_CUSTOMER":
                        return Long.parseLong(usrName.substring("cust".length()));
                    case "ROLE_CAB":
                        return Long.parseLong(usrName.substring("cab".length()));
                }
            }
        }
        return -1L;
    }
}
