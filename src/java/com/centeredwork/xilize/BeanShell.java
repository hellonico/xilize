package com.centeredwork.xilize;
/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */
 
/**
 *
 * @author Andy Streich
 */

import bsh.*;
import bsh.NameSource.Listener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wrapper for the BeanShell interpreter.
 *
 */
public class BeanShell {
    
    private Interpreter interpreter;
    
    public BeanShell() {
        init();
    }
    
	public BeanShell newInstance() { return new BeanShell(); }
	
    protected void setInterpreter(Interpreter i) {
        interpreter = i;
    }
    protected void init() {
        
        interpreter = new Interpreter();
        NameSpace xilns = interpreter.getNameSpace();
        xilns.importPackage("com.centeredwork.xilize");
        xilns.importPackage("java.util.regex");
    }

    void set( String name, Object value ) {
        try {
            interpreter.set(name, value);
        } catch (EvalError ex) {
            ex.printStackTrace();
        }
    }
    
    void source( Task task, File file ) {
        
        // todo: finish this
        try {
            interpreter.source(file.getAbsolutePath());
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (EvalError e) {
            
            String msg = null;
            String lineNum = "0";
            String sourceFile = file.getAbsolutePath();
            if( e instanceof TargetError ) {
                TargetError te = (TargetError)e;
                msg = te.printTargetError(te.getTarget());
            } else if( e instanceof ParseException ) {
                ParseException pe = (ParseException)e;
                msg = pe.getMessage();// + "::"+pe.toString();
                Matcher m = Pattern.compile("Parse error at line (\\d+)").matcher(msg);
                if( m.find() ) {
                    lineNum = m.group(1);
                }
            } else {
                msg = e.getMessage();
            }
            task.report(sourceFile+":"+lineNum+":"+msg);
            
        }
        
    }
    
    Object eval(String statements) throws EvalError {
        return interpreter.eval(statements);
    }
    
    String exec(Task task, int startLine, String statements) throws EvalError {
        
        Object result = null;
        try {
            
            result = interpreter.eval(statements);
            
        } catch (EvalError e) {
            
            String msg = e.getMessage();
            
            // bshLine is handled this way because ParseException.getErrorLineNumber() throws
            // a null pointer exception
            int bshLine = -1;
            
            if( e instanceof TargetError ) {
                
                TargetError te = (TargetError)e;
                msg = te.getTarget().getMessage();
                bshLine = e.getErrorLineNumber();
                
            } else if( e instanceof ParseException ) {
                
                //parser errors have the form:
                //      Parse error at line 2, column 31.  Encountered: ...
                //and this is the only way to get the line number information
                Matcher m2 = Pattern.compile("^Parse error at line (\\d+)").matcher(msg);
                if( m2.find() )
                    bshLine = new Integer(m2.group(1));
                
            } else {
                
                bshLine = e.getErrorLineNumber();
                
                // another little hack to provide only relevant information
                //      Sourced file: inline evaluation of: ``Object o = new Object(); o.x();'' : ...
                // we known the "sourced file" is a string not a file
                final String msgPrefix = "Sourced file: inline evaluation of: ``";
                final String token = ";'' : ";
                
                if( msg.startsWith(msgPrefix)) {
                    int n = msg.indexOf(token);
                    msg = msg.substring(n + token.length());
                }
                
            }
            
            int scriptLine = startLine + bshLine;
            task.error( scriptLine, msg);
            throw e;
        }
        
        return result==null? "": result.toString();
    }
    
}