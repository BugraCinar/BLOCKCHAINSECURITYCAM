package com.example.blockchaincamera.service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.blockchaincamera.dto.BlockAcceptedData;
import com.example.blockchaincamera.dto.ErrorInvalidBlockData;
import com.example.blockchaincamera.dto.LatestBlockResponseData;
import com.example.blockchaincamera.dto.MessageType;
import com.example.blockchaincamera.dto.NewBlockData;
import com.example.blockchaincamera.dto.RecentBlocksResponseData;
import com.example.blockchaincamera.model.Block;
import com.example.blockchaincamera.model.Blockchain;
import com.example.blockchaincamera.util.HashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

@Service
public class BlockchainWorkerService {
    
    private final Blockchain blockchain;
    private final ImageProcessingService imageService;
    private final ObjectMapper objectMapper;
    @Setter
    @Getter
    private WebSocketSession nodeSession;
    
    private final ServerConfigService serverConfigService;

    // Test switch: when true, compare current image against itself (should yield ~100%)
    @Value("${app.image.similarity.loopback:false}")
    private boolean loopbackSameImage;

    public BlockchainWorkerService(ImageProcessingService imageService, ObjectMapper objectMapper, ServerConfigService serverConfigService) {
        this.imageService = imageService;
        this.objectMapper = objectMapper;
        this.serverConfigService = serverConfigService;
        this.blockchain = new Blockchain(serverConfigService.getDifficulty());
    }

    public String getStorageInfo() {
        return blockchain.getStorageInfo();
    }

    
    public void handleBinaryImage(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) return;
        this.currentImageBytes = bytes;
        
        Block latest = blockchain.getLatestBlock();
        if (latest != null && latest.getImageBase64() != null) {
            try {
                com.example.blockchaincamera.util.Base64ImageUtil.Encoding enc =
                    com.example.blockchaincamera.util.Base64ImageUtil.fromString(latest.getImageEncoding());
                this.previousImageBytes = com.example.blockchaincamera.util.Base64ImageUtil.decode(latest.getImageBase64(), enc);
            } catch (IllegalArgumentException iae) {
                this.previousImageBytes = null;
            }
        } else {
            this.previousImageBytes = null;
        }
        if (pendingHeader != null) {
            this.currentMiningTask = new com.example.blockchaincamera.service.BlockchainNodeService.MiningTaskMessage(
                pendingHeader.getIndex(),
                pendingHeader.getTimestamp(),
                pendingHeader.getPrevHash(),
                pendingHeader.getDataString(),
                pendingHeader.getAnalysisResult(),
                pendingHeader.getImageSimilarityScore(),
                null,
                null
            );
            pendingHeader = null;
            stopMining();
            startMining();
        }
    }


    public void handleLatestBlockResponse(com.example.blockchaincamera.dto.WebSocketMessage<?> wsMessage) throws Exception {
        LatestBlockResponseData responseData = objectMapper.convertValue(wsMessage.getData(), LatestBlockResponseData.class);
        Block latestBlock = responseData.getBlock();

        System.out.println("Worker received latest block: Index " + latestBlock.getIndex() +
                ", Hash: " + latestBlock.getHash().substring(0, 10) + "...");

    
    blockchain.syncWithBlocks(java.util.List.of(latestBlock));
    }


    public void handleRecentBlocksResponse(com.example.blockchaincamera.dto.WebSocketMessage<?> wsMessage) throws Exception {
        RecentBlocksResponseData responseData = objectMapper.convertValue(wsMessage.getData(), RecentBlocksResponseData.class);
        System.out.println("Worker received recent blocks: " + responseData.getBlocks().size() + " blocks");

    blockchain.syncWithBlocks(responseData.getBlocks());
    }


    public void handleBlockAccepted(com.example.blockchaincamera.dto.WebSocketMessage<?> wsMessage) throws Exception {
        BlockAcceptedData acceptedData = objectMapper.convertValue(wsMessage.getData(), BlockAcceptedData.class);
        System.out.println("Worker received block accepted: Index " + acceptedData.getBlockIndex() +
                ", Reward: " + acceptedData.getReward() +
                ", Winner: " + acceptedData.getWorkerId());


        stopMining();
    }


    public void handleErrorInvalidBlock(com.example.blockchaincamera.dto.WebSocketMessage<?> wsMessage) throws Exception {
        ErrorInvalidBlockData errorData = objectMapper.convertValue(wsMessage.getData(), ErrorInvalidBlockData.class);
        System.err.println("Worker received error: " + errorData.getReason() +
                " (Index: " + errorData.getBlockIndex() + ")");


        stopMining();
    }


    public void handleMiningTask(com.example.blockchaincamera.dto.WebSocketMessage<?> wsMessage) throws Exception {

        com.example.blockchaincamera.service.BlockchainNodeService.MiningTaskMessage taskMessage = 
            objectMapper.convertValue(wsMessage.getData(), com.example.blockchaincamera.service.BlockchainNodeService.MiningTaskMessage.class);
        
        this.currentMiningTask = taskMessage;
        String currBase64 = taskMessage.getCurrentImageBase64();
        String prevBase64 = taskMessage.getPreviousImageBase64();
    this.currentImageBytes = currBase64 != null ? java.util.Base64.getDecoder().decode(currBase64) : null;
    this.previousImageBytes = prevBase64 != null ? java.util.Base64.getDecoder().decode(prevBase64) : null;
        
        System.out.println("Worker received mining task. Current bytes: " + (currentImageBytes == null ? 0 : currentImageBytes.length) +
            ", Previous bytes: " + (previousImageBytes == null ? 0 : previousImageBytes.length));


        stopMining();


    startMining();
    }


    @Getter
    public volatile boolean isMining = false;
    private CompletableFuture<Void> miningTask;
    private byte[] currentImageBytes;
    private byte[] previousImageBytes;
    private com.example.blockchaincamera.service.BlockchainNodeService.MiningTaskMessage currentMiningTask;
    
    private volatile com.example.blockchaincamera.dto.MiningTaskHeader pendingHeader;

    public void startMining() {
        isMining = true;
        miningTask = CompletableFuture.runAsync(this::performMining);
    }

    public void stopMining() {
        isMining = false;
        if (miningTask != null) {
            miningTask.cancel(true);
        }
    }

    private void performMining() {
        try {
            System.out.println("Worker started mining...");

            int nextIndex = currentMiningTask.getIndex();
            String prevHash = currentMiningTask.getPrevHash();
            LocalDateTime timestamp = currentMiningTask.getTimestamp();

            ImageProcessingService.ImageComparisonResult comparisonResult;

            // Loopback mode: force previous = current to verify 100% similarity
            if (loopbackSameImage && currentImageBytes != null) {
                previousImageBytes = currentImageBytes;
            }
            if (previousImageBytes == null) {
                comparisonResult = imageService.compareImageBytes(currentImageBytes);
            } else {
                comparisonResult = imageService.compareTwoImagesBytes(previousImageBytes, currentImageBytes);
            }
            // Always log components; will print a helpful line even if one image is null
            imageService.debugSimilarityComponents(previousImageBytes, currentImageBytes);
        
            String dataString = "test asamasi";

            // Mining loop devamı...
            int nonce = 0;
            String hash;
            String difficultyPrefix = "0".repeat(Math.max(1, serverConfigService.getDifficulty()));
        
            while (isMining) {
                final String imageDigestComputed = (currentImageBytes == null ? "" : com.example.blockchaincamera.util.HashUtil.applyHash(currentImageBytes));
                String dataToHash = nextIndex + timestamp.toString() + prevHash + nonce +
                        dataString + comparisonResult.analysisResult() + comparisonResult.similarityScore() +
                        imageDigestComputed;
                hash = HashUtil.applyHash(dataToHash);

                if (hash.startsWith(difficultyPrefix)) {
                    System.out.println("Worker found valid hash! Nonce: " + nonce + ", Hash: " + hash.substring(0, 10) + "...");

                    com.example.blockchaincamera.util.Base64ImageUtil.Encoding sendEnc = com.example.blockchaincamera.util.Base64ImageUtil.Encoding.DEFLATE;
                    String currImageB64ForBlock = (currentImageBytes == null ? null :
                        com.example.blockchaincamera.util.Base64ImageUtil.encode(currentImageBytes, sendEnc, true));
                    sendMinedBlockToNode(nextIndex, timestamp, prevHash, hash, nonce,
                        dataString, comparisonResult.analysisResult(), comparisonResult.similarityScore(), currImageB64ForBlock, imageDigestComputed, sendEnc.name());
                    break;
                }

                nonce++;

                if (nonce % 10000 == 0) {
                    System.out.println("Worker mining progress: " + nonce + " nonces tried...");
                }

                if (nonce > 1000000) {
                    System.err.println("Worker mining timeout, stopping...");
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("Worker mining error: " + e.getMessage());
        } finally {
            isMining = false;
        }
    }

    private void sendMinedBlockToNode(int index, LocalDateTime timestamp, String prevHash,
                                      String hash, int nonce, String dataString,
                                      String analysisResult, double imageSimilarityScore,
                                      String imageBase64, String imageDigest, String imageEncoding) throws Exception {

        if (nodeSession == null || !nodeSession.isOpen()) {
            System.err.println("Worker: No connection to Node");
            return;
        }

    NewBlockData blockData = new NewBlockData(index, timestamp, prevHash, hash, nonce,
        dataString, analysisResult, imageSimilarityScore, imageBase64, imageEncoding, imageDigest);

        com.example.blockchaincamera.dto.WebSocketMessage<NewBlockData> message =
                new com.example.blockchaincamera.dto.WebSocketMessage<>(MessageType.BLOCK_FOUND, blockData);

        String messageJson = objectMapper.writeValueAsString(message);
        nodeSession.sendMessage(new TextMessage(messageJson));

        System.out.println("Worker sent mined block to Node: Index " + index +
                ", Analysis: " + analysisResult + 
                ", Similarity: " + String.format("%.2f", imageSimilarityScore) + "%");
    }

    
    public void handleMiningTaskHeader(com.example.blockchaincamera.dto.WebSocketMessage<?> wsMessage) throws Exception {
        com.example.blockchaincamera.dto.MiningTaskHeader header = objectMapper.convertValue(wsMessage.getData(), com.example.blockchaincamera.dto.MiningTaskHeader.class);
        this.pendingHeader = header;
        stopMining();
        System.out.println("Worker received task header: index=" + header.getIndex() + ", imageId=" + header.getImageId());
    }

    
    public void handleMiningTaskHeader(Object wsMessageObj) throws Exception {
        com.example.blockchaincamera.dto.WebSocketMessage<?> wsMessage = objectMapper.convertValue(wsMessageObj, com.example.blockchaincamera.dto.WebSocketMessage.class);
        handleMiningTaskHeader(wsMessage);
    }

   





}