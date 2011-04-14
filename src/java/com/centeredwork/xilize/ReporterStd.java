/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

/**
 * ReporterStd implements a console oriented interface suitable
 * for running Xilize from the command line or within tools that capture the 
 * standard output streams.  Messages are formatted conventionally.  That is 
 * tools like NetBeans and jEdit's Console plugin will jump to file/line locations
 * on warnings and errors.
 */
public class ReporterStd implements Reporter {
    
    private long startTime = System.currentTimeMillis();
    
    protected int errors;
    protected int warnings;
    
    public ReporterStd() {
    }

    public long getLifeTime() { return System.currentTimeMillis() - startTime; }
    
    public void debug(Object o) {
        System.out.println("<debug> "+o);
    }

    public void error( Object o ) {
        errors++;
        System.out.println(o);
    } 
    
    public void report(Object o) {
        System.out.println(o);
    }

    public void warn(Object o) {
        warnings++;
        System.out.println(o);
    }

    public int getErrors() {
        return errors;
    }

    public int getWarnings() {
        return warnings;
    }

    public Reporter newInstance() {
        return new ReporterStd();
    }
    
}
