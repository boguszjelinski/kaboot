package no.kabina.kaboot.security;

import org.springframework.security.core.userdetails.User.UserBuilder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class UserDetailsServiceImp implements UserDetailsService {

  @Override
  public UserDetails loadUserByUsername(String username) {

    User user = findUserbyUername(username);

    UserBuilder builder = null;
    if (user != null) {
      builder = org.springframework.security.core.userdetails.User.withUsername(username);
      builder.password(new BCryptPasswordEncoder().encode(user.getPassword()));
      builder.roles(user.getRoles());
    } else {
      throw new UsernameNotFoundException("User not found.");
    }
    return builder.build();
  }

  private User findUserbyUername(String username) {
    if (username.startsWith("cab")) {
      return new User(username, username, "CAB");
    } else if (username.startsWith("cust")) {
      return new User(username, username, "CUSTOMER");
    } else if (username.startsWith("adm")) {
      return new User(username, username, "ADMIN");
    }
    return null;
  }
}