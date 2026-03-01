package com.example.blockchaincamera.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import com.example.blockchaincamera.dto.MessageType;
import com.example.blockchaincamera.model.Block;
import com.example.blockchaincamera.model.Blockchain;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.Getter;
import lombok.Setter;

@Service
public class BlockchainNodeService {
    
    private final Blockchain blockchain;
    private final ImageProcessingService imageService;
    private final List<WebSocketSession> connectedWorkers = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
   
    private final java.util.concurrent.ConcurrentHashMap<Integer, java.util.List<CandidateSubmission>> pendingSubmissions = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Integer, Long> firstSubmissionTime = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Integer> scheduledConsensusChecks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final long MAX_SIMILARITY_WAIT_MILLIS = 2000; 
    private static final double SIMILARITY_TOLERANCE = 10.0; 
    private final ReentrantLock consensusLock = new ReentrantLock();
   
    private static final Path DATA_DIR = Path.of("blockchain-data");
    private static final Path DATA_FILE = DATA_DIR.resolve("blockchain.json");
    private final ObjectMapper fileMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ReentrantLock ioLock = new ReentrantLock();
    
    private final ServerConfigService serverConfigService;

    public BlockchainNodeService(ImageProcessingService imageService, ObjectMapper objectMapper, ServerConfigService serverConfigService) {
        this.imageService = imageService;
        this.objectMapper = objectMapper;
        this.serverConfigService = serverConfigService;
    this.blockchain = new Blockchain(this.serverConfigService.getDifficulty());
    System.out.println("Node difficulty set to: " + this.serverConfigService.getDifficulty());

        
        List<Block> persisted = loadFromDisk();
        if (!persisted.isEmpty()) {
            System.out.println("Loading blockchain from disk: " + persisted.size() + " blocks");
            boolean ok = this.blockchain.replaceWithBlocks(persisted);
            if (!ok) {
                System.err.println("Persisted blockchain snapshot invalid; keeping in-memory genesis");
            }
        }
      
        saveToDisk(this.blockchain.getAllBlocks());

        startMiningTaskScheduler();
    }
    
    public void addWorkerSession(WebSocketSession session) {
        connectedWorkers.add(session);
        System.out.println("Worker connected: " + session.getId());
    }
    
    public void removeWorkerSession(WebSocketSession session) {
        connectedWorkers.remove(session);
        System.out.println("Worker disconnected: " + session.getId());
    }
    
    public int getConnectedWorkersCount() {
        return connectedWorkers.size();
    }
    
    public String getStorageInfo() {
        return blockchain.getStorageInfo();
    }
    
    public Block getLatestBlock() {
        return blockchain.getLatestBlock();
    }
    
    public int getChainLength() {
        return blockchain.getChainLength();
    }
    
    public List<Block> getRecentBlocks(int count) {
        return blockchain.getRecentBlocks(count);
    }
    
   
    
    public void broadcastLatestBlock() {
        if (connectedWorkers.isEmpty()) {
            return;
        }
        try {
            Block latest = getLatestBlock();
            if (latest == null) return;

            com.example.blockchaincamera.dto.RecentBlocksResponseData payload =
                new com.example.blockchaincamera.dto.RecentBlocksResponseData(
                    java.util.List.of(latest),
                    getChainLength(),
                    latest.getIndex(),
                    latest.getIndex()
                );
            com.example.blockchaincamera.dto.WebSocketMessage<com.example.blockchaincamera.dto.RecentBlocksResponseData> msg =
                new com.example.blockchaincamera.dto.WebSocketMessage<>(MessageType.RECENT_BLOCKS_RESPONSE, payload);
            String json = objectMapper.writeValueAsString(msg);
            for (WebSocketSession session : connectedWorkers) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
            System.out.println("Broadcasted latest block to " + connectedWorkers.size() + " workers: index " + latest.getIndex());
        } catch (IOException e) {
            System.err.println("Error broadcasting latest block: " + e.getMessage());
        }
    }
    
    private void startMiningTaskScheduler() {
    int interval = this.serverConfigService.getMiningIntervalSeconds();
    int initialDelay = Math.min(5, interval); 
    scheduler.scheduleAtFixedRate(this::sendMiningTaskToWorkers, initialDelay, interval, TimeUnit.SECONDS);
    System.out.println("Node started scheduler: Mining tasks (initial=" + initialDelay + "s, period=" + interval + "s)");
    }
    
    public void sendMiningTaskToWorkers() {
        if (connectedWorkers.isEmpty()) {
            return;
        }
        
        try {
            String dataString = "test asamasi";
            
            
            String currentTestImage = imageService.selectRandomTestImage();
            
            
            
            
            byte[] currentImageBytes = decodeResourceToScaledJpegBytes(currentTestImage, 320, 0.75f);
           
            MiningTaskData taskData = createMiningTask(dataString);

           
            String imageId = java.util.UUID.randomUUID().toString();
            com.example.blockchaincamera.dto.MiningTaskHeader header = new com.example.blockchaincamera.dto.MiningTaskHeader(
                taskData.getIndex(),
                taskData.getTimestamp(),
                taskData.getPrevHash(),
                taskData.getDataString(),
                taskData.getAnalysisResult(),
                taskData.getImageSimilarityScore(),
                imageId,
                1
            );
            com.example.blockchaincamera.dto.WebSocketMessage<com.example.blockchaincamera.dto.MiningTaskHeader> headerMsg =
                new com.example.blockchaincamera.dto.WebSocketMessage<>(MessageType.MINING_TASK_HEADER, header);
            String headerJson = objectMapper.writeValueAsString(headerMsg);
            broadcastTextSafely(headerJson);

            for (WebSocketSession session : connectedWorkers) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new BinaryMessage(currentImageBytes));
                    } catch (java.io.IOException e) {
                        System.err.println("Error sending binary image to worker: " + e.getMessage());
                    }
                }
            }
            System.out.println("Node sent binary mining task to " + connectedWorkers.size() + " workers. imageId=" + imageId);
            
    } catch (IOException e) {
            System.err.println("Error sending mining task: " + e.getMessage());
        }
    }

    

    

    
    private byte[] decodeResourceToScaledJpegBytes(String resourcePath, int maxDim, float jpegQuality) {
        if (resourcePath == null) return new byte[0];
        try {
            java.awt.image.BufferedImage src;
            ClassPathResource res = new ClassPathResource(resourcePath);
            if (res.exists()) {
                try (var is = res.getInputStream()) {
                    src = javax.imageio.ImageIO.read(is);
                }
            } else {
                src = javax.imageio.ImageIO.read(Path.of(resourcePath).toFile());
            }
            if (src == null) {
                ClassPathResource res2 = new ClassPathResource(resourcePath);
                try (var is = res2.getInputStream()) {
                    return is.readAllBytes();
                } catch (Exception ex) {
                    return Files.readAllBytes(Path.of(resourcePath));
                }
            }
            int w = src.getWidth();
            int h = src.getHeight();
            double scale = 1.0;
            if (maxDim > 0) {
                int maxWH = Math.max(w, h);
                if (maxWH > maxDim) scale = maxDim / (double) maxWH;
            }
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            java.awt.image.BufferedImage dst = new java.awt.image.BufferedImage(nw, nh, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2 = dst.createGraphics();
            try {
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.drawImage(src, 0, 0, nw, nh, null);
            } finally {
                g2.dispose();
            }
            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                 javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO.createImageOutputStream(baos)) {
                javax.imageio.ImageWriter jpgWriter = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
                jpgWriter.setOutput(ios);
                javax.imageio.plugins.jpeg.JPEGImageWriteParam jpegParams = new javax.imageio.plugins.jpeg.JPEGImageWriteParam(java.util.Locale.getDefault());
                jpegParams.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                jpegParams.setCompressionQuality(Math.max(0.1f, Math.min(1.0f, jpegQuality)));
                jpgWriter.write(null, new javax.imageio.IIOImage(dst, null, null), jpegParams);
                jpgWriter.dispose();
                return baos.toByteArray();
            }
        } catch (java.io.IOException e) {
            System.err.println("Failed scaling to bytes, fallback to raw: " + e.getMessage());
            try {
                ClassPathResource res = new ClassPathResource(resourcePath);
                if (res.exists()) {
                    try (var is = res.getInputStream()) { return is.readAllBytes(); }
                }
                return Files.readAllBytes(Path.of(resourcePath));
            } catch (java.io.IOException ex) {
                return new byte[0];
            }
        }
    }

    private void broadcastTextSafely(String json) {
        for (WebSocketSession session : connectedWorkers) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (java.io.IOException e) {
                    System.err.println("Failed to send WS message: " + e.getMessage());
                }
            }
        }
    }
    
    public void handleWorkerMessage(String message, WebSocketSession session) {
        try {
            @SuppressWarnings("unchecked")
            com.example.blockchaincamera.dto.WebSocketMessage<Object> wsMessage = 
                objectMapper.readValue(message, com.example.blockchaincamera.dto.WebSocketMessage.class);
            
            if (wsMessage.getType() == MessageType.BLOCK_FOUND) {
                handleBlockFoundConsensus(wsMessage.getData(), session);
            }
            
    } catch (IOException e) {
            System.err.println("Error handling worker message: " + e.getMessage());
        }
    }
    
    private void handleBlockFoundConsensus(Object blockData, WebSocketSession session) {
        try {
            String blockJson = objectMapper.writeValueAsString(blockData);
            Block foundBlock = objectMapper.readValue(blockJson, Block.class);
      
            try {
                com.example.blockchaincamera.dto.NewBlockData dto = objectMapper.readValue(blockJson, com.example.blockchaincamera.dto.NewBlockData.class);
                if (dto.getImageBase64() != null) {
                    foundBlock.setImageBase64(dto.getImageBase64());
                }
                if (dto.getImageEncoding() != null) {
                    foundBlock.setImageEncoding(dto.getImageEncoding());
                }
            } catch (java.io.IOException ignore) { }

            
            if (!preValidateBlock(foundBlock)) {
                System.out.println("Pre-validation failed for worker " + session.getId() + " at index " + foundBlock.getIndex());
                notifyErrorToSessions(java.util.List.of(session), "Pre-validation failed", foundBlock.getIndex(), "Hash/prev/difficulty mismatch");
                return;
            }

            int idx = foundBlock.getIndex();
            long now = System.currentTimeMillis();
            boolean isFirst;
            consensusLock.lock();
            try {
                pendingSubmissions.computeIfAbsent(idx, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                                  .add(new CandidateSubmission(foundBlock, session, now));
                isFirst = (firstSubmissionTime.putIfAbsent(idx, now) == null);
            } finally {
                consensusLock.unlock();
            }

            if (isFirst && scheduledConsensusChecks.add(idx)) {
                scheduler.schedule(() -> {
                    try { evaluateConsensusIfReady(idx); } finally { scheduledConsensusChecks.remove(idx); }
                }, MAX_SIMILARITY_WAIT_MILLIS, TimeUnit.MILLISECONDS);
            } else {
              
                evaluateConsensusIfReady(idx);
            }
        } catch (IOException e) {
            System.err.println("Error handling found block: " + e.getMessage());
        }
    }

    private boolean preValidateBlock(Block b) {
        try {
           
            Block latest = blockchain.getLatestBlock();
            if (b.getIndex() == 0) {
                if (!"0".equals(b.getPrevHash())) {
                    return false;
                }
            } else {
                if (latest == null) {
                    return false;
                }
                if (b.getIndex() != latest.getIndex() + 1) {
                    return false;
                }
                if (!b.getPrevHash().equals(latest.getHash())) {
                    return false;
                }
            }
           
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void evaluateConsensusIfReady(int index) {
        java.util.List<CandidateSubmission> subs = pendingSubmissions.get(index);
        if (subs == null || subs.isEmpty()) return;

        long firstTs = firstSubmissionTime.getOrDefault(index, 0L);
        long elapsed = System.currentTimeMillis() - firstTs;
        boolean timeExpired = elapsed >= MAX_SIMILARITY_WAIT_MILLIS;
        boolean enoughSubs = subs.size() >= 2; 
        if (!timeExpired && !enoughSubs) {
          
            return;
    }
  
    double minSim = Double.POSITIVE_INFINITY;
    double maxSim = Double.NEGATIVE_INFINITY;
    CandidateSubmission best = null;
        for (CandidateSubmission cs : subs) {
            double s = cs.block.getImageSimilarityScore();
            if (s < minSim) minSim = s;
            if (s > maxSim) maxSim = s;
            if (best == null || cs.timestamp < best.timestamp) best = cs;
        }
        double spread = maxSim - minSim;
        if (spread > SIMILARITY_TOLERANCE) {
            System.out.println("Consensus rejected at index " + index + ": similarity spread=" + String.format("%.2f", spread));
          
            notifyErrorToSessions(subs.stream().map(cs -> cs.session).toList(),
                "Similarity discrepancy exceeds tolerance", index, "spread=" + String.format("%.2f", spread));
            clearConsensus(index);

            sendMiningTaskToWorkers();
            return;
        }

     
        if (best != null) {
            Block accepted = best.block;
            if (blockchain.validateAndAddBlock(accepted)) {
                System.out.println("Consensus accepted block at index " + index + 
                    ": similarity min/max=" + String.format("%.2f/%.2f", minSim, maxSim));
                saveToDisk(blockchain.getAllBlocks());
                notifyWorkersBlockAccepted(accepted);
                broadcastLatestBlock();
            } else {
                System.out.println("Consensus candidate failed final validation at index " + index);
                notifyErrorToSessions(java.util.List.of(best.session), "Final validation failed", index, null);
            }
        }
        clearConsensus(index);
    }

    private void clearConsensus(int index) {
        consensusLock.lock();
        try {
            pendingSubmissions.remove(index);
            firstSubmissionTime.remove(index);
        } finally {
            consensusLock.unlock();
        }
    }

    private void notifyErrorToSessions(java.util.Collection<WebSocketSession> sessions, String reason, int index, String details) {
        try {
            com.example.blockchaincamera.dto.ErrorInvalidBlockData err =
                new com.example.blockchaincamera.dto.ErrorInvalidBlockData(reason, index, details);
            com.example.blockchaincamera.dto.WebSocketMessage<com.example.blockchaincamera.dto.ErrorInvalidBlockData> msg =
                new com.example.blockchaincamera.dto.WebSocketMessage<>(MessageType.ERROR_INVALID_BLOCK, err);
            String json = objectMapper.writeValueAsString(msg);
            for (WebSocketSession s : sessions) {
                if (s != null && s.isOpen()) {
                    s.sendMessage(new TextMessage(json));
                }
            }
        } catch (java.io.IOException e) {
            System.err.println("Failed to notify error: " + e.getMessage());
        }
    }

    private static class CandidateSubmission {
        final Block block;
        final WebSocketSession session;
        final long timestamp;
        CandidateSubmission(Block block, WebSocketSession session, long timestamp) {
            this.block = block; this.session = session; this.timestamp = timestamp;
        }
    }

  
    private List<Block> loadFromDisk() {
        ioLock.lock();
        try {
            if (!Files.exists(DATA_FILE)) {
                return Collections.emptyList();
            }
            String json = Files.readString(DATA_FILE, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return Collections.emptyList();
            }
            return fileMapper.readValue(json, new TypeReference<List<Block>>() {});
        } catch (IOException e) {
            System.err.println("Failed to load blockchain from disk: " + e.getMessage());
            return Collections.emptyList();
        } finally {
            ioLock.unlock();
        }
    }

    private void saveToDisk(List<Block> blocks) {
        ioLock.lock();
        try {
            Files.createDirectories(DATA_DIR);
            String json = fileMapper.writerWithDefaultPrettyPrinter().writeValueAsString(blocks);
            Path tmp = DATA_DIR.resolve("blockchain.json.tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, DATA_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("Blockchain persisted: " + blocks.size() + " blocks -> " + DATA_FILE.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save blockchain to disk: " + e.getMessage());
        } finally {
            ioLock.unlock();
        }
    }
    
    private void notifyWorkersBlockAccepted(Block block) {
        try {
            com.example.blockchaincamera.dto.BlockAcceptedData blockData = 
                new com.example.blockchaincamera.dto.BlockAcceptedData(
                    block.getIndex(), 
                    block.getHash()
                );
            
            com.example.blockchaincamera.dto.WebSocketMessage<com.example.blockchaincamera.dto.BlockAcceptedData> message = 
                new com.example.blockchaincamera.dto.WebSocketMessage<>(MessageType.BLOCK_ACCEPTED, blockData);
            
            String messageJson = objectMapper.writeValueAsString(message);
            
            for (WebSocketSession session : connectedWorkers) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(messageJson));
                }
            }
            
    } catch (IOException e) {
            System.err.println("Error notifying workers: " + e.getMessage());
        }
    }
    
    private MiningTaskData createMiningTask(String dataString) {
        Block latestBlock = blockchain.getLatestBlock();
        int newIndex = (latestBlock != null) ? latestBlock.getIndex() + 1 : 0;
        String prevHash = (latestBlock != null) ? latestBlock.getHash() : "0";
        

        String analysisResult = "Awaiting Worker Analysis";
        double imageSimilarityScore = 0.0;
        
        return new MiningTaskData(
            newIndex,
            LocalDateTime.now(),
            prevHash,
            dataString,
            analysisResult,
            imageSimilarityScore
        );
    }
    

    @Setter
    @Getter
    public static class MiningTaskMessage {

        private int index;
        private LocalDateTime timestamp;
        private String prevHash;
        private String dataString;
        private String analysisResult;
        private double imageSimilarityScore;
    private String currentImageBase64;
    private String previousImageBase64;
        
    public MiningTaskMessage() {}
        
    public MiningTaskMessage(int index, LocalDateTime timestamp, String prevHash,
                   String dataString, String analysisResult, double imageSimilarityScore,
                   String currentImageBase64, String previousImageBase64) {
            this.index = index;
            this.timestamp = timestamp;
            this.prevHash = prevHash;
            this.dataString = dataString;
            this.analysisResult = analysisResult;
            this.imageSimilarityScore = imageSimilarityScore;
        this.currentImageBase64 = currentImageBase64;
        this.previousImageBase64 = previousImageBase64;
        }

    }
    
    @Getter
    public static class MiningTaskData {

        private final int index;
        private final LocalDateTime timestamp;
        private final String prevHash;
        private final String dataString;
        private final String analysisResult;
    private final double imageSimilarityScore;
        
    public MiningTaskData(int index, LocalDateTime timestamp, String prevHash,
                String dataString, String analysisResult, double imageSimilarityScore) {
            this.index = index;
            this.timestamp = timestamp;
            this.prevHash = prevHash;
            this.dataString = dataString;
            this.analysisResult = analysisResult;
            this.imageSimilarityScore = imageSimilarityScore;
        }

    }
}
