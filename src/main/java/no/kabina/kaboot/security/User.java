package no.kabina.kaboot.security;

public class User {
  private String username;
  private String password;
  private String[] roles;

  /** constructor.

   * @param username usr
   * @param password pass
   * @param roles roles
   */
  public User(String username, String password, String... roles) {
    this.username = username;
    this.password = password;
    this.roles = roles;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String[] getRoles() {
    return roles;
  }

  public void setRoles(String[] roles) {
    this.roles = roles;
  }
}