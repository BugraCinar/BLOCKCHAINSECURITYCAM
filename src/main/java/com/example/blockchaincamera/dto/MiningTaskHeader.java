package com.example.blockchaincamera.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MiningTaskHeader {
    private int index;
    private LocalDateTime timestamp;
    private String prevHash;
    private String dataString;
    private String analysisResult;
    private double imageSimilarityScore;
    private String imageId;
    private int totalChunks;
}
