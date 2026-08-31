package com.eduscreen.app.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Membuktikan rantai perkakas berdiri: konteks Spring hidup, dan tes berjalan terhadap
 * PostgreSQL sungguhan lewat Testcontainers, bukan database dalam memori (TC-38).
 */
class PostgresSmokeTest extends PostgresTestBase {

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("TC-38: tes berjalan terhadap PostgreSQL sungguhan, bukan H2")
    void runsAgainstRealPostgres() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select version()")) {

            assertThat(result.next()).isTrue();
            String version = result.getString(1);

            assertThat(version)
                    .as("TC-38 melarang H2; tes harus menyentuh PostgreSQL sungguhan")
                    .contains("PostgreSQL");
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
    }
}
