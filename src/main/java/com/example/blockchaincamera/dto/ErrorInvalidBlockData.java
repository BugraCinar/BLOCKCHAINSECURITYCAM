package com.example.blockchaincamera.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorInvalidBlockData {
    private String reason;
    private int blockIndex;
    private String details;
}