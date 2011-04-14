package com.centeredwork.xilize;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** call the static startup() method once to initialize the translation engine,
 * then create an instance of Xilize2 for each use of the engine.
 */
public class Xilize2 extends TaskFile implements Runnable {
    
    public enum ExitCode {
        
        Okay(0), Error(1), Usage(2), Fatal(3), UserHalt(4), Io(5);
        
        private int value;
        public int getExitCode() { return value; }
        
        ExitCode(int i) { value = i; }
    }
    
    private static TaskFile.Master masterTask;
    
    public static void startup(Reporter reporter, BeanShell beanShell, HashMap<String,String> definitions) {
        
        if( reporter == null ) {
            throw new IllegalArgumentException("reporter is null");
        }
        
        File configFile = null;
        if( definitions != null && definitions.containsKey(Key._XilizeConfigFile_.name())) {
            configFile =  new File( definitions.get(Key._XilizeConfigFile_));
        }
        
        masterTask = new TaskFile.Master(configFile, reporter, beanShell);
        if( configFile != null ) {
            try {
                masterTask.xilize();
            } catch (XilizeException ex) {
                masterTask.error("master task configuration file error, trying to continue", ex);
            }
        }
        
        masterTask.define(definitions);
        masterTask.loadSystemProperties();        
    }
    
    public static void shutdown() {
        masterTask = null;
    }
    
    public static String[] signatureNames() {
        if( masterTask == null )
            throw new IllegalStateException("master task is null, was startup() called first?");
        return masterTask.sigNames();
    }
    
    private Task subtask;
    private ExitCode resultCode = ExitCode.Okay;
    
    public Xilize2() {
        super(masterTask,null);
        
        if( masterTask == null ) {
            throw new IllegalStateException("master task may not be null, call Xilize2.startup() first");
        }
        
        env = masterTask.getEnv();
        env.reset();
    }
    
    public void run() {
        resultCode = translate();
    }
    
    public ExitCode translate() {
        
        if( subtask == null ) {
            // try creating subtask from exisiting definitions in the key/value map
            createTask();
        }
        if( getResultCode() != ExitCode.Okay ) {
            error("aborting");
            return getResultCode();
        }
        if( subtask == null ) {
            // nothing to do
            error("no task defined, aborting");
            return ExitCode.Fatal;
        }
        
        if( subtask instanceof TaskDir ) {
            File rootConfigFile = ((TaskDir)subtask).getLocalFile("root.xilconfig");
            if( rootConfigFile != null ) {
                try {
                    include(0, rootConfigFile);
                } catch (XilizeException ex) {
                    error("problem in root.xilconfig", ex);
                    return ExitCode.Fatal;
                }
            }
            
        }
        // todo:  dir-oriented tasks run in separate thread when not
        //      run from the command line
        try {
            subtask.xilize();
        } catch (XilizeException ex) {
            resultCode = ex.getCode();
        }
        subtask = null;
        return getResultCode();
    }
    
    private void createTask() {
        
        if(isDefined(Key._TargetFile_) ) {
            
            xilizeFile(
                    value(Key._TargetRoot_),
                    value(Key._TargetDir_),
                    value(Key._TargetFile_));
            
        } else if(isDefined(Key._TargetDir_) ) {
            
            xilizeDirectory(
                    value(Key._TargetRoot_),
                    value(Key._TargetDir_));
            
        } else if(isDefined(Key._TargetBranch_) ) {
            
            xilizeBranch(
                    value(Key._TargetRoot_),
                    value(Key._TargetBranch_));
            
        } else if(isDefined(Key._TargetRoot_) ) {
            
            xilizeProject(value(Key._TargetRoot_));
            
        } else {
            error("coding error in Xilize2.createTask()");
        }
    }
    
    public String xilizePhrase(String phrase) {
        return markup(phrase);
    }
    
    public String xilizePhrase(String phrase, String rootPath, String subDir) {
        return null;
    }
    
    public String xilizeBlocks(String input, String rootPath, String subDir) {
        return null;
    }
    
    public String xilizeBlocks(String input) {
        
        BlockReader br = new BlockReader(this, new StringReader(input));
        
        try {
            
            getRawBlocks( br );
            
            Block root = new Block();
            (new BlockAssembler(this,rawBlocks)).assemble(root);
            
            for( Block b : root.getChildren() ) {
                b.exec();
            }
            
            for( Block b : root.getChildren() ) {
                b.translate();
            }
            
            for( Block b : root.getChildren() ) {
                b.translateLast();
            }
            
            StringWriter sw = new StringWriter(input.length() * 2);
            PrintWriter pw = new PrintWriter( sw );
            for( Block b : root.getChildren() ) {
                b.write(pw);
                pw.println();
            }
            pw.close();
            return sw.toString();
            
        } catch( XilizeException e ) {
            error(0, e.getMessage());
        }
        
        return null;
    }
    
    
    public void xilizeFile( File root, File file) {
        xilizeFile(root.getAbsolutePath(), file.getParent(), file.getAbsolutePath());
    }
    public void xilizeFile( String rootPath, String subDir, String filename) {
        File f = Files.localFile(subDir,rootPath);
        f = Files.localFile(filename, f.getAbsolutePath());
        if( !f.exists() ) {
            error("file "+f.getAbsolutePath()+" does not exist");
        }
        subtask = xilDir(rootPath, subDir, false);
        if( subtask==null )
            return;
        TaskDir sub = ((TaskDir)subtask).getSubDirTask(f.getParentFile());
        if( sub != null )
            sub.oneFile(f);
    }
    
    public void xilizeBranch( File root, File subdir) {
        xilizeBranch(root.getAbsolutePath(), subdir.getAbsolutePath());
    }
    public void xilizeBranch(String rootPath, String subDir) {
        subtask = xilDir(rootPath, subDir, true);
    }
    
    public void xilizeDirectory( File root, File subdir) {
        xilizeDirectory(root.getAbsolutePath(), subdir.getAbsolutePath());
    }
    public void xilizeDirectory(String rootPath, String subDir) {
        subtask = xilDir(rootPath, subDir, false);
    }
    
    public void xilizeProject(File root) {
        xilizeProject(root.getAbsolutePath());
    }
    
    public void xilizeProject(String path) {
        xilizeBranch(path, path);
    }
    
    public String getDescription() {
        return "xilize task";
    }
    
    public void xilize() throws XilizeException {
        throw new IllegalStateException("use one of the xilizeXXXX() methods");
    }
    
    private TaskDir xilDir(String rootPath, String subDir, boolean subSubDirsToo) {
        
        File root = new File(rootPath);
        define(Key._Root_, root.getAbsolutePath());
        if( !root.exists() ) {
            error("directory "+rootPath+" does not exist");
            resultCode = ExitCode.Fatal;
            return null;
        }
        if( !root.isDirectory() ) {
            error(rootPath+" is not a directory");
            resultCode = ExitCode.Fatal;
            return null;
        }
        File branch = Files.localFile(subDir, rootPath);
        if( !branch.exists() ) {
            error("directory "+subDir+" does not exist");
            resultCode = ExitCode.Fatal;
            return null;
        }
        if( !branch.isDirectory() ) {
            error(subDir+" is not a directory");
            resultCode = ExitCode.Fatal;
            return null;
        }
        if( !Files.isSubDir(root,branch) ) {
            error(subDir+" is not a subdirectory of "+rootPath+"");
            resultCode = ExitCode.Fatal;
            return null;
        }
        
        String dirPath = Files.trimDirPath(branch);
        String rPath = Files.trimDirPath(root);
        
        String[] root2dirRelativePathArray = null;
        int maxDepth = 0;
        if( rPath.equals(dirPath) && subSubDirsToo ) {
            maxDepth = TaskDir.ALL;
        } else if( !rPath.equals(dirPath) ){
            String reldir = dirPath.substring(rPath.length()+1);
            root2dirRelativePathArray = reldir.split(Pattern.quote(File.separator));
            maxDepth = subSubDirsToo? TaskDir.ALL : root2dirRelativePathArray.length;
        }
        
        TaskDir td = new TaskDir(this, root);
        td.makeTree(0, maxDepth, root2dirRelativePathArray);
        return td;
    }

    public ExitCode getResultCode() {
        return resultCode;
    }

}

