package com.guidepins.city.controller;

import com.guidepins.city.domain.City;
import com.guidepins.city.service.CityService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 访客入口：列出已接入底图的地市及各地市的地图包路径、浏览范围。 */
@RestController
@RequestMapping("/api/cities")
public class CityController {
    private final CityService cities;

    public CityController(CityService cities) {
        this.cities = cities;
    }

    @GetMapping
    List<City> list() {
        return cities.list();
    }
}
