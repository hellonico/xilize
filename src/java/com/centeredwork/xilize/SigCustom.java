/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

import bsh.EvalError;
import bsh.TargetError;
import bsh.ParseException;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wrapper around custom signatures written in BeanShell.
 */
public class SigCustom extends Signature {
    
    private Block codeBlock;
    private Task owner;
    private int lineNumber;
    
    public SigCustom(Task owner, Block codeBlock) throws XilizeException {
        
        name = owner.markupKM(codeBlock.getLine(0));
        lineNumber = codeBlock.getLineNumber();
        if( name == null || name.matches(" *") ) {
            throw new XilizeException(owner, lineNumber,
                    "custom signature name missing");
        }
        if( !name.matches("[A-Za-z]+") ) {
            throw new XilizeException(owner, lineNumber,
                    "custom signature name may contain only letters");
        }
        this.owner = owner;
        this.codeBlock = codeBlock;
    }
    
    public String translate(Task task, Block block) {
        
        BeanShell bsh = task.getBsh();
        String result = null;
        
        try {
            
            bsh.set("sig", this);
            bsh.set("task", task);
            bsh.set("block", block);
            bsh.set("text", block.linesAsString());
            
            result = bsh.exec(getOwner(), codeBlock.getLineNumber(), codeBlock.linesAsStringTrim(1));
            
        }  catch ( EvalError e ) {
            
            // bsh.exec() has reported the error with respect to the 
            // codeBlock, now we report it wrt the block of text being translated
            // (different files, line locations, and messages)
            
            task.error(block.getLineNumber(), "custom signature translation failed " );
            
        }
        return result;
        
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public Task getOwner() {
        return owner;
    }
    
}
