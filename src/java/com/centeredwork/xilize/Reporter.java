/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

/**
 * Interface for informational, debug, warning, and error output.  When Xilize2 is 
 * used within another tool (for instance and editor like jEdit) a suitable
 * implementation of this interface must be provided.
 * @see ReporterStd ReporterStd implements a console oriented interface suitable
 * for running Xilize from the command line.
 */
public interface Reporter {
    
    public Reporter newInstance();
    public void debug( Object o );
    public void error(Object o);
    public void report( Object o );
    public void warn( Object o );
    public int getErrors();
    public int getWarnings();
    public long getLifeTime();
}
