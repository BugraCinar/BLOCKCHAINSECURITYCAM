package com.example.blockchaincamera.model;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Block {
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

    public Block() {

    }
}