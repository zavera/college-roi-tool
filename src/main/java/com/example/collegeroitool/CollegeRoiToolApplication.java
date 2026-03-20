package com.example.collegeroitool;
import org.springframework.web.client.RestTemplate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CollegeRoiToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollegeRoiToolApplication.class, args);
    }
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
