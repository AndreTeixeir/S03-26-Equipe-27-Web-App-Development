package com.smarttrafficflow.backend.config;

import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Refuses to start the application when the datasource password was not provided.
 * <p>
 * The property has no default value on purpose. Spring Boot does not fail on an unresolved placeholder when it
 * binds datasource properties: it would pass the literal {@code ${SPRING_DATASOURCE_PASSWORD}} to the database
 * and the failure would only show up as an authentication error. Checking the connection details also keeps
 * service connections (such as the Testcontainers database used in tests) working, since they bring their own password.
 */
@Component
class DatasourcePasswordGuard {

    DatasourcePasswordGuard(JdbcConnectionDetails connectionDetails) {
        String password = connectionDetails.getPassword();
        if (!StringUtils.hasText(password) || isUnresolvedPlaceholder(password)) {
            throw new IllegalStateException(
                    "Datasource password is not configured. Set the SPRING_DATASOURCE_PASSWORD environment variable "
                            + "(see backend/.env.example) before starting the application.");
        }
    }

    private static boolean isUnresolvedPlaceholder(String value) {
        return value.startsWith("${") && value.endsWith("}");
    }
}
