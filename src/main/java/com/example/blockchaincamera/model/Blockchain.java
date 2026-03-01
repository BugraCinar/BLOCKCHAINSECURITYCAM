package com.example.blockchaincamera.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.blockchaincamera.util.HashUtil;

public class Blockchain {

    private final ConcurrentHashMap<Integer, Block> chain = new ConcurrentHashMap<>();
    private final AtomicInteger lastIndex = new AtomicInteger(-1);
    private final AtomicInteger difficulty = new AtomicInteger(4); 

    public Blockchain(int initialDifficulty) {
        this.difficulty.set(initialDifficulty);
        

        if (lastIndex.get() == -1) {
            Block genesisBlock = createGenesisBlock();
            addGenesisBlock(genesisBlock);
        }
    }
    
    private Block createGenesisBlock() {
        Block genesis = new Block();
        genesis.setIndex(0);
        genesis.setTimestamp(LocalDateTime.now());
        genesis.setPrevHash("0");
        genesis.setNonce(0);
        genesis.setDataString("Genesis Block - Blockchain Camera System");
        genesis.setAnalysisResult("System Initialized - Diamond is Secure");
    genesis.setImageSimilarityScore(0.0); 
    genesis.setImageBase64(null); 
    genesis.setImageEncoding("NONE");
        genesis.setHash(calculateHash(genesis));
        return genesis;
    }

    private void addGenesisBlock(Block block) {
        chain.put(block.getIndex(), block);
        lastIndex.set(block.getIndex());
        
        System.out.println("Genesis block created: Index " + block.getIndex() + 
                          ", Hash: " + block.getHash().substring(0, 10) + "...");
    }

    public String calculateHash(Block block) {
    String digestOrPath = (block.getImageDigest() != null) ? block.getImageDigest() :
                   (block.getImageBase64() != null ? block.getImageBase64() : "");
    String dataToHash = block.getIndex() +
                block.getTimestamp().toString() +
                block.getPrevHash() +
                block.getNonce() +
                block.getDataString() +
                block.getAnalysisResult() +
        block.getImageSimilarityScore() +
        digestOrPath;
        return HashUtil.applyHash(dataToHash);
    }

    public synchronized boolean addBlock(Block block) {
        Block latestBlock = getLatestBlock();
        

        if (block.getIndex() == 0 && latestBlock == null) {

            if (!block.getPrevHash().equals("0")) {
                System.err.println("Genesis block must have prevHash = '0'");
                return false;
            }
            System.out.println("Validating Genesis block...");
        } else {

            if (latestBlock != null && block.getIndex() != latestBlock.getIndex() + 1) {
                System.err.println("Invalid block index. Expected: " + (latestBlock.getIndex() + 1) + 
                                 ", Got: " + block.getIndex());
                return false;
            }
            if (latestBlock != null && !block.getPrevHash().equals(latestBlock.getHash())) {
                System.err.println("Invalid previous hash. Expected: " + latestBlock.getHash() + 
                                 ", Got: " + block.getPrevHash());
                return false;
            }
        }
        

        chain.put(block.getIndex(), block);
        lastIndex.set(block.getIndex());
        

        if (chain.size() > 100) {
            int oldestToKeep = block.getIndex() - 99;
            chain.entrySet().removeIf(entry -> entry.getKey() < oldestToKeep);
        }
        
        System.out.println("Block added to chain: Index " + block.getIndex() + 
                          ", Hash: " + block.getHash().substring(0, 10) + "... " +
                          "(Memory: " + chain.size() + " blocks)");
        return true;
    }
    
    public List<Block> getRecentBlocks(int count) {
        List<Block> recentBlocks = new ArrayList<>();
        int startIndex = Math.max(0, lastIndex.get() - count + 1);
        
        for (int i = startIndex; i <= lastIndex.get(); i++) {
            Block block = chain.get(i);
            if (block != null) {
                recentBlocks.add(block);
            }
        }
        
        return recentBlocks;
    }
    
    public Block getLatestBlock() {
        int currentIndex = lastIndex.get();
        return currentIndex >= 0 ? chain.get(currentIndex) : null;
    }
    
    public int getChainLength() {
        return lastIndex.get() + 1;
    }
    
    public String getStorageInfo() {
        return "In-Memory Blockchain: " + chain.size() + " blocks in memory, " +
               "latest index: " + lastIndex.get();
    }
    

    public synchronized boolean validateAndAddBlock(Block block) {
        try {

            String expectedHash = calculateHash(block);
            if (!expectedHash.equals(block.getHash())) {
                System.err.println("Invalid block hash. Expected: " + expectedHash + ", Got: " + block.getHash());
                return false;
            }
            

            String difficultyPrefix = "0".repeat(difficulty.get());
            if (!block.getHash().startsWith(difficultyPrefix)) {
                System.err.println("Block does not meet difficulty requirement: " + difficultyPrefix);
                return false;
            }
            

            return addBlock(block);
            
        } catch (Exception e) {
            System.err.println("Error validating block: " + e.getMessage());
            return false;
        }
    }
    

    public synchronized void syncWithBlocks(List<Block> receivedBlocks) {
        for (Block block : receivedBlocks) {
            if (block.getIndex() > lastIndex.get()) {
                validateAndAddBlock(block);
            }
        }
    }
    
    
    public List<Block> getAllBlocks() {
        List<Block> allBlocks = new ArrayList<>();
        for (int i = 0; i <= lastIndex.get(); i++) {
            Block block = chain.get(i);
            if (block != null) {
                allBlocks.add(block);
            }
        }
        return allBlocks;
    }

    
    public synchronized boolean replaceWithBlocks(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }
      
        String difficultyPrefix = "0".repeat(difficulty.get());
        String prevHash = "0";
        for (int i = 0; i < blocks.size(); i++) {
            Block b = blocks.get(i);
            if (b.getIndex() != i) {
                System.err.println("Snapshot invalid: unexpected index at position " + i);
                return false;
            }
            if (i == 0) {
                if (!"0".equals(b.getPrevHash())) {
                    System.err.println("Snapshot invalid: genesis prevHash must be '0'");
                    return false;
                }
            } else {
                if (!prevHash.equals(b.getPrevHash())) {
                    System.err.println("Snapshot invalid: prevHash mismatch at index " + i);
                    return false;
                }
            }
            String expectedHash = calculateHash(b);
            if (!expectedHash.equals(b.getHash())) {
                System.err.println("Snapshot invalid: hash mismatch at index " + i);
                return false;
            }
           
            if (i > 0 && !b.getHash().startsWith(difficultyPrefix)) {
                System.err.println("Snapshot invalid: difficulty not met at index " + i);
                return false;
            }
            prevHash = b.getHash();
        }

        
        chain.clear();
        lastIndex.set(-1);
        for (Block b : blocks) {
            chain.put(b.getIndex(), b);
            lastIndex.set(b.getIndex());
        }
        System.out.println("In-memory blockchain replaced from snapshot: " + blocks.size() + " blocks");
        return true;
    }
}
