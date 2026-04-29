package bg.fibank.cashdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Cash Desk Module.
 *
 * <p>Run locally with:</p>
 * <pre>
 *   mvn clean package
 *   java -jar target/cash-desk-module-1.0.0.jar
 * </pre>
 *
 * <p>All API endpoints require the {@code FIB-X-AUTH} request header.</p>
 */
@SpringBootApplication
public class CashdeskApplication {

	public static void main(String[] args) {
		SpringApplication.run(CashdeskApplication.class, args);
	}

}
