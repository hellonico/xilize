/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */


package com.centeredwork.xilize;
import java.io.File;
import java.text.NumberFormat;
import java.util.HashMap;

/**
 * Main contains the entry points for running Xilize from the command line.
 * See {@link Main#main(String[]) main() } for command line syntax.
 */

public class Main {
    
    /**
     * The command line syntax:
     *
     * <PRE>    Xilize -env config_file
     *            ...target and mode in configuration file
     *    Xilize [-env config_file] file
     *            ...classic mode file translation
     *    Xilize [-env config_file] -cdir directory
     *            ...classic mode directory translation
     *    Xilize [-env config_file] -ctree directory
     *            ...classic mode directory tree translation
     *    Xilize [-env config_file] root
     *            ...natural mode project
     *    Xilize [-env config_file] root branch
     *            ...branch of natural mode project
     *    Xilize [-env config_file] root branch file
     *            ...file in natural mode project
     *    Xilize [-env config_file] -ndir root directory
     *            ...single directory of natural mode project</PRE>
     * @param args command line arguments
     */
    public static void main(String [] args) {
        
        if( args.length == 0 ) {
            quit("arguments required");
            return;
        }
        HashMap<String,String> map = new HashMap<String,String>();
        int i = 0;
        boolean dirOnly = false;
        boolean findRoot = false;
        
        
        
        while( i<args.length && args[i].startsWith("-")) {
            String s = args[i];
            if( s.matches("-h|--help") ) {
                usage();
                return;
            } else if( s.matches("-cf|--config-file") ) {
                if( i+1 >= args.length ) {
                    quit("config file must be specified with this option");
                    return;
                } else {
                    i++;
                    map.put(Key._XilizeConfigFile_.name(), args[i]);
                }
            } else if( s.matches("-do|--directory-only")) {
                dirOnly = true;
            } else if( s.matches("-fr|--find-root")) {
                findRoot = true;
            }
            i++;
        }
        
        File target = null;
        File qualifier = null;
        
        if( findRoot ) {
            if( i != args.length - 1 ) {
                quit("too few arguments");
                return;
            }
            qualifier = new File(args[i]);
            i++;
        } else {
            target = new File(args[i]);
            i++;
            if( i < args.length ) {
                qualifier = new File(args[i]);
                i++;
            }
        }
        if( i != args.length ) {
            quit("too many arguments");
            return;
        }
        
        
        if( target != null ) {
            if( !target.exists() ) {
                quit(target.getAbsolutePath()+" does not exist.");
                return;
            }
            if( qualifier != null ) {
                qualifier = Files.localFile(qualifier.getPath(), target.getAbsolutePath());
                if( !qualifier.exists() ) {
                    quit(qualifier.getAbsolutePath()+" does not exist.");
                    return;
                }
            }
        }
        
        if( findRoot ) {
            if( qualifier == null ) {
                quit("-fr option requires a target");
            }
            if( !qualifier.exists() ) {
                quit(qualifier.getAbsolutePath()+" does not exist.");
                return;
            }
            target = Files.findRoot(qualifier);
            if( target == null ) {
                quit("root not found starting from "+ qualifier.getAbsolutePath());
                return;
            }
        }
        
        Xilize2.startup(new ReporterStd(), new BeanShell(), map);
        
        Xilize2 x = new Xilize2();
        if( target == null && qualifier == null ) {
            ;
        } else if( qualifier == null ) {
            if( target.isDirectory() ) {
                if( dirOnly ) {
                    x.xilizeDirectory(target, target);
                } else {
                    x.xilizeBranch(target, target);
                }
            } else {
                x.xilizeFile(target.getParentFile(), target);
            }
        } else {
            if( !target.isDirectory() ) {
                quit(target.getAbsolutePath() +" is not a directory");
            } else if(qualifier.isFile()) {
                x.xilizeFile(target, qualifier);
            } else {
                if( dirOnly ) {
                    x.xilizeDirectory(target, qualifier);
                } else {
                    x.xilizeBranch(target, qualifier);
                }
            }
        }
        Xilize2.ExitCode code = x.translate();
        
        long time = x.getEnv().getReporter().getLifeTime();
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(1);
        nf.setMinimumFractionDigits(1);
        
        System.out.println("translated ("+nf.format(time/1000.)+" seconds)");
        
        Xilize2.shutdown();
        System.exit( x.getResultCode().getExitCode() );
        
    }
    
    private static void quit(String msg) {
        System.err.println(msg);
        System.err.println();
        usage(Xilize2.ExitCode.Usage.getExitCode());
    }
    
    private static void usage() {
        usage(Xilize2.ExitCode.Okay.getExitCode());
    }
    
    private static String NL = System.getProperty("line.separator");
    
    private static String USAGE =
            NL+"Arguments may have one of two forms:"+NL
            + "        -cf file"+NL
            + "or"+NL
            + "        [ options ] target [ qualifier ]"+NL
            + NL
            + "\"target\" is a file or directory tree to translate.  If it is the root of a"+NL
            + "project tree then \"qualifier\" may be a file or directory within that tree and"+NL
            + "translation will be restricted to its scope. \"qualifier\" may be expressed as a"+NL
            + "relative path from \"target\"."+NL
            + NL
            + "options:"+NL
            + NL
            + "| -cf file | --config-file file | reads configuration \"file\" before translation"+NL
            + "| -do      | --directory-only   | translate single directory only"+NL
            + "| -fr      | --find-root        | automatically locate root directory"+NL
            + "| -h       | --help             | this message"+NL
            + NL
            + "see http://www.centeredwork.com/xilize2 for more information"+NL;
    
    private static void usage(int exitCode) {
        System.err.println("Usage:");
        System.err.print(USAGE);
        System.exit(exitCode);
    }
}