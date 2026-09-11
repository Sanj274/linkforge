package com.linkforge.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LinkforgeApplication {

	public static void main(String[] args) {
		// Some Windows/OS locales report a legacy IANA zone id (e.g. "Asia/Calcutta"
		// instead of "Asia/Kolkata"). The PostgreSQL JDBC driver sends the JVM's
		// default timezone straight to the server during connection setup, and
		// Postgres rejects the legacy name outright. Forcing UTC here sidesteps
		// that entirely, regardless of the host machine's locale.
		System.setProperty("user.timezone", "UTC");
		SpringApplication.run(LinkforgeApplication.class, args);
	}

}