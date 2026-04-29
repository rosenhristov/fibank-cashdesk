package bg.fibank.cashdesk.config;

import bg.fibank.cashdesk.filter.AuthHeaderFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Security configuration for the Cash Desk Module.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Activates {@link AppProperties} so it can be injected anywhere in the context.</li>
 *   <li>Registers {@link AuthHeaderFilter} explicitly via {@link FilterRegistrationBean},
 *       giving control over URL patterns and filter order without bringing in Spring Security.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class SecurityConfig {
    /**
     * Registers the authentication filter for all {@code /api/**} paths.
     *
     * <p>Order 1 ensures it runs before any other custom filters added in later phases.</p>
     */
    @Bean
    public FilterRegistrationBean<AuthHeaderFilter> authHeaderFilterRegistration(AuthHeaderFilter authHeaderFilter) {
        FilterRegistrationBean<AuthHeaderFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(authHeaderFilter);
        registration.addUrlPatterns("/api/*");
        registration.setName("authHeaderFilter");
        registration.setOrder(1);

        return registration;
    }
}
