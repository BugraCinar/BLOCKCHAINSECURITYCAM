package com.example.blockchaincamera.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlockAcceptedData {
    private int blockIndex;
    private String blockHash;
    private String message;
    private double reward;        // zorluk*10 ??
    private String workerId;     
    
    
    public BlockAcceptedData(int blockIndex, String blockHash) {
        this.blockIndex = blockIndex;
        this.blockHash = blockHash;
        this.message = "Block accepted";
        this.reward = 0.0;
        this.workerId = "";
    }
}