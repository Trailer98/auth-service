package com.selflearning.authservice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan({
        "com.selflearning.authservice.application.mapper",
        "com.selflearning.authservice.auth.mapper",
        "com.selflearning.authservice.permission.mapper",
        "com.selflearning.authservice.role.mapper"
})
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

}
