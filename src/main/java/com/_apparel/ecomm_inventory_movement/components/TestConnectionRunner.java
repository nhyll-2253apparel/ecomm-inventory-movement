package com._apparel.ecomm_inventory_movement.components;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

//@Component
//@RequiredArgsConstructor
//@Slf4j
public class TestConnectionRunner {//implements CommandLineRunner {

//    private final DataSource dataSource;
//
//    @Override
//    public void run(String... args) throws Exception {
//
//        try (Connection conn = dataSource.getConnection()) {
//            log.info("Connected successfully.");
//
//            PreparedStatement ps =
//                    conn.prepareStatement(
//                            "select sysdate from dual");
//
//            ResultSet rs = ps.executeQuery();
//
//            while (rs.next()) {
//                log.info("DB Time: {}", rs.getTimestamp(1));
//            }
//        }
//    }
}