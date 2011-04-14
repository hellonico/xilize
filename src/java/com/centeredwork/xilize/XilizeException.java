/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

/**
 *
 * @author Andy Streich
 */
public class XilizeException extends Exception {
    
    private Xilize2.ExitCode code = Xilize2.ExitCode.Okay;
    
    public XilizeException(Xilize2.ExitCode code, Task task, Throwable cause) {     
        super(task.getDescription() +": " +cause.getMessage(), cause);
        this.code = code;        
    }
    
    public XilizeException(Xilize2.ExitCode code, Task task, String msg) {        
        super(task.getDescription() +": " +msg);
        this.code = code;        
    }
    
    public XilizeException(Task task, int lineNumber, String msg) {        
        super(task.getPath()+":"+lineNumber+": "+msg);
    }
    
    public Xilize2.ExitCode getCode() { return code; }
    
}
