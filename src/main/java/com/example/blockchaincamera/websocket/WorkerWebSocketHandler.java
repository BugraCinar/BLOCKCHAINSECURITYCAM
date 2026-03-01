package com.example.blockchaincamera.websocket;

import org.springframework.lang.NonNull;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import com.example.blockchaincamera.dto.MessageType;
import com.example.blockchaincamera.service.BlockchainWorkerService;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WorkerWebSocketHandler extends AbstractWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final BlockchainWorkerService workerService;

    public WorkerWebSocketHandler(BlockchainWorkerService workerService, ObjectMapper objectMapper) {
        this.workerService = workerService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        workerService.setNodeSession(session);
        System.out.println("Worker connected to Node: " + session.getId());
        
        
        requestLatestBlock(session);
    }

    private void requestLatestBlock(@NonNull WebSocketSession session) throws Exception {
        com.example.blockchaincamera.dto.WebSocketMessage<Object> request = 
            new com.example.blockchaincamera.dto.WebSocketMessage<>(MessageType.REQUEST_LATEST_BLOCK, null);
        
        String requestJson = objectMapper.writeValueAsString(request);
        session.sendMessage(new TextMessage(requestJson));
        System.out.println("Worker requested latest block from Node");
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        workerService.setNodeSession(null);
    System.out.println("Worker disconnected from Node: " + session.getId() + " (code=" + status.getCode() + ", reason=" + status.getReason() + ")");
    }
    @Override
    public void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        if (!session.isOpen()) {
            System.err.println("Worker received message on closed session; ignoring.");
            return;
        }
        String payload = message.getPayload();
        System.out.println("Worker received message: " + payload);

        try {
            com.example.blockchaincamera.dto.WebSocketMessage<?> wsMsg =
                    objectMapper.readValue(payload, com.example.blockchaincamera.dto.WebSocketMessage.class);

            handleWebSocketMessage(wsMsg);

    } catch (Exception e) {
            System.err.println("Worker message processing error: " + e.getMessage());
        }
    }

    @Override
    public void handleBinaryMessage(@NonNull WebSocketSession session, @NonNull BinaryMessage message) {
        if (!session.isOpen()) {
            System.err.println("Worker received binary on closed session; ignoring.");
            return;
        }
        try {
            byte[] bytes = message.getPayload().array();
            workerService.handleBinaryImage(bytes);
        } catch (Exception e) {
            System.err.println("Worker binary message error: " + e.getMessage());
        }
    }

    private void handleWebSocketMessage(com.example.blockchaincamera.dto.WebSocketMessage<?> wsMessage) throws Exception {
        switch (wsMessage.getType()) {
            case LATEST_BLOCK_RESPONSE -> workerService.handleLatestBlockResponse(wsMessage);
            case RECENT_BLOCKS_RESPONSE -> workerService.handleRecentBlocksResponse(wsMessage);
            case BLOCK_ACCEPTED -> workerService.handleBlockAccepted(wsMessage);
            case ERROR_INVALID_BLOCK -> workerService.handleErrorInvalidBlock(wsMessage);

            case MINING_TASK_HEADER -> workerService.handleMiningTaskHeader((Object) wsMessage);
            default -> System.err.println("Unknown message type: " + wsMessage.getType());
        }
    }

}
