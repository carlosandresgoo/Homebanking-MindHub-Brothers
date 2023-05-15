package com.mindhub.homebanking.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@EnableWebSecurity
@Configuration
public class WebAuthorization {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.authorizeRequests()
                .antMatchers("/web/pages/services.html","/web/pages/contact.html","/web/pages/aboutUs.html","/index.html","/web/pages/signon.html", "/web/pages/register.html","/web/assets/**","/web/css/**","/web/js/register.js", "/web/js/signon.js","/web/js/index.js","/web/js/aboutUs.js").permitAll()
                .antMatchers(HttpMethod.POST,"/api/login", "/api/logout","/api/clients").permitAll()
                .antMatchers("/admin/manager.html","/h2-console/**","api/clients").hasAuthority("ADMIN")
                .antMatchers("/api/clients/current/accounts/{id}","/api/accounts/{id}","/api/cards/{id}","/api/clients/current/accounts", "/api/clients/current","/api/loans","/api/current/loans").hasAuthority("CLIENT")
                .antMatchers(HttpMethod.POST,"/api/clients/current/accounts","/api/clients/current/cards","/api/clients/current/transactions", "/api/loans","/api/current/loans" ).hasAnyAuthority("CLIENT", "ADMIN")
                .antMatchers("/web/js/**").hasAnyAuthority("CLIENT", "ADMIN")
                .antMatchers("/web/pages/**").hasAnyAuthority("CLIENT", "ADMIN")
                .antMatchers("/web/pages/payloans.html").hasAuthority("CLIENT")
                .antMatchers(HttpMethod.PUT,"/api/cards/{id}", "/api/accounts/{id}" ).hasAnyAuthority("CLIENT", "ADMIN")
                .anyRequest().denyAll();


        http.formLogin()
                .usernameParameter("email")
                .passwordParameter("password")
                .loginPage("/api/login");

        http.logout().logoutUrl("/api/logout").deleteCookies("JSESSIONID");

        http.csrf().disable();

        //disabling frameOptions so h2-console can be accessed

        http.headers().frameOptions().disable();

        // if user is not authenticated, just send an authentication failure response

        http.exceptionHandling().authenticationEntryPoint((req, res, exc) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED));

        // if login is successful, just clear the flags asking for authentication

        http.formLogin().successHandler((req, res, auth) -> clearAuthenticationAttributes(req));

        // if login fails, just send an authentication failure response

        http.formLogin().failureHandler((req, res, exc) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED));

        // if logout is successful, just send a success response

        http.logout().logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler());

//        http.logout().logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler())
//                .and()
//                .sessionManagement()
//                .invalidSessionUrl("/web/pages/signon.html")
//                .sessionFixation().none()
//                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
//                .maximumSessions(1);

                 return http.build();
    }
    private void clearAuthenticationAttributes(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        }
    }

}