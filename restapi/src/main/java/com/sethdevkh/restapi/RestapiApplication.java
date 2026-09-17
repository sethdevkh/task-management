package com.sethdevkh.restapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.sethdevkh.restapi.security.CorsProperties;
import com.sethdevkh.restapi.security.JwtProperties;
import com.sethdevkh.restapi.seed.SeedProperties;

@SpringBootApplication
@EnableConfigurationProperties({ SeedProperties.class, JwtProperties.class, CorsProperties.class })
public class RestapiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestapiApplication.class, args);
    }

}
