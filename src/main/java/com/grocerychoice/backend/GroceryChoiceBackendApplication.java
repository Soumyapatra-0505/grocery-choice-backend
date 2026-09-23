package com.grocerychoice.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * Grocery Choice Backend REST API Application.
 *
 * Configured with Spring Data JPA and MySQL database connectivity.
 */
@SpringBootApplication
public class GroceryChoiceBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(GroceryChoiceBackendApplication.class, args);
    }

}
