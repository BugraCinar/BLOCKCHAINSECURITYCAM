package com.example.blockchaincamera.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.blockchaincamera.model.Block;
import com.example.blockchaincamera.service.BlockchainNodeService;
import com.example.blockchaincamera.service.BlockchainWorkerService;
import com.example.blockchaincamera.service.ServerConfigService;

@RestController
@RequestMapping("/api/blockchain")
public class BlockchainController {

    private final ServerConfigService serverConfig;
    private final BlockchainNodeService nodeService;
    private final BlockchainWorkerService workerService;

    public BlockchainController(ServerConfigService serverConfig,
                                BlockchainNodeService nodeService,
                                BlockchainWorkerService workerService) {
        this.serverConfig = serverConfig;
        this.nodeService = nodeService;
        this.workerService = workerService;
    }

    
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getServerInfo() {
        Map<String, Object> info = new HashMap<>();
    info.put("serverType", serverConfig.getServerName());
        info.put("serverName", serverConfig.getServerName());
        info.put("port", serverConfig.getServerPort());
        info.put("isNode", serverConfig.isNode());
        info.put("isWorker", serverConfig.isWorker());

        if (serverConfig.isNode()) {
            info.put("workerEndpoints", serverConfig.getWorkerEndpoints());
            info.put("connectedWorkers", nodeService.getConnectedWorkersCount());
        } else {
            info.put("nodeEndpoint", serverConfig.getNodeEndpoint());
        }

        return ResponseEntity.ok(info);
    }

    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getBlockchainStatus() {
        Map<String, Object> status = new HashMap<>();

        if (serverConfig.isNode()) {
            Block latestBlock = nodeService.getLatestBlock();
            status.put("latestBlock", latestBlock);
            status.put("chainLength", nodeService.getChainLength());
            status.put("connectedWorkers", nodeService.getConnectedWorkersCount());
            status.put("storageInfo", nodeService.getStorageInfo());
        } else {
            status.put("message", "Worker server - blockchain status available through Node");
            status.put("miningStatus", workerService.isMining() ? "MINING" : "IDLE");
            status.put("storageInfo", workerService.getStorageInfo());
        }

        return ResponseEntity.ok(status);
    }

    
    @PostMapping("/mining/start")
    public ResponseEntity<Map<String, String>> startMining() {
        Map<String, String> response = new HashMap<>();

        if (!serverConfig.isNode()) {
            response.put("error", "Only Node server can start mining tasks");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            nodeService.sendMiningTaskToWorkers();
            response.put("message", "Mining task sent to all connected workers");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", "Failed to send mining task: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    
    @GetMapping("/workers")
    public ResponseEntity<Map<String, Object>> getWorkers() {
        Map<String, Object> response = new HashMap<>();

        if (!serverConfig.isNode()) {
            response.put("error", "Only Node server can manage workers");
            return ResponseEntity.badRequest().body(response);
        }

        response.put("connectedWorkers", nodeService.getConnectedWorkersCount());
        response.put("workerEndpoints", serverConfig.getWorkerEndpoints());

        return ResponseEntity.ok(response);
    }

  
    @GetMapping("/worker/mining-status")
    public ResponseEntity<Map<String, Object>> getWorkerMiningStatus() {
        Map<String, Object> response = new HashMap<>();

        if (!serverConfig.isWorker()) {
            response.put("error", "Only Worker server has mining status");
            return ResponseEntity.badRequest().body(response);
        }

        response.put("isMining", workerService.isMining());
        response.put("status", workerService.isMining() ? "MINING" : "IDLE");

        return ResponseEntity.ok(response);
    }

   
    @PostMapping("/worker/stop-mining")
    public ResponseEntity<Map<String, String>> stopWorkerMining() {
        Map<String, String> response = new HashMap<>();

        if (!serverConfig.isWorker()) {
            response.put("error", "Only Worker server can stop mining");
            return ResponseEntity.badRequest().body(response);
        }

        workerService.stopMining();
        response.put("message", "Mining stopped");

        return ResponseEntity.ok(response);
    }

    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
    health.put("serverType", serverConfig.getServerName());
        health.put("timestamp", java.time.LocalDateTime.now().toString());

        return ResponseEntity.ok(health);
    }
}