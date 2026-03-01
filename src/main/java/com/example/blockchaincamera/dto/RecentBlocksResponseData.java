package com.example.blockchaincamera.dto;

import java.util.List;

import com.example.blockchaincamera.model.Block;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentBlocksResponseData {
    private List<Block> blocks;          // son 50
    private int totalChainLength;       
    private int fromIndex;               
    private int toIndex;                 
}
