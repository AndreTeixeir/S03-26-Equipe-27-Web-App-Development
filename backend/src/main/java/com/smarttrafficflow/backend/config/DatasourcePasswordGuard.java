package com.smarttrafficflow.backend.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * Refuses to start the application when the datasource password was not provided.
 * <p>
 * The property has no default value on purpose. Spring Boot does not fail on an unresolved placeholder when it
 * binds datasource properties: it would pass the literal {@code ${SPRING_DATASOURCE_PASSWORD}} to the database
 * and the failure would only show up as a connection or authentication error. The check runs on the connection
 * details, which keeps service connections (such as the Testcontainers database used in tests) working, since they
 * bring their own password. Every {@link DataSource} depends on the check, so it runs before Flyway or JPA
 * try to reach the database.
 */
@Configuration(proxyBeanMethods = false)
class DatasourcePasswordGuard {

    private static final String CHECK_BEAN_NAME = "datasourcePasswordCheck";

    @Bean(CHECK_BEAN_NAME)
    DatasourcePasswordCheck datasourcePasswordCheck(JdbcConnectionDetails connectionDetails) {
        return new DatasourcePasswordCheck(connectionDetails.getPassword());
    }

    @Bean
    static BeanFactoryPostProcessor dataSourceDependsOnPasswordCheck() {
        return new AbstractDependsOnBeanFactoryPostProcessor(DataSource.class, CHECK_BEAN_NAME) {
        };
    }

    static class DatasourcePasswordCheck {

        DatasourcePasswordCheck(String password) {
            if (!StringUtils.hasText(password) || isUnresolvedPlaceholder(password)) {
                throw new IllegalStateException(
                        "Datasource password is not configured. Set the SPRING_DATASOURCE_PASSWORD environment "
                                + "variable (see backend/.env.example) before starting the application.");
            }
        }

        private static boolean isUnresolvedPlaceholder(String value) {
            return value.startsWith("${") && value.endsWith("}");
        }
    }
}
