/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Instances of TaskDir are associated with local directories and as such control
 * the information available to their child file and subdirectory tasks.
 */
public class TaskDir extends TaskFile {
    
    /**
     * indicates all subdirectories are to be processed.
     */
    public static final int ALL = -1;
    
    /**
     * a string of the form "../../" that provides a path to the project's root directory,
     * if any.  Used in natural mode only.
     */
    protected String relProjectRoot = "";
    private ArrayList<TaskFile> sources = new ArrayList<TaskFile>();
    private ArrayList<TaskDir> subdirs;
    private ArrayList<String> ordering;
    private int depth;
    
    /**
     * creates and instance of TaskDir for a particular directory.
     * @param parent the parent task
     * @param dir the directory associated with this task
     */
    public TaskDir(Task parent, File dir) {
        
        super(parent,dir);
        
        if( parent instanceof Xilize2 || getLocalFile("root.xilconfig") != null )
            root = true;
        
        report(file.toString());
        
        if( isNatural() ) naturalInit();
        
        // setup directory specific info
        
        define(Key._DirLabel_, file.getName());
        
        File dirConfigFile = getLocalFile("dir.xilconfig");
        if( dirConfigFile != null ) {
            try {
                include(0, dirConfigFile);
            } catch (XilizeException ex) {
                error("problem in dir.xilconfig", ex);
            }
        }
        define(Key._DirPath_, file.getAbsolutePath());
        define(Key._DirName_, file.getName());
        
        if( !isRoot() ) {
//        if( isDefined(Key._NotRoot_) ) {
            // this is not project root directory
            String sep = value(Key._DirLabelSeparator_);
            String label = value(Key._DirLabel_);
            String labelList = value(Key._DirLabelList_);
            String[] dirLabels = labelList.split(Pattern.quote(sep));
            
            // make a link to each of the dir labels
            
            StringBuilder sb = new StringBuilder();
            String path = value("_ProjectRoot_");
            sb.append("[\""+dirLabels[0]+"\":"+path+"index.html]");
            for( int i=1; i<dirLabels.length; i++ ) {
                path = path.substring(3);
                sb.append(sep);
                sb.append("[\""+dirLabels[i]+"\":"+path+"index.html]");
            }
            sb.append(sep);
            sb.append(value(Key._DirLabel_));
            define(Key._DirLabelListLinked_, sb.toString());
            
            defineAppend(Key._DirLabelList_, sep + label);
            
        } else {
            define(Key._DirLabelList_, value(Key._DirLabel_));
            define(Key._DirLabelListLinked_, value(Key._DirLabel_));
            
        }
        
        // load any beanshell files
        
        // todo: control with a key definition that may be placed in the xildir.config file
        ArrayList<File> bshFiles = Files.listFiles(file, ".*\\.bsh$");
        for( File f : bshFiles ) {
            getBsh().source(this, f);
        }        
    }
    
    private boolean root;
    
    public boolean isRoot() {
        return root;
    }
    
    private void naturalInit() {
        
        if( isRoot() ) {
            define(Key._ProjectRoot_, "./");
        } else {
            defineAppend(Key._ProjectRoot_, "../");
//            define(Key._NotRoot_, "true");            
        }
        
//        if( parent instanceof TaskDir ) {
//            // this is not the project root directory, it's a subdirectory
//            defineAppend(Key._ProjectRoot_, "../");
//            define(Key._NotRoot_, "true");
//        } else {
//            define(Key._ProjectRoot_, "");
//        }
        
        natIncDef(Key.commoninc, "common.xilinc");
        natIncDef(Key.headerinc, "header.xilinc");
        natIncDef(Key.footerinc, "footer.xilinc");
        
        ArrayList<File> cssFiles = Files.listFiles(file, ".*\\.css");
        File defcss = new File( file, "default.css");
        if( cssFiles.contains(defcss) ) {
            define(Key.css, "default.css");
        } else if( cssFiles.size() == 1 ) {
            define(Key.css, cssFiles.get(0).getName());
        } else {
            defineNaturally(Key.css);
        } // else there is no Key.css definition available to files in this directory
        
    }
    
    protected void defineNaturally(Key key) {
        if( parentHasDefined(key) ) {
            define( key, "../"+value(key));
        }
    }
    
    private void natIncDef( Key key, String filename ) {
        
        File f = getLocalFile(filename);
        if( f == null ) {
            defineNaturally(key);
        } else {
            define(key, f.getName());
        }
    }
    
    public void xilize() throws XilizeException {
        
        // xilize files in this directory and its subdirectories
        
        if( getSources() != null ) {
            
            // establish page order            
            
            File pageOrder = getLocalFile("page.xilconfig");
            if( pageOrder == null ) {
                
                // use a sorted list of source files
                ArrayList<File> files = Files.listFiles(file,".*\\.xil$");
                ordering = new ArrayList<String>(files.size());
                for( File f : files )
                    getOrdering().add(f.getName());
                
            } else {
                
                // use the page.xil file
                try {
                    String[] sa = Files.read(pageOrder).replaceAll("[ \t]+", "").split("\\s+");
                    ordering =  new ArrayList<String>(Arrays.asList(sa));
                } catch (IOException ex) {
                    error("reading page order file", ex);
                }
            }
            
            for( TaskFile tf : sources ) {
                
                // set up prev and next keys
                
                String outext = value(Key._OutputExtension_);
                String xilfile = tf.file.getName();
                int i = getOrdering().indexOf(xilfile);
                define(Key._PagesTotal_, String.valueOf(getOrdering().size()));
                
                if( i != -1) {
                    
                    define(Key._PageNumber_, String.valueOf(i+1));
                    
                    if( getOrdering().size() == 1) {
                        ;  // there is no previous or next page
                    } else if( getOrdering().size() == 2) {
                        if( i==0 ) {
                            tf.define(Key._Prev_, in2out(getOrdering().get(1), outext));
                            tf.define(Key._Next_, in2out(getOrdering().get(1), outext));
                        } else {
                            tf.define(Key._Prev_, in2out(getOrdering().get(0), outext));
                            tf.define(Key._Next_, in2out(getOrdering().get(0), outext));
                        }
                    } else if( i == 0 ) {
                        tf.define(Key._Prev_, in2out(getOrdering().get(getOrdering().size() -1), outext));
                        tf.define(Key._Next_, in2out(getOrdering().get(1), outext));
                    } else if( i== getOrdering().size() -1 ) {
                        tf.define(Key._Prev_, in2out(getOrdering().get(getOrdering().size() -2), outext));
                        tf.define(Key._Next_, in2out(getOrdering().get(0), outext));
                    } else {
                        tf.define(Key._Prev_, in2out(getOrdering().get(i-1), outext));
                        tf.define(Key._Next_, in2out(getOrdering().get(i+1), outext));
                    }
                }
                
                tf.xilize();
            }
        }
        
        // xilize subdirectories
        if( getSubdirs() != null ) {
            for( TaskDir td : subdirs ) {
                td.xilize();
            }
        }
        
    }
    
    private static Pattern outfilePattern = Pattern.compile("^(.*\\.)xil$");
    
    private String in2out(String filename, String outext) {
        return outfilePattern.matcher(filename).replaceAll("$1"+outext);
    }
    
    /**
     * creates file and subdirectory tasks for children of this directory.
     *
     * @param curDepth     current curDepth
     * @param maxDepth  max curDepth to scan: TaskDir.ALL means unlimited, 0 means just this dir
     * @param dirList   path to first directory to scan for files and subdirectories
     */
    void makeTree( int curDepth, int maxDepth, String[] dirList ) {
        
        depth = curDepth;
        
        if( dirList == null || curDepth >= dirList.length ) {
            ArrayList<File> files = Files.listFiles(file,".*\\.xil$");
            for( File f : files ) {
                getSources().add( new TaskFile(this, f));
            }
        }
        
       // get subdirectories
        ArrayList<File> dirs = null;
        if( dirList == null || curDepth >= dirList.length ) {
            dirs  = Files.listDirs(file, ".*");
        } else {
            dirs  = Files.listDirs(file, Pattern.quote(dirList[curDepth]));
        }
        if( dirs.size() == 0 )
            return;
        
        // filter subdirectories
        
        String include = value(Key._DirInclude_);
        String exclude = value(Key._DirExclude_);
        
        if( !include.equals("") ) {
            String[] sa = include.split("\\s*,+\\s*");
            for( int i = 0; i<dirs.size(); ) {
                String dirname = dirs.get(i).getName();
                if( !contains( sa, dirs.get(i).getName()) ) 
                    dirs.remove(i);
                else
                    i++;
            }
        }
        
        if( !exclude.equals("") ) {
            String[] sa = exclude.split("\\s*,+\\s*");
            for( int i = 0; i<dirs.size(); ) {
                String dirname = dirs.get(i).getName();
                if( contains( sa, dirs.get(i).getName()) ) 
                    dirs.remove(i);
                else
                    i++;
            }
        }
        
        // create sub directory tasks
        
        subdirs = new ArrayList<TaskDir>(dirs.size());

        for( File f : dirs ) {
            getSubdirs().add(new TaskDir( this, f ));
        }
        
        // create define sub dir lists
        
        StringBuilder sb = new StringBuilder();
        StringBuilder sbl = new StringBuilder();
        String sep = value(Key._SubDirListSeparator_);
        for( TaskDir td : subdirs ) {
            sb.append(td.value(Key._DirLabel_) + sep);
            sbl.append("[\""+td.value(Key._DirLabel_)+"\":"
                    +td.value(Key._DirName_)+"/index.html]" + sep);
        }
        define(Key._SubDirList_, sb.toString().trim());
        define(Key._SubDirListLinked_, sbl.substring(0,sbl.length()-sep.length()+1));
        
        if( !(maxDepth == 0 || (maxDepth > 0 && curDepth >= maxDepth)) ) {
            for( TaskDir td : subdirs ) {
                td.makeTree(curDepth+1, maxDepth, dirList);
            }
        }
                
     }
    
    public String getDescription() {
        return "directory "+getPath();
    }
    
    public File getLocalFile(String filename) {
        File f = new File( file, filename );
        return f.exists()? f : null;
    }
    
    public ArrayList<TaskFile> getSources() {
        return sources;
    }
    
    public int pageCount() {
        return sources == null? 0 : sources.size();
    }
    
    public ArrayList<TaskDir> getSubdirs() {
        return subdirs;
    }
    
    public boolean hasSubDirs() {
        return subdirs == null? false : subdirs.size() != 0;
    }
    
    public ArrayList<String> getOrdering() {
        return ordering;
    }
    
    public String pageListLinked(Task task) {
        int pn = new Integer(task.value(Key._PageNumber_));
        String outext = value(Key._OutputExtension_);
        
        StringBuilder sb = new StringBuilder();
        for( int i=1; i <= ordering.size(); i++) {
            if( i==pn ) {
                sb.append("%(curpage) "+i+"%");
            } else {
                sb.append("\""+i+"\":"+in2out(ordering.get(i-1), outext));
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }
    
    public int getDepth() {
        return depth;
    }
    
    TaskDir getSubDirTask(File dir) {
        if( file.equals(dir) )
            return this;
        for(TaskDir sub : subdirs ) {
            TaskDir sd = sub.getSubDirTask(dir);
            if(  sd != null ) {
                return sd;
            }                
        }
        return null;
    }
    
    void oneFile(File target) {
        sources = new ArrayList<TaskFile>();
        sources.add(new TaskFile(this, target));
    }

    
    CatalogListener getCatalog() {
        return catalog;
    }

    private boolean contains(String[] sa, Object object) {
        for( int i=0; i<sa.length; i++ ) {
            if( sa[i].equals(object))
                return true;
        }
        return false;
    }
    
}
