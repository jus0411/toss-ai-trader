package com.toss.trading;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {
    public static void main(String[] args) {
        loadEnv();
        SpringApplication.run(BackendApplication.class, args);
    }

    private static void loadEnv() {
        // Check current directory first
        File envFile = new File(".env");
        if (!envFile.exists()) {
            // Check workspace root (one level up if run from backend subdir)
            envFile = new File("../.env");
        }
        if (envFile.exists()) {
            System.out.println("Loading environment variables from: " + envFile.getAbsolutePath());
            try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eqIdx = line.indexOf('=');
                    if (eqIdx > 0) {
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        } else if (value.startsWith("'") && value.endsWith("'")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        System.setProperty(key, value);
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to load .env file: " + e.getMessage());
            }
        } else {
            System.out.println(".env file not found. Relying on system environment variables.");
        }
    }
}

