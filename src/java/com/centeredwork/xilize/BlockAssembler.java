package com.centeredwork.xilize;

import java.util.ArrayList;



public class BlockAssembler {
    
    private final TaskFile task;

    
    int index;  // current index into array of rawBlocks
    
    // to warn if start and end blocks don't match
    int start;
    int end;
    ArrayList<Block> rawBlocks = new ArrayList<Block>();
    
    BlockAssembler(TaskFile task,  ArrayList<Block> rawBlocks) {
        this.task = task;
        this.rawBlocks =  rawBlocks;
    }
    
    void assemble(Block parent) {
        
        assembleChildren(parent);
        if (start > end) {
            this.task.warning(0, start-end +" more start blocks than end blocks");
        }
    }
    
    private void extendBlock(Block parent, Signature sig) {
        
        if( index >= rawBlocks.size() )
            return;
        
        Block block = rawBlocks.get(index);
        while(index < rawBlocks.size() && !block.isSigned() && !block.isEndBlock()) {
            block.setSignature(sig);
            parent.addChild(block);
            index++;
            if( index < rawBlocks.size() )
                block = rawBlocks.get(index);
            else
                break;
        }
        
    }
    
    private void assembleChildren(Block parent) {
        
        while (index < rawBlocks.size()) {
            
            Block block = rawBlocks.get(index);
            index++;
            
            if (block.isEndBlock() ) {
                end++;
                if (end > start) {
                    this.task.warning(block.getLineNumber(), "end block without matching startblock");
                }
                return;
            }
            if( block.isStartBlock() ) {
                
                start++;
                parent.addChild(block);
                assembleChildren(block);
                
            } else if( block.isExtended() ) {
                
                parent.addChild(block);
                extendBlock(parent, block.getSignature());
                
            } else {
                
                parent.addChild(block);
            }
        }
    }
}