package com.timegraph;

import com.timegraph.engine.DatabaseEngine;
import com.timegraph.parser.SqlAndVcsParser;
import com.timegraph.seed.SampleDataSeeder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class TimeGraphApplication {

    @Value("${timegraph.repo.path:.timegraph}")
    private String repoPathStr;

    public static void main(String[] args) {
        SpringApplication.run(TimeGraphApplication.class, args);
    }

    @Bean
    public DatabaseEngine databaseEngine() {
        Path repoPath = Paths.get(repoPathStr);
        return new DatabaseEngine(repoPath);
    }

    @Bean
    public SqlAndVcsParser sqlAndVcsParser() {
        return new SqlAndVcsParser();
    }

    @Bean
    public CommandLineRunner initDatabase(DatabaseEngine engine, SampleDataSeeder seeder) {
        return args -> {
            seeder.seedIfEmpty(engine);
        };
    }
}
