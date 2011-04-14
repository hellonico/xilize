package com.centeredwork.xilize;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Block is the basic unit of parsing an Xilize source file.  Raw blocks as retuned
 * by a BlockReader are fed to the block assembler which identifies parent/child
 * relationships and creates parse tree of complete blocks.
 * @see TaskFile
 * @see Signature
 */
public class Block {
    
    private Signature sig;
    private ArrayList<String> lines = new ArrayList<String>();
    private ArrayList<Block> children;
    private Block parent;
    private int trailingBlankLineCount;  // required for propper <pre> output
    
    // flags
    private boolean extended;
    private boolean startBlock;
    private boolean endBlock;
    private boolean signed;
    private boolean writeChildren = true;
    
    public void setWriteChildren( boolean writeChildren ) {
        this.writeChildren = writeChildren;
    }
    
    // for error reporting
    private Task task;
    private int startLineNumber;
    
    // an association between source line numbers and the lines of the block, for error reporting
    private ArrayList<Integer> lineNumbers = new ArrayList<Integer>();
    
    // set by ctor or translate()
    private String translation = "";
    
    public Block( Task task, int startLineNumber, String firstLine ) {
        this(task, startLineNumber, firstLine, false);
    }
    
    Block( Task task, int startLineNumber, String firstLine, boolean startBlock  ) {
        
        this.startBlock = startBlock;
        this.task = task;
        this.startLineNumber = startLineNumber;
        
        if( sigNormal(firstLine) ||  sigSymbol(firstLine) ||  sigAbrrev(firstLine) ) {
            
            signed = true;
            
            // todo:  add special structured "signatures"
        } else {
            
            sig = task.getSignature(task.value(Key._UnsignedBlockSigName_));
            addLine(startLineNumber, firstLine);
            signed = false;
        }
        
    }
    
    
    static Block createEndBlock(Task task, int sourceLineNumber) {
        
        Block b = new Block(task, new Signature() {
            public String translate(Task task, Block block) {
                task.error(block.getLineNumber(), "end block being translated, coding error");
                return "";
            }
        });
        b.startLineNumber = sourceLineNumber;
        b.endBlock = true;
        return b;
    }
    
    Block( Task task, Signature sig ) {
        
        this.task = task;
        this.sig = sig;
        this.startLineNumber = -1;
        this.signed = true;
    }
    
    Block( String text ) {
        translation = text;
    }
    
    public Block() {}
    
    // normal signatures
    private static final String SIGNATURE_REGEX
            = "^\\s*(\\w+)("+Modifiers.REGEX_7+")(\\.|\\.\\.)(?:$|(?: (.*)))$";
    
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile(SIGNATURE_REGEX);
    
    private static final Pattern FN_SIG_PATTERN = Pattern.compile("fn(\\d+)");
    private static final Pattern NOT_SIG_PATTERN = Pattern.compile("\\d+");
    
    private boolean sigNormal(String firstLine) {
        
        Matcher m = SIGNATURE_PATTERN.matcher(firstLine);
        if( !m.matches() )
            return false;
        
        String name = m.group(1);
        String modifiers = m.group(2)==null? "": m.group(2);
        extended = m.group(10).length() == 2;
        
        // eliminate "1." false signatures
        Matcher notSig = NOT_SIG_PATTERN.matcher(name);
        if( notSig.matches() ) {
            return false;
        }
        
        // footnote
        Matcher fm = FN_SIG_PATTERN.matcher(name);
        if( fm.matches() ) {
            sig = new Signature.Footnote(fm.group(1));
        } else { // others
            sig = task._getSignature(name);
            if( sig == null ) {
                if( !task.isDefined(Key._NoWarnOnLooksLikeSig_) ) {
                    task.warning(startLineNumber, "\""+name+modifiers+m.group(10)+
                            "\" looks like a signature");
                }
                return false;
            } else {
                sig = sig.copy();
            }
        }
        String text =  m.group(11);
        if( text != null )
            addLine(startLineNumber, text );
        
//        sig.setMods(new Modifiers.Sig(task,modifiers));
        sig.setMods(task,modifiers);
        return true;
    }
    
    // signatures which are identified by a special symbol:
    //     |, *, #
    // respectively:  table, ul, ol
    private static final String SYMBOL_SIG_REGEX
            = "^ *("+Modifiers.REGEX_7+")(\\*+|#+|\\|)(?:"+Modifiers.REGEX_7+")(?: *$| .*$)";
    
    private static final Pattern SYMBOL_SIG_PATTERN = Pattern.compile(SYMBOL_SIG_REGEX);
    
    private boolean sigSymbol(String firstLine) {
        
        Matcher m = SYMBOL_SIG_PATTERN.matcher(firstLine);
        if( !m.matches() )
            return false;
        
        String s = m.group(9);
        String t = m.group(10);
        char c = m.group(9).charAt(0);
        switch(c) {
            case '|':
                addLine(startLineNumber, m.group(0) );
                sig = new SigTable();
                break;
                
            case '*':   // fallthrough
            case '#':
                addLine(startLineNumber, m.group(0) );
                sig = new SigList();
                break;
                
            default: return false;
        }
        return true;
    }
    
    // url-abbreviation:  [abbrev]url
    
    private boolean sigAbrrev(String firstLine) {
        
        if( Signature.Abbrev.isAbbrevLine(firstLine) ) {
            addLine(startLineNumber, firstLine );
            sig = new Signature.Abbrev();
            return true;
        }
        return false;
    }
    
    
    public void wrapChildren(String prelude, String coda) {
        if( children == null ) {
            setChildren(new ArrayList<Block>());
        }
        children.add(0, new Block.Static(prelude));
        children.add(new Block.Static(coda));
    }
    
    // used by BlockReader
    void addLine(int number, String s, boolean startBlock) {
        this.startBlock = startBlock;
        addLine( number, s );
    }
    
    public void addLine(int number, String s) {
        lines.add(s);
        lineNumbers.add(number);
    }
    
    public void removeLine(int index) {
        lines.remove(index);
        lineNumbers.remove(index);
    }
    
    public void setLine( int n, String s ) {
        lines.set(n,s);
    }
    
    public String getLine( int n ) {
        return lines.get(n);
    }
    
    public String linesAsString() {
        return linesAsString(0, false);
    }
    
    public String linesAsStringTrim() {
        return linesAsString(0, true);
    }
    
    public String linesAsString(int start) {
        return linesAsString(start, false);
    }
    
    public String linesAsStringTrim(int start) {
        return linesAsString(start, true);
    }
    
    private void wrapLine(int wrap, String line, StringBuilder sb) {
        
        if( wrap < 1 ) {
            sb.append(line);
            sb.append('\n');
            return;
        }
        do {
            if( wrap > line.length() ) {
                sb.append(line);
                sb.append('\n');
                line = "";
            } else {
                int n = line.lastIndexOf(' ', wrap);
                if( n == -1 ) {
                    sb.append(line+'\n');
                    line = "";
                } else {
                    sb.append(line.substring(0,n));
                    sb.append('\n');
                    if( n<line.length() - 1) {
                        line = line.substring(n+1);
                    } else {
                        line = "";
                    }
                }
                
            }
        } while( !line.equals("") );
    }
    
    private void wrapLines(int wrap, StringBuilder sb, boolean addTrailingBlankLines ) {
        
        for( String line : lines ) {
            wrapLine(wrap, line, sb);
        }
        if( addTrailingBlankLines ) {
            for( int i = 0; i < trailingBlankLineCount; i++ ) {
                sb.append('\n');
            }
        }
    }
    
    public String wrapLines(int wrap) {
        StringBuilder sb = new StringBuilder();
        if( children == null ) {
            wrapLines( wrap, sb, false);
            return sb.toString();
        }
        
        wrapLines( wrap, sb, true);
        for( int i=0; i<children.size() - 1; i++ ) {
            children.get(i).wrapLines( wrap, sb, true);
        }
        
        // don't add trailing blank lines to the last child
        if( !children.isEmpty() )
            children.get(children.size()-1).wrapLines( wrap, sb, false);
        return sb.toString();
    }
    
    // note: does not place a \n at the end of the last line
    private String linesAsString(int start, boolean trim) {
        if( lines == null || lines.isEmpty() )
            return "";
        
        StringBuilder sb = new StringBuilder();
        for( int i=start; i<lines.size()-1; i++ ) {
            sb.append( trim? lines.get(i).trim() : lines.get(i) );
            sb.append('\n');
        }
        sb.append( trim? lines.get(lines.size()-1).trim() : lines.get(lines.size()-1));
        return sb.toString();
    }
    
    public void exec() throws XilizeException {
        sig.exec(task, this);
    }
    
    void lastPass(Block root) {}
    
    public void translate() {
        translation = sig.translate(task, this);
    }
    public void translateLast() {
        if( translation == null )
            translation = sig.translateLast(task, this);
    }
    public void translateChildren() {
        for( Block c : children ) {
            c.translate();
        }
    }
    
    /**
     * writes the block's translation to the output stream
     * @param pw translation stream
     * @see Signature#writes()
     * @see Signature#write(PrintWriter,Block)
     */
    public void write(PrintWriter pw) {
        if( sig.writes() ) {
            sig.write(pw, this);
        } else if( writeChildren && children != null ) {
            for( Block child : children ) {
                child.write(pw);
            }
        } else if( translation != null ) {
            pw.println( translation );
        }
    }
    
    public void addChild(Block block) {
        if(children == null)
            setChildren(new ArrayList<Block>());
        children.add(block);
    }
    
    public Signature getSignature() {
        return sig;
    }
    public void setSignature(Signature signature) {
        this.sig = signature;
    }
    
    boolean isStartBlock() {
        return startBlock;
    }
    
    public String getTranslation() {
        return translation;
    }
    public void setTranslation(String translation) {
        this.translation = translation;
    }
    
    public ArrayList<Block> getChildren() {
        return children;
    }
    
    public boolean isParent() {
        return children != null;
    }
    
    Task getTask() {
        return task;
    }
    
    public ArrayList<String> getLines() {
        return lines;
    }
    
    public Block getParent() {
        return parent;
    }
    
    public int getLineNumber() {
        return startLineNumber;
    }
    
    public int getLineNumber(String originalLineText ) {
        int n = lines.indexOf(originalLineText);
        if( n == -1 )
            return startLineNumber;
        return lineNumbers.get(n);
    }
    
    public int getTrailingBlankLineCount() {
        return trailingBlankLineCount;
    }
    
    void setTrailingBlankLineCount(int n) {
        trailingBlankLineCount = n;
    }
    
    boolean isEndBlock() {
        return endBlock;
    }
    
    public boolean isExtended() {
        return extended;
    }
    
    public boolean isSigned() {
        return signed;
    }
    
    /**
     * A <CODE>Static</CODE> block has no signature or modifiers and is used only
     * in {@link Signature#translate(Task,Block) } methods to create unprocessed output text.
     */
    public static class Static extends Block {
        
        Static(String text) { super(text); }
        
        public void write(PrintWriter pw) {
            pw.println( getTranslation() );
        }
        
    }
    
    /**
     * for debugging.
     * @return a synopsis of this block
     */
    public String toString() {
        if( endBlock ) {
            return "[end block"+":"+trailingBlankLineCount+"]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(sig);
        sb.append(extended? ".." : ".");
        sb.append(" [lines="+lines.size()+":"+trailingBlankLineCount+"] ");
        if( lines.size()>0 ) {
            String line = lines.get(0);
            if( line.length() < 30 )
                sb.append(line);
            else
                sb.append(line.substring(0,30)+"...");
        }
        if( startBlock )
            sb.append(" [start block]");
        return sb.toString();
    }
    
    public void sign( String sigName, String mods ) {
        sig = task.getSignature(sigName);
        sig.setMods(new Modifiers.Sig(task, mods));
    }
    
    public void sign( String sigName, Modifiers mods ) {
        sig = task.getSignature(sigName);
        sig.setMods(mods);
    }
    
    public String translateAs( String sigName, String mods ) {
        sign(sigName, mods);
        translate();
        return translation==null? "" : translation;
    }
    
    public String translateAs( String sigName, Modifiers mods ) {
        sign(sigName, mods);
        translate();
        return translation==null? "" : translation;
    }
    
    public void setChildren(ArrayList<Block> children) {
        this.children = children;
    }
    
    public void morph(String firstline) throws XilizeException {
        Block b = new Block(task, getLineNumber(), firstline);
        setSignature(b.getSignature());
        setLine(0, b.getLine(0));
        exec();
    }
    
}
