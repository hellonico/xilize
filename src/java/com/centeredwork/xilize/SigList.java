/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents signatures for the <code>#</code> and <code>*</code> Xilize markup for lists.
 */
public class SigList extends Signature {
    
    private Task task;
    private Stack<List> stack = new Stack<List>();
    
    public SigList() {
        super("list");
    }
    
    private static final String LIST_REGEX
            = " *("+Modifiers.REGEX_7+")([*#]+)("+Modifiers.REGEX_7+" )? *(.*)?";
    
    private static final Pattern LIST_PATTERN = Pattern.compile(LIST_REGEX);
    
    public String translate(Task task, Block block) {
        
        this.task = task;
        block.setWriteChildren(false);
        
        List dummyHead = new List(0, '!', 0, null);
        stack.push( dummyHead );
        
        for( String line : block.getLines() ) {
            
            int lineNum = block.getLineNumber(line);
            Matcher m = LIST_PATTERN.matcher(line);
            if( !m.matches() ) {
                task.error(lineNum, "problem with list signature");
                continue;
            }
            
            Modifiers lmods = new Modifiers.Sig(task,m.group(1));
            int level = m.group(9).length();
            char type = m.group(9).charAt(0);
            Modifiers imods = new Modifiers.Sig(task,m.group(10));
            String text = m.group(18);
            
            Item it = new TextItem(lineNum,imods,text);
            
            if( stack.peek().level == level ) {
                
                stack.peek().add(it);
                
            } else if( stack.peek().level > level ) {
                
                do {
                    stack.pop();
                } while(stack.peek().level > level );
                stack.peek().add(it);
                
            } else {
                
                List top = stack.peek();
                
                // add any implied lists and items
                for( int lvl = top.level+1; lvl < level; lvl++) {
                    List ls = new List(lvl, type, lineNum, null);
                    top.add(ls);
                    stack.push(ls);
                    top = stack.peek();
                }
                
                // add the new sublist and its first item
                List list = new List(level, type, lineNum, lmods);
                list.add(it);
                stack.peek().add(list);
                stack.push(list);
            }
        }
        
        StringBuilder sb = new StringBuilder();
        dummyHead.firstSublist().write(0, sb);
        return sb.toString();
    }
    
    abstract private class Item {
        Modifiers mods;
        List sublist;
        int lineNum;
        
        Item(int ln, Modifiers mods) {
            lineNum = ln;
            this.mods = mods;
        }
        
        abstract void write(int indent, StringBuilder sb);
    }
    
    private class TextItem extends Item {
        String text;
        
        TextItem(int ln) {
            super(ln, null);
            this.text = "";
        }
        TextItem(int ln, Modifiers mods, String text) {
            super(ln, mods);
            this.text = task.markup(text);
        }
        void write(int indent, StringBuilder sb) {
            indent(indent, sb);
            sb.append("<li");
            if( mods!=null)
                sb.append(mods.tagAttributes());
            sb.append(">"+text);
            
            if( sublist != null ) {
                sb.append("\n");
                sublist.write(indent+1, sb);
                indent(indent, sb);
            }
            sb.append("</li>\n");
        }
        public String toString() {
            return text;
        }
    }
    
    private class List {
        int level;
        Modifiers mods;
        char type;
        int lineNum;
        ArrayList<Item> items = new ArrayList<Item>();
        
        List( int level, char type, int lineNum, Modifiers mods ) {
            this.level = level;
            this.mods = mods;
            this.type = type;
            this.lineNum = lineNum;
        }
        
        void add( Item it ) {
            items.add(it);
        }
        
        void add( List list ) {
            
            if( items.size() <= 0 ) {
                add( new TextItem(lineNum) );
//                add( new Item(lineNum, null) );
            }
            items.get(items.size()-1).sublist = list;
        }
        
        void write(int indent, StringBuilder sb) {
            indent(indent, sb);
            if( type=='#' ) sb.append("<ol"); else sb.append("<ul");
            if( mods!=null)
                sb.append(mods.tagAttributes());
            sb.append(">\n");
            for( Item it : items ) {
                it.write( indent+1, sb);
            }
            indent( indent, sb );
            if( type=='#' ) sb.append("</ol>"); else sb.append("</ul>");
            sb.append('\n');
        }
        
        public String toString() {
            return type+"["+level+":"+lineNum+"]"+items.size();
        }
        List firstSublist() {
            return items.get(0).sublist;
        }
    }
    
    private static void indent( int indent, StringBuilder sb ) {
        for( int i=0; i<indent; i++ ) {
            sb.append("  ");
        }
    }
}

