package com.example.blockchaincamera.websocket;

import java.io.IOException;

import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.example.blockchaincamera.dto.ErrorInvalidBlockData;
import com.example.blockchaincamera.dto.LatestBlockResponseData;
import com.example.blockchaincamera.dto.MessageType;
import com.example.blockchaincamera.model.Block;
import com.example.blockchaincamera.service.BlockchainNodeService;
import com.fasterxml.jackson.databind.ObjectMapper;

public class NodeWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final BlockchainNodeService nodeService;

    public NodeWebSocketHandler(BlockchainNodeService nodeService, ObjectMapper objectMapper) {
        this.nodeService = nodeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        nodeService.addWorkerSession(session);
        System.out.println("Worker connected to Node: " + session.getId());

    
        try {
            java.util.List<com.example.blockchaincamera.model.Block> recentBlocks = nodeService.getRecentBlocks(50);
            com.example.blockchaincamera.dto.RecentBlocksResponseData payload =
                new com.example.blockchaincamera.dto.RecentBlocksResponseData(
                    recentBlocks,
                    nodeService.getChainLength(),
                    recentBlocks.isEmpty() ? -1 : recentBlocks.get(0).getIndex(),
                    recentBlocks.isEmpty() ? -1 : recentBlocks.get(recentBlocks.size() - 1).getIndex()
                );
            com.example.blockchaincamera.dto.WebSocketMessage<com.example.blockchaincamera.dto.RecentBlocksResponseData> msg =
                new com.example.blockchaincamera.dto.WebSocketMessage<>(
                    com.example.blockchaincamera.dto.MessageType.RECENT_BLOCKS_RESPONSE,
                    payload
                );
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
                } catch (IllegalStateException ise) {
                    System.err.println("Session closed while sending initial sync: " + ise.getMessage());
                }
            }
            System.out.println("Initial sync sent to worker " + session.getId() + 
                               ": " + recentBlocks.size() + " blocks");
    } catch (IOException e) {
            System.err.println("Failed to send initial sync to worker: " + e.getMessage());
        }
    }

    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Mesaj alındı: " + payload);

        try {
            
            com.example.blockchaincamera.dto.WebSocketMessage<?> wsMsg = 
                objectMapper.readValue(payload, com.example.blockchaincamera.dto.WebSocketMessage.class);
            
            
            String response = handleWebSocketMessage(session, wsMsg);
            
            
            if (response != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(response));
                } catch (IllegalStateException ise) {
                    System.err.println("Session closed before reply could be sent: " + ise.getMessage());
                }
            }
            
        } catch (Exception e) {
            System.err.println("Mesaj işleme hatası: " + e.getMessage());
            
            ErrorInvalidBlockData errorData = new ErrorInvalidBlockData(
                "Message Processing Error", -1, e.getMessage()
            );
            com.example.blockchaincamera.dto.WebSocketMessage<ErrorInvalidBlockData> errorMessage = 
                new com.example.blockchaincamera.dto.WebSocketMessage<>(
                    MessageType.ERROR_INVALID_BLOCK, errorData
                );
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorMessage)));
                } catch (IllegalStateException ise) {
                    System.err.println("Session closed while sending error: " + ise.getMessage());
                }
            }
        }
    }

    private String handleWebSocketMessage(WebSocketSession session, com.example.blockchaincamera.dto.WebSocketMessage<?> wsMessage) throws Exception {
        switch (wsMessage.getType()) {
            case REQUEST_LATEST_BLOCK -> {
                return handleLatestBlockRequest();
            }
                
            case REQUEST_RECENT_BLOCKS -> {
                return handleRecentBlocksRequest();
            }
                
            case BLOCK_FOUND -> {
              
                nodeService.handleWorkerMessage(objectMapper.writeValueAsString(wsMessage), session);
                return null;
            }
                
            default -> throw new IllegalArgumentException("Unknown message type: " + wsMessage.getType());
        }
    }

    private String handleLatestBlockRequest() throws Exception {
        Block latestBlock = nodeService.getLatestBlock();
        if (latestBlock == null) {
            throw new IllegalStateException("No blocks in blockchain");
        }
        
        LatestBlockResponseData responseData = new LatestBlockResponseData(latestBlock, nodeService.getChainLength());
        com.example.blockchaincamera.dto.WebSocketMessage<LatestBlockResponseData> response = 
            new com.example.blockchaincamera.dto.WebSocketMessage<>(MessageType.LATEST_BLOCK_RESPONSE, responseData);
        
        return objectMapper.writeValueAsString(response);
    }

    private String handleRecentBlocksRequest() throws Exception {
        
        return handleLatestBlockRequest();
    }

    @SuppressWarnings("UseSpecificCatch")
    

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
    nodeService.removeWorkerSession(session);
    System.out.println("Worker disconnected from Node: " + session.getId() + " (code=" + status.getCode() + ", reason=" + status.getReason() + ")");
    }
}
