package com.example.blockchaincamera.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.example.blockchaincamera.service.BlockchainNodeService;
import com.example.blockchaincamera.service.BlockchainWorkerService;
import com.example.blockchaincamera.service.ServerConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final BlockchainNodeService nodeService;
    private final BlockchainWorkerService workerService;
    private final ServerConfigService serverConfig;
    private final ObjectMapper objectMapper;

    public WebSocketConfig(BlockchainNodeService nodeService, 
                          BlockchainWorkerService workerService,
                          ServerConfigService serverConfig,
                          ObjectMapper objectMapper) {
        this.nodeService = nodeService;
        this.workerService = workerService;
        this.serverConfig = serverConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        
      
        if (serverConfig.isNode()) {
            registry
                .addHandler(new NodeWebSocketHandler(nodeService, objectMapper), "/ws/node")
                .setAllowedOrigins("*");
        }
        
     
        if (serverConfig.isWorker()) {
            registry
                .addHandler(new WorkerWebSocketHandler(workerService, objectMapper), "/ws/worker")
                .setAllowedOrigins("*");
        }
    }
}
