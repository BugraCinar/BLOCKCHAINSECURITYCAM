package com.example.blockchaincamera.dto;

import com.example.blockchaincamera.model.Block;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LatestBlockResponseData {
    private Block block;
    private int chainLength;
}