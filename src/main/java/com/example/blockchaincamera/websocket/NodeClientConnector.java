package com.example.blockchaincamera.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import com.example.blockchaincamera.service.BlockchainNodeService;
import com.example.blockchaincamera.service.ServerConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;

@Component
public class NodeClientConnector {

    private final ServerConfigService serverConfig;
    private final BlockchainNodeService nodeService;
    private final ObjectMapper objectMapper;

    public NodeClientConnector(ServerConfigService serverConfig, BlockchainNodeService nodeService, ObjectMapper objectMapper) {
        this.serverConfig = serverConfig;
        this.nodeService = nodeService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void connectIfNode() {
        if (!serverConfig.isNode()) {
            return;
        }
    
    if (!serverConfig.getNodeConnectToWorkers()) {
            System.out.println("Node outbound WS connections disabled (app.node.connectToWorkers=false)");
            return;
        }
        String[] workerEndpoints = serverConfig.getWorkerEndpoints();
        if (workerEndpoints == null || workerEndpoints.length == 0) {
            return;
        }
        for (String endpoint : workerEndpoints) {
            try {
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
            new NodeWebSocketHandler(nodeService, objectMapper),
            endpoint
        );
        manager.setAutoStartup(true);
        manager.start();
        System.out.println("Node connecting to Worker endpoint (manager): " + endpoint);
            } catch (Exception e) {
                System.err.println("Node failed to connect to Worker " + endpoint + ": " + e.getMessage() +
                    " (Is the worker app running on that port?)");
            }
        }
    }
}
