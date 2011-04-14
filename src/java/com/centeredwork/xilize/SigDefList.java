/*
 Copyright (c) 2006 Andy Streich.  This is private and confidential property
 may be used and/or distributed only by obtaining a valid license from Andy Streich,
 PO Box 1236, Trinidad, CA  95570 USA.  Email:  andy@centeredwork.com.
 */

package com.centeredwork.xilize;

import java.lang.IllegalStateException;
import java.util.ArrayList;

/**
 *
 * @author Andy Streich
 */
public class SigDefList  extends Signature {
    
    public SigDefList() {
        super("dl");
    }
    
    public String translate(Task task, Block block) {
        
        ArrayList<String> lines = block.getLines();
        StringBuilder buffer = new StringBuilder();
        buffer.append("<dl"+tagAttributes()+">\n");
        
        for( String line : lines ) {
            String[] terms = line.split("\\s+:\\s+");
            if( terms.length < 2 ) {
                throw new IllegalStateException("too few parts in definition list");
            }
            
            for(int i=0; i < terms.length - 1; i++) {
                String t = task.markup(terms[i]);
                buffer.append("  <dt>"+t+"</dt>\n");
//
//            int n = t.indexOf(". ");
//            if( n!=-1 ) {
//                spec = BlockFactory.parseSpec( t.substring(0,n) );
//                if( spec == null ) {
//                    spec = new Spec();
//                } else {
//                    t = t.substring(n+2);
//                }
//            }
//            items.add(new BlockTerm(spec, t) );
            }
            
            String[] def = terms[terms.length - 1].split("\\s+;\\s+");
            for(int i=0; i < def.length; i++) {
                String d = task.markup(def[i]);
                buffer.append("    <dd>"+d+"</dd>\n");
//                int n = d.indexOf(". ");
//                Spec spec = new Spec();
//                if( n!=-1 ) {
//                    spec = BlockFactory.parseSpec( d.substring(0,n) );
//                    if( spec == null ) {
//                        spec = new Spec();
//                    } else {
//                        d = d.substring(n+2);
//                    }
//                }
//                items.add(new BlockDef(spec, d) );
            }
            
        }
        buffer.append("</dl>");
        return buffer.toString();
    }
}
