/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Static file-oriented, utility methods.
 */
public class Files {

    private static final String ROOT_CONFIG = "root.xilconfig";
    private static final String DIR_CONFIG = "dir.xilconfig";
    
    private Files() {}
    
    /**
     * converts backslash file separators to forward slashs appropriate for URL's.
     * @param path input path string
     * @return path with <CODE>\</CODE> changed to <CODE>/</CODE>
     */
    public static String normalizePath(String path) {
        return path.replace('\\', '/');
    }
    
    private static final String USELESS_PATHEND = File.separator+".";
    
    public static String trimDirPath(File dir) {
            String s = dir.getAbsolutePath();
            if( s.endsWith(USELESS_PATHEND) )
                return s.substring(0, s.length()-USELESS_PATHEND.length());
            return s;
    }
    /**
     * creates a file object for a <I>path</I> that may or may not be absolute.  If
     * not absolute, <I>dir</I> is used as its parent directory.
     * @param path a relative or absolute path
     * @param dir an absolute path
     * @return reference to a file object for <I>path</I> if <I>path</I> is absolute or to
     * <I>dir/path</I> if not
     */
    public static File localFile( String path, String dir ) {
        return isAbsolute( path ) ?
            new File(path) :
            new File(dir + File.separator + path);
    }
    
    /** this is a patch for odd behavior under Windows, unnessary on *nix.
     *
     * @param path relative or absolute path
     * @return true if path is absolute
     */
    public static boolean isAbsolute(String path) {
        // the match statement was necessary for Windows, File.isAbsolute()
        // didn't recognize \a\b as absolute, Java 1.4 issue?
        // no problems on Linux
        return path.matches("(([a-zA-Z]:)|(/|\\\\)).*");
    }
    
    /**
     * Tests if "sub" is a subdirectory of the given "parent" directory by
     * comparing their corresponding absolute path strings.
     *
     * @param parent parent directory
     * @param sub subdirectory
     * @return true if parent is an ancestor or sub
     */
    public static boolean isSubDir(File parent, File sub) {
        String p = parent.getAbsolutePath();
        return sub.getAbsolutePath().regionMatches(0,p,0,p.length());
    }
    
    public static File findRoot(File file) {
        
        if( file == null )
            return null;
        
        if( file.isDirectory() ) {
            File root = new File(file, ROOT_CONFIG);
            if( root.exists() )
                return root.getParentFile();
        }
        return findRoot( file.getParentFile() );
    }
    
    public static File findTopDirConfig(File file) {
        
        if( file == null )
            return null;
        
        if( file.isDirectory() ) {
            File dirConfig = new File(file, DIR_CONFIG);
            if( dirConfig.exists() ) {
                File top = findTopDirConfig(file.getParentFile());
                return top == null ?  dirConfig.getParentFile() : top;
            }
        }
        return findTopDirConfig( file.getParentFile() );
    }
    
    public static File getProjectRoot(File file) {
        
        if( file == null )
            return null;
        
        File root = findRoot(file);
        if( root != null )
            return root;
        
        return findTopDirConfig(file);       
        
    }
    
    /**
     * reads a text file
     *
     * @param path file path
     * @throws java.io.IOException same as FileReader
     * @throws java.io.FileNotFoundException same as FileReader
     * @return the file contents
     */
    public static String read( File path ) throws IOException, FileNotFoundException {
        
        Reader reader = new BufferedReader(new FileReader(path));
        StringBuilder sb = new StringBuilder((int)path.length());
        int c = -1;
        while((c=reader.read()) != -1 ) {
            sb.append((char)c);
        }
        reader.close();
        return sb.toString();
    }
    
    /**
     * Reads a file into a String with optional line wrapping and leading/trailing 
     * newlines removed.
     *
     * @param path file to read
     * @param wrap column at which to wrap lines, use 0 for no wrapping
     * @throws java.io.IOException same as FileReader
     * @throws java.io.FileNotFoundException same as FileReader
     * @return the file contents with trailing newlines removed
     */
    public static String read( File path, int wrap ) throws IOException, FileNotFoundException {
        
        BufferedReader reader = new BufferedReader(new FileReader(path));
        StringBuilder sb = new StringBuilder((int)path.length());
        String line = null;
        while((line = reader.readLine()) != null ) {
            if( wrap < 1 ) {
                sb.append(line);
                continue;
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
        reader.close();
        
        // trim leading and trailing new lines
        int n = sb.length();
        while( --n > 0 && sb.charAt(n) =='\n') ;
        int m = 0;
        while(m < sb.length() && sb.charAt(m) =='\n') m++;
        return sb.toString().substring(m, n+1);
    }
    
    /**
     * gets a sorted list of files matching <I>regex</I>.
     * @param path directory to look at
     * @param regex pattern to match
     * @return sorted list of matching file objects
     */
    public static ArrayList<File> listFiles( File path, final String regex ) {
        File[] files = path.listFiles( new FileFilter() {
            public boolean accept(File file) {
                return !file.isDirectory() && file.getName().matches( regex );
            }
        });
        Arrays.sort(files);
        return new ArrayList<File>(Arrays.asList(files));
    }
    
    /**
     * gets a sorted list of directories matching <I>regex</I>.
     * @param path directory to look at
     * @param regex pattern to match
     * @return sorted list of matching directories.
     */
    public static ArrayList<File> listDirs( File path, final String regex ) {
        File[] dirs = path.listFiles( new FileFilter() {
            public boolean accept(File file) {
                return file.isDirectory() && file.getName().matches( regex );
            }
        });
        Arrays.sort(dirs);
        return new ArrayList<File>(Arrays.asList(dirs));
    }
    
    
}
