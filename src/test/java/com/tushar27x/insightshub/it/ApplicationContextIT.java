package com.tushar27x.insightshub.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole app starts against the containers: Flyway migrates an empty database and Hibernate's
 * {@code ddl-auto: validate} accepts the result. Replaces the old {@code contextLoads()}, which
 * booted against Neon (B23).
 */
@IntegrationTest
class ApplicationContextIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoadsAndFlywayBuildsTheV2Schema() {
        Integer migrations = jdbc.queryForObject(
                "SELECT count(*) FROM v2.flyway_schema_history WHERE success", Integer.class);
        assertThat(migrations).isGreaterThanOrEqualTo(4);

        Integer publicTables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'", Integer.class);
        assertThat(publicTables).as("v2 never writes to the public schema (v1 lives there)").isZero();
    }
}
