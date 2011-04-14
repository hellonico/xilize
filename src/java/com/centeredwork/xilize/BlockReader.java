/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads blocks of text from an input stream.  Implemented as a one-line
 * pushback LineNumberReader.
 * @author Andy Streich
 */
public class BlockReader extends LineNumberReader {
    
    private Task task;
    
    private String lastLine;  // pushback buffer
    
    private Pattern isComment;
    private Pattern isStartBlock;
    private Pattern isEndBlock;
    private String tabSpaces;
    
    /**
     * Creates a BlockReader instance.
     * 
     * <B>Note</B>, the following values of the follow keys at the time of this object's
     * creation control how it behaves:  <CODE>_LineCommentString_</CODE>, <CODE>_BlockStartString_</CODE>,
     * and, <CODE>_BlockEndString_</CODE>.
     * @param reader input source containing Xilize markup
     * @param task current task
     */
    public BlockReader( Task task, Reader reader ) {
        
        super(reader);
        this.task = task;
        
        isComment = Pattern.compile("\\s*"+Pattern.quote(task.value(Key._LineCommentString_))+".*");
        isStartBlock = Pattern.compile("(.*?)"+" *"+Pattern.quote(task.value(Key._BlockStartString_)));
        isEndBlock = Pattern.compile(" *"+Pattern.quote(task.value(Key._BlockEndString_)));
        
        int n = task.isDefined(Key._SpacesPerTab_) ?
            Integer.valueOf(task.value(Key._SpacesPerTab_)) :
            1;
        
        StringBuilder sb = new StringBuilder(n);
        for( int i=0; i<n; i++ )
            sb.append(" ");
        tabSpaces = sb.toString();
        
    }
    
    /**
     * gets the next non-comment source line with trailing whitespace removed and
     * spaces substituted for tabs.  
     * 
     * <B>Note:</B>  Number of spaces per tab is controlled by the key <CODE>_SpacesPerTab_</CODE>.
     * @return next line, EOL not included
     * @throws java.io.IOException same as java.io.LineNumberReader
     */
    public String nextLine() throws IOException {
        
        // check for pushed-back line
        if( lastLine != null ) {
            String tmp = lastLine;
            lastLine = null;
            return tmp;
        }
        
        // skip comment lines
        String s = null;
        do {
            s = readLine();
        } while( s != null && isComment.matcher(s).matches() );
        
        // tabs converted to spaces
        // trims trailing whitespace
        
        return s==null? null: s.replaceAll("\\s+$", "").replaceAll("\t",tabSpaces);
    }
    
    void pushback( String line ) {
        lastLine = line;
    }
    
    /**
     * gets the line number of last non-comment source line read
     * @return line number of last non-comment source line read
     */
    public int getLineNumber() {
        
        if( lastLine == null ) {
            return super.getLineNumber();
        }
        return super.getLineNumber() - 1;
    }
    
    Block readRawBlock() throws IOException {
        
        // skip blank lines
        String line = null;
        do {
            line = nextLine();
            if( line == null ) return null;  // EOF
        } while( line.matches(" *") );
        
        if( isEndBlock.matcher(line).matches() )
            return Block.createEndBlock(task, getLineNumber());
        
        // create block
        
        Block block = null;
        Matcher m = isStartBlock.matcher(line);
        if( m.matches()) {
            // block only has one line and it ends with the start-block marker
            block = new Block(task, getLineNumber(), m.group(1), true);
            block.setTrailingBlankLineCount(blankLines());
            return block;
        }
        
        block = new Block(task, getLineNumber(), line);
        
        line = nextLine();
        while( line != null && line.matches(" *\\S.*") ) {
            if( isEndBlock.matcher(line).matches() )
                break;
            
            m = isStartBlock.matcher(line);
            if( m.matches()) {
                block.addLine(getLineNumber(), m.group(1), true);
                block.setTrailingBlankLineCount(blankLines());
                return block;
            }
            
            block.addLine(getLineNumber(), line);
            line = nextLine();
        }
        if( line != null ) {
            pushback(line);  
        }        
        block.setTrailingBlankLineCount(blankLines());
        return block;
    }
    
    private int blankLines() throws IOException {
        
        String line = nextLine();
        if( line == null ) return 0;
        
        int count = 0;
        while( line.matches(" *") ) {
            count++;
            line = nextLine();
            if( line == null ) return count;
        }
        if( line != null ) {
            pushback(line);
        }
        return count;
    }
}

