package com.guidepins;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 后端入口：装配认证、地市、地图和底图模块，并定时清理过期会话。 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class GuidePinsApplication {
    public static void main(String[] args) {
        SpringApplication.run(GuidePinsApplication.class, args);
    }
}
