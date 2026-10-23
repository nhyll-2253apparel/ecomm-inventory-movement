package com._apparel.ecomm_inventory_movement;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class OracleTest {

    public static void main(String[] args) throws Exception {
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", requiredEnvironmentVariable("ORACLE_DB_USERNAME"));
        connectionProperties.setProperty("password", requiredEnvironmentVariable("ORACLE_DB_PASSWORD"));
        connectionProperties.setProperty("oracle.net.tns_admin", requiredEnvironmentVariable("ORACLE_TNS_ADMIN"));
        connectionProperties.setProperty(
                "oracle.net.ssl_version",
                System.getenv().getOrDefault("ORACLE_SSL_VERSION", "1.2"));

        try (Connection connection = DriverManager.getConnection(
                "jdbc:oracle:thin:@LPOOL",
                connectionProperties)) {
            System.out.println("Connected!");
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set the " + name + " environment variable before running OracleTest.");
        }
        return value;
    }
}