package com.guidepins;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 阶段一后端入口：装配地市、公共地图和南京底图三个只读模块。 */
@SpringBootApplication
public class GuidePinsApplication {
    public static void main(String[] args) {
        SpringApplication.run(GuidePinsApplication.class, args);
    }
}
