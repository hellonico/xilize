/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 *
 * @author Andy Streich
 */
public class SigToc extends Signature implements CatalogListener {
    
    private static final String HEADER_ID = "toc_entry";
    private static final String TOC_CLASS = "toc";
    
    private int minlevel = 1;
    private int maxlevel = 6;
    private String listtype = "*";
    private boolean open = true;
    private ArrayList<String> entries;
    
    private static final Pattern PATTERN =
            Pattern.compile(" *(?:(\\d)(?: +(\\d)(?: +(\\*|#))?)?)? *");
    
    public SigToc() {
        super("toc");
    }
    
    public String translate(Task task, Block block) {
        entries = new ArrayList<String>();
        task.addCatalogListener(this);
        Matcher m = PATTERN.matcher(block.linesAsString());
        if( m.matches() ) {
            if( m.group(1) != null ) {
                minlevel = new Integer(m.group(1));
                if( m.group(2) != null ) {
                    maxlevel = new Integer(m.group(2));
                    if( m.group(3) != null ) {
                        listtype = m.group(3);
                    }
                }
            }
        } else {
            task.error(block.getLineNumber(), "invalid TOC specification, using defaults");
        }
        return null;
    }
    
    public String translateLast(Task task, Block block) {
        
        if( entries.size() == 0 ) {
            task.warning(block.getLineNumber(), "TOC is empty");
            return "";
        }
        
        int lineNum = block.getLineNumber();
        Block b = new Block(task, lineNum, entries.get(0));
        for( int i=1; i<entries.size(); i++ ) {
            b.addLine(lineNum+i, entries.get(i));
        }
        b.translate();

        // pass on the modifiers from this signature to new list block
        getMods().setDefaultCssClass("toc");        
        b.setTranslation(insertAttributes(b.getTranslation()));
        return b.getTranslation();
    }
    
    private boolean isInteresting(int itemLevel) {
        
        if( !open ) return false;
        
        if( itemLevel < minlevel ) {
            open = false;   // note: this closes this TOC to further entries
            return false;
        }
        if( itemLevel > maxlevel )
            return false;
        
        return true;
    }
    
    public boolean hasInterest(TaskFile task, Catalog.Item item) {
        return isInteresting(item.getLevel());
    }
    
    public void entry(TaskFile task, Catalog.Item item) {
        int itemLevel = item.getLevel();
        if( !isInteresting(itemLevel) ) {
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        for( int i=minlevel-1; i<itemLevel; i++){
            sb.append(listtype);
        }
        sb.append(' ');
        sb.append("<a href=\"#" + item.getId() + "\">");
        sb.append(item.getText());
        sb.append("</a>");
        entries.add(sb.toString());
    }
    
    
}
