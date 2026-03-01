package com.example.blockchaincamera.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import com.example.blockchaincamera.service.BlockchainWorkerService;
import com.example.blockchaincamera.service.ServerConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;

@Component
public class WorkerClientConnector {

    private final ServerConfigService serverConfig;
    private final BlockchainWorkerService workerService;
    private final ObjectMapper objectMapper;

    public WorkerClientConnector(ServerConfigService serverConfig, BlockchainWorkerService workerService, ObjectMapper objectMapper) {
        this.serverConfig = serverConfig;
        this.workerService = workerService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void connectIfWorker() {
        if (!serverConfig.isWorker()) {
            return;
        }
        String nodeEndpoint = serverConfig.getNodeEndpoint();
        if (nodeEndpoint == null) {
            return;
        }
    
    WebSocketContainer clientContainer = ContainerProvider.getWebSocketContainer();
    try {
        clientContainer.setDefaultMaxTextMessageBufferSize(2 * 1024 * 1024);
        clientContainer.setDefaultMaxBinaryMessageBufferSize(2 * 1024 * 1024);
    } catch (Throwable t) {
        System.err.println("WS client buffer config failed: " + t.getMessage());
    }
    WebSocketClient client = new StandardWebSocketClient(clientContainer);
    WebSocketConnectionManager manager = new WebSocketConnectionManager(
        client,
        new WorkerWebSocketHandler(workerService, objectMapper),
        nodeEndpoint
    );
    manager.setAutoStartup(true);
    manager.start();
    System.out.println("Worker connecting to Node endpoint: " + nodeEndpoint);
    }
}
