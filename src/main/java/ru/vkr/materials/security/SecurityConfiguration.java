package ru.vkr.materials.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import ru.vkr.materials.repository.UserRepository;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users) throws Exception {
        http.authorizeHttpRequests(access -> access
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/", "/login", "/css/**", "/js/**", "/favicon.ico", "/api/info", "/actuator/health")
                .permitAll()
                .requestMatchers("/admin/catalog/materials", "/admin/catalog/materials/**")
                .hasAnyRole("ADMIN", "STOREKEEPER")
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/reports/stocks", "/reports/stocks.csv", "/reports/movement", "/reports/movement.csv",
                        "/reports/expiry", "/reports/expiry.csv")
                .hasAnyRole("ADMIN", "STOREKEEPER")
                .requestMatchers("/inventory", "/inventory/**").hasAnyRole("ADMIN", "STOREKEEPER")
                .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login")
                        .defaultSuccessUrl("/dashboard", true).failureUrl("/login?error").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").deleteCookies("JSESSIONID"))
                .addFilterBefore(new AccountStateFilter(users), AuthorizationFilter.class);
        return http.build();
    }
}
