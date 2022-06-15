package no.kabina.kaboot.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

  private final String CUSTOMER = "CUSTOMER";

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.authorizeRequests()
                .antMatchers("/cabs").hasAnyRole("CAB", CUSTOMER)
                .antMatchers("/stops").hasAnyRole("CAB", CUSTOMER)
                .antMatchers("/orders").hasRole("CUSTOMER")
                .antMatchers("/legs").hasRole("CAB")
                .antMatchers("/routes").hasRole("CAB")
                .antMatchers("/dispatch").permitAll() //hasRole("ADMIN")
                .antMatchers("/stats").hasRole("ADMIN")
                .antMatchers("/api-docs").permitAll()
            .anyRequest().authenticated()
            .and().cors().and().csrf().disable()
            .httpBasic()
            .and()
            .logout().permitAll().logoutSuccessUrl("/login")
            .and()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
  }

  @Override
  protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.userDetailsService(userDetailsService()).passwordEncoder(passwordEncoder());
  }

  @Override
  @Bean
  public UserDetailsService userDetailsService() {
    return new UserDetailsServiceImp();
  }

  @Bean
  public BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}