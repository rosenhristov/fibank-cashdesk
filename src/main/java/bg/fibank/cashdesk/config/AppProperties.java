package bg.fibank.cashdesk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed binding for all {@code app.*} properties defined in application.properties.
 *
 * <p>Registered via {@code @EnableConfigurationProperties} in {@link SecurityConfig}.
 * IDE autocomplete is provided by the {@code spring-boot-configuration-processor} dependency.</p>
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Auth auth = new Auth();
    private final Data data = new Data();

    @lombok.Data
    public static class Auth {

        /**
         * API key that must be supplied in the {@code FIB-X-AUTH} request header.
         */
        private String apiKey;
    }

    @lombok.Data
    public static class Data {

        /**
         * Path to the pipe-delimited file storing current cashier balances and denominations.
         */
        private String balancesFile;

        /**
         * Path to the pipe-delimited append-only transaction log.
         */
        private String transactionsFile;
    }
}
