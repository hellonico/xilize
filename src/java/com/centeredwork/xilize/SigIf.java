/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

import bsh.EvalError;
import java.util.ArrayList;
import java.util.regex.*;

/**
 * Xilize markup:
 *
 *  <pre>
 *  if. beanshell-code {{
 *
 *      do these blocks if the beanshell-code evaluates to true
 *
 *      else. {{
 *          do the else-child blocks
 *      }}
 *   }}
 *  </pre>
 *
 *  <p> "else" clause is optional
 *
 * @author Andy Streich
 */
public class SigIf extends Signature {
    
    public SigIf() {
        super("if");
    }
    
    public void exec(Task task, Block block) throws XilizeException {
        
        if( !block.isParent()) {
            task.error(block.getLineNumber(), "child blocks required (used when condition is true)");
            return;
        }
        
        String code = block.linesAsString();
        BeanShell bsh = task.getBsh();
        String result = null;
        
        try {
            bsh.set("task", task);
            result = bsh.exec(task, block.getLineNumber(), code);
            
            boolean isTrue = new Boolean(result);
            ArrayList<Block> kids = block.getChildren();
            Block last = kids.get(kids.size()-1);
            if( isTrue ) {
                if( last.getSignature().getName().equals("else") ) {
                    kids.remove(kids.size()-1);
                }
            } else {
                if( last.getSignature().getName().equals("else") ) {
                    block.setChildren(last.getChildren());
                } else {
                    block.setChildren(null);
                }
            }
        } catch ( EvalError e ) {
            
            throw new XilizeException(task, block.getLineNumber(), e.getMessage());
            
        } finally {
            bsh.set("task", null);
        }
        
    }
    
    public String translate(Task task, Block block) {
        if( block.getChildren()!=null ) {
            for( Block c : block.getChildren() ) {
                c.translate();
            }
        }
        return null;
    }
    
    public static class Def extends Signature {
        
        boolean negate;
        
        Def(String name, boolean negate) {
            super(name);
            this.negate = negate;
        }
        
        
        private static Pattern pattern = Pattern.compile(" *(\\S+) +(.*)");
        
        public void exec(Task task, Block block) throws XilizeException {
            
            if( !block.isParent()) {
                Matcher m = pattern.matcher(block.getLine(0));
                if( !m.matches() ) {
                    task.error(block.getLineNumber(), "must have key and text for true condition");
                } else {
                    if(
                            (negate && !task.isDefined(m.group(1)))
                            || (!negate && task.isDefined(m.group(1))) ) {
                        
                        block.morph(m.group(2));
                    }
                }
                return;
            }
            
            String line = "if. "
                    +(negate? "!":"")
                    +"task.isDefined(\""+block.linesAsStringTrim()+"\")";
            
            block.morph(line);
        }
        
        public String translate(Task task, Block block) {
            if( block.getChildren()!=null ) {
                for( Block c : block.getChildren() ) {
                    c.translate();
                }
            }
            return null;
        }
        
    }
}