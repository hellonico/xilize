/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Instances of TaskFile are associated with a particular Xilize source file
 * and as such control the information available to the file being processed.
 *
 * <p>This class reads an Xilize source file and produces a parse tree of
 * signed blocks which is translated and written to an output file.
 */
public class TaskFile extends Task {
    
    protected File file;        // source file with xilize markup to be translated
    protected File outputFile;  // target file to generate
    protected Catalog catalog;
    
    // list of raw blocks, not yet assembled into parse tree
    protected ArrayList<Block> rawBlocks = new ArrayList<Block>();
    
    public TaskFile(Task parent, File file) {
        super(parent);
        this.file = file;
        catalog = new Catalog(this);
    }
    
    protected boolean isGeneratingOutput() { return true; }
    
    /**
     * path associated with this task.
     * @return absolute pathname
     */
    public String getPath() {
        return file.getAbsolutePath();
    }
    
    public File getFile() { return file; }

    
    static class Include extends TaskFile {
        
        int lineNum;
        
        public Include(TaskFile tf, File file, int lineNum, ArrayList<Block> rawBlocks) {
            super(tf, file);
            this.lineNum = lineNum;
            this.rawBlocks = rawBlocks;
            defReg = parent.defReg;
            abbrevReg = parent.abbrevReg;
            sigReg = parent.sigReg;
        }
        
        public String getDescription() {
            return "include file "+getPath();
        }
        
        public void xilize() throws XilizeException {
            
            try {
                BlockReader br = new BlockReader(this, new BufferedReader(new FileReader(file)));
                getRawBlocks( br );
                br.close();
            } catch (IOException e) {
                // to do fix exception handling, give it to include signature if possible
                getParent().error(lineNum, "error reading include file: "+e.getMessage());
                e.printStackTrace();
            }
        }
    }
        
    static class Config extends TaskFile {
        
        public Config(Task parent, File file) {
            super(parent,file);
        }
        
        public String getDescription() { return "config file"+getPath(); }
        
        protected boolean isGeneratingOutput() { return false; }
        
    }
    
    static class Master extends Config {
        
        Master( File configFile, Reporter reporter, BeanShell bsh) {
            
            super(null, configFile);
            env = new Env(reporter, bsh);
            Key.addDefaultKV(defReg);
            Signature.addStdSigSet(this);
            
        }
        
        public String getDescription() { return "master task"; }
        
        void loadSystemProperties() {
            Properties prop = System.getProperties();
            Enumeration e = prop.keys();
            while( e.hasMoreElements() ) {
                String k = (String) e.nextElement();
                define(k, prop.getProperty(k));
            }
        } 
    }
    
    public String getDescription() {
        return "file "+getPath();
    }
    
    public String toString() {
        return getDescription() +":"+ (file==null? "": file.getName());
    }
    
    
    public void xilize() throws XilizeException {
        if( isHalted() ) {
            throw new XilizeException(Xilize2.ExitCode.UserHalt, this, "user interrupt");
        }
        try {
            BlockReader br = new BlockReader(this, new BufferedReader(new FileReader(file)));
            xilize(br);
            br.close();
        } catch (IOException e) {
            error("error reading file", e);
        }
    }
    
    private void xilize( BlockReader br ) throws XilizeException {
        
        report(file.toString());
        
        // add input file info definitions
        
        define(Key._FilePathXil_, Files.normalizePath(file.getAbsolutePath()));
        define(Key._FileNameXil_, file.getName());
        
        try {
            
            // create list of raw blocks, i.e. block children not yet identified
            
            int xilFileStart = -1;
            
            if( isNatural() ) {
                if( isDefined(Key.commoninc) ) include( value(Key.commoninc) );
                if( isDefined(Key.headerinc) ) include( value(Key.headerinc) );
                xilFileStart = rawBlocks.size();
            }
            
            getRawBlocks( br );
            
            if( isNatural() ) {
                
                if( isDefined(Key.footerinc) ) 
                    
                    include( value(Key.footerinc) );
                
                if( xilFileStart < rawBlocks.size() ) {
                    
                    for( int i = xilFileStart; i< rawBlocks.size(); i++ ) {
                        Block b = rawBlocks.get(i);
                        if( !b.isSigned() ) {
                            String label = markup(b.linesAsString());
                            define(Key._NaturalLabel_, label);
                            defineDefault(Key.title.name(), label);  //legacy support
                            b.setSignature(getSignature(value(Key._NaturalSig_)));
                            break;
                        }
                    }
                }
                
            }
            
            // assemble parse tree
            
            Block root = new Block();
            (new BlockAssembler(this,rawBlocks)).assemble(root);
            
            // do directives
            
            for( Block b : root.getChildren() ) {
                b.exec();
            }
            
            // add epilog/prolog
            
            Block prolog = new Block( this, getSignature("prolog"));
            Block epilog = new Block( this, getSignature("epilog"));
            ArrayList<Block> rootKids = root.getChildren();
            if( root.getChildren() == null ) {
                root.addChild(prolog);
                root.addChild(epilog);
            } else {
                root.getChildren().add(0, prolog);
                root.addChild(epilog);
            }
            
            // translate
            
            // add output file info defintions
            
            String inputName = file.getName();
            String outputExtension = value(Key._OutputExtension_);
            outputFile = new File(
                    file.getParent(),
                    inputName.replaceAll("\\.xil$", "."+outputExtension));
            
            define(Key._FilePathHtml_, Files.normalizePath(outputFile.getAbsolutePath()));
            define(Key._FilePathOuput_, Files.normalizePath(outputFile.getAbsolutePath()));
            define(Key._FileNameHtml_, outputFile.getName());
            define(Key._FileNameOutput_, outputFile.getName());
            
            for( Block b : root.getChildren() ) {
                b.translate();
            }
            
            for( Block b : root.getChildren() ) {
                b.translateLast(); //  for "toc." etc.
            }
            
            // write
            
            if( isGeneratingOutput() ) {
                PrintWriter pw = new PrintWriter( new BufferedWriter( new FileWriter(outputFile) ) );
                for( Block b : root.getChildren() ) {
                    b.write(pw);
                    pw.println();
                }
                pw.close();
            }
            
        } catch( XilizeException e ) {
            error(0, e.getMessage());
            throw e;
        } catch( IOException e ) {
            error("error reading/writing source file, trying to continue", e);
        }
        
    }
    
   
        
    /**
     * read blocks from an include file into this task's raw block list.
     *
     * @param path file to include
     * @throws com.centeredwork.xilize.XilizeException on unrecoverable error
     */
    void include( String path ) throws XilizeException {        
        include(0, path);
    }    
    
    void include( int line, String path ) throws XilizeException {        
        include(line, Files.localFile( path, file.getParent()));
    }    
    
    void include(int line, File path ) throws XilizeException {
        
        (new TaskFile.Include(this, path, line, rawBlocks)).xilize();
    }    
    
    /**
     * reads raw blocks (children not yet identified) and appends them to 
     * the existing list.
     * 
     * @param br reader to use
     * @throws com.centeredwork.xilize.XilizeException on unrecoverable error
     */
    protected void getRawBlocks( BlockReader br ) throws XilizeException {
        
        Block b = null;
        try {
            while( (b = br.readRawBlock()) != null ) {
                if( b.getSignature()==null ) {
                    error(b.getLineNumber(), "block has no signature");
                    throw new XilizeException( Xilize2.ExitCode.Error, this, "block has no signature");
                }
                if( isValueTrue(Key._DebugReportRawBlocks_)) {
                    report(b.toString());
                }
                if( b.getSignature().isImmediate() ) {
                    b.exec();
                } else {
                    rawBlocks.add(b);
                }
            }
        } catch (IOException e) {
            int ln = 0;
            if( rawBlocks.size() > 0 )
                ln = rawBlocks.get(rawBlocks.size()-1).getLineNumber();
            error(ln, "reading problem after this block: "+ e.getMessage());
        }
        
    }

    
}


