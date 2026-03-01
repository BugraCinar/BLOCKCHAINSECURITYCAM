package com.example.blockchaincamera.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewBlockData {
    private int index;
    private LocalDateTime timestamp;
    private String prevHash;
    private String hash;
    private int nonce;
    private String dataString;
    private String analysisResult;
    private double imageSimilarityScore; 
    private String imageBase64; 
    private String imageEncoding; 
    private String imageDigest;
}