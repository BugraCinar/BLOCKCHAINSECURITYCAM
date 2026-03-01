package com.example.blockchaincamera.service;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class ServerConfigService {
    
    public enum ServerType {
        NODE, WORKER_1, WORKER_2
    }
    
    private final ServerType serverType;
    private final int serverPort;
    private final int difficulty;
    private final int miningIntervalSeconds;
    private final boolean nodeConnectToWorkers;
    
    public ServerConfigService(Environment environment) {

    String portStr = environment.getProperty("server.port", "8080");
        this.serverPort = Integer.parseInt(portStr);
    String diffStr = environment.getProperty("app.difficulty", "5");
    this.difficulty = Integer.parseInt(diffStr);
    String intervalStr = environment.getProperty("app.mining.intervalSeconds", "15");
    this.miningIntervalSeconds = Integer.parseInt(intervalStr);
    String connectToWorkersStr = environment.getProperty("app.node.connectToWorkers", "true");
    this.nodeConnectToWorkers = Boolean.parseBoolean(connectToWorkersStr);
        

        this.serverType = switch (serverPort) {
            case 8080 -> ServerType.NODE;
            case 8081 -> ServerType.WORKER_1;
            case 8082 -> ServerType.WORKER_2;
            default -> ServerType.NODE;
        };
        
        System.out.println("Server initialized as: " + serverType + " on port: " + serverPort);
    }

    public boolean isNode() {
        return serverType == ServerType.NODE;
    }
    
    public boolean isWorker() {
        return serverType == ServerType.WORKER_1 || serverType == ServerType.WORKER_2;
    }
    
    public String getServerName() {
        return serverType.name();
    }
    public int getDifficulty() {
        return difficulty;
    }
    public int getMiningIntervalSeconds() {
        return miningIntervalSeconds;
    }
    public boolean getNodeConnectToWorkers() {
        return nodeConnectToWorkers;
    }
    public int getServerPort() {
        return serverPort;
    }
    

    public String[] getWorkerEndpoints() {
        if (isNode()) {
            return new String[]{
                "ws://localhost:8081/ws/worker",
                "ws://localhost:8082/ws/worker"
            };
        }
        return new String[0];
    }
    

    public String getNodeEndpoint() {
        if (isWorker()) {
            return "ws://localhost:8080/ws/node";
        }
        return null;
    }
}
