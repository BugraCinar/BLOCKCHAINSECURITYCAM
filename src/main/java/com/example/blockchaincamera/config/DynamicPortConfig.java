package com.example.blockchaincamera.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import com.example.blockchaincamera.service.ServerConfigService;

@Component
public class DynamicPortConfig implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {

    @Autowired
    private ServerConfigService serverConfig;

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        factory.setPort(serverConfig.getServerPort());
        System.out.println("Server configured to run on port: " + serverConfig.getServerPort());
    }
}
