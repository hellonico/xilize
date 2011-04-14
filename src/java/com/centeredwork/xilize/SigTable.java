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
 * Represents the <CODE>table</CODE> signature and the shorthand sig <CODE>|</CODE>.
 */
public class SigTable extends Signature {
    
    private Task task;
    private Block block;
    
    boolean headerClosed;
    private ArrayList<StringBuilder> headerRows;
    private ArrayList<StringBuilder> rows;
    private ArrayList<StringBuilder> footerRows;
    
    SigTable() {
        super("table");
    }

    void setMods(Task task, String modifiers) {
        this.mods = new Modifiers.Table(task,modifiers);
    }
        
    public String translate(Task task, Block block) {
        
        headerRows = new ArrayList<StringBuilder>();
        rows = new ArrayList<StringBuilder>();
        footerRows = new ArrayList<StringBuilder>();
        
        this.task = task;
        this.block = block;
        block.setWriteChildren(false);
        StringBuilder buffer = new StringBuilder();
        
        // todo: handle rows as children in a more condensed form
        
        // todo: header/footer rows
        
        buffer.append("<table"+tagAttributes()+">"+colSpec(block)+"\n");
        
        if( block.isParent() ) {
            
            for( Block child : block.getChildren() ) {
                
                if( child.isParent() ) {
                    row(child);
                } else {
                    // todo: add warning if multiline
                    // todo: add warning if mods on sig and at start of line
                    row(child.getLine(0));
                }
            }
            
        } else {
            
            for( String line : block.getLines() ) {
                row(line);
            }
        }
        
        if( !headerRows.isEmpty() ) {
            buffer.append("<thead>\n");
            for( StringBuilder s : headerRows ) {
                buffer.append(s);
            }
            buffer.append("</thead>\n");
        }
        
        if( !footerRows.isEmpty() ) {
            buffer.append("<tfoot>\n");
            for( StringBuilder s : footerRows ) {
                buffer.append(s);
            }
            buffer.append("</tfoot>\n");
        }
        
        if( !rows.isEmpty() ) {
            buffer.append("<tbody>\n");
            for( StringBuilder s : rows ) {
                buffer.append(s);
            }
            buffer.append("</tbody>\n");
        }
        
        buffer.append("</table>");
        
        return buffer.toString();
    }
    
    private static final String ROW_REGEX
            = "^ *("+Modifiers.REGEX_7+")(\\|.*)$";
    private static final Pattern ROW_PATTERN = Pattern.compile(ROW_REGEX);
    
    private void row( String line ) {
        
        StringBuilder buffer = new StringBuilder();
        Matcher m = ROW_PATTERN.matcher(line);
        if( !m.matches() ) {
            task.warning(block.getLineNumber(line), "expeciting a table row");
            return;
        }
        Modifiers mods = new Modifiers.Sig(task, m.group(1));
        
        buffer.append("  <tr"+mods.tagAttributes()+">\n");
        cells(buffer, m.group(9));
        buffer.append("  </tr>\n");
        
        addRow(mods, buffer);
    }
    
    private void addRow(Modifiers mods, StringBuilder sb) {
        if( mods.isHeader() ) {
            if( headerClosed )
                footerRows.add(sb);
            else
                headerRows.add(sb);
        } else {
            headerClosed = true;
            rows.add(sb);
        }
    }
    
    private void row( Block block ) {
        
        StringBuilder buffer = new StringBuilder();
        buffer.append("  <tr"+block.getSignature().getMods().tagAttributes()+">\n");
        for( Block child : block.getChildren() ) {
            cell(buffer, child);
        }
        buffer.append("  </tr>\n");
        addRow(block.getSignature().getMods(), buffer);
    }
    
    private static final String CELL_REGEX
            = "\\|("+Modifiers.REGEX_7+" )? *([^\\|]*)";
    private static final Pattern CELL_PATTERN = Pattern.compile(CELL_REGEX);
    
    private void cells( StringBuilder buffer, String line ) {
        Matcher m = CELL_PATTERN.matcher(line);
        while( m.find() ) {
            Modifiers.Cell mods = new Modifiers.Cell(task, m.group(1));
            writeCell(buffer, mods.tagAttributes(), m.group(9));
        }
    }
    
    private void writeCell(StringBuilder buffer, String mods, String contents) {
        buffer.append("    <td"+mods+">");
        buffer.append(task.markup(contents.trim()));
        buffer.append("</td>\n");
    }
    
    private static final String CHILD_CELL_REGEX
            = "(?:\\|("+Modifiers.REGEX_7+" )? *)?(.*)";
    private static final Pattern CHILD_CELL_PATTERN = Pattern.compile(CHILD_CELL_REGEX);
    
    
    private void cell( StringBuilder buffer, Block block ) {
        
        if( block.isParent() ) {
            
            buffer.append("    <td"+block.getSignature().tagAttributes()+">");
            buffer.append('\n');
            for( Block child : block.getChildren() ) {
                child.translate();
                buffer.append(child.getTranslation());
                buffer.append('\n');
            }
            buffer.append("    </td>\n");
            
        } else {
            
            if( block.getSignature().getName().equals(task.value(Key._UnsignedBlockSigName_)) ) {
                Signature sig = task.getSignature("imo");
                block.setSignature(sig);
            }
            buffer.append("    <td"+block.getSignature().tagAttributes()+">");
            buffer.append('\n');
            block.translate();
            buffer.append(block.getTranslation());
            buffer.append('\n');
            buffer.append("    </td>\n");
        }
    }
    
    private String colSpec(Block block) {
        
        if( block.getLines().isEmpty() || !block.getLine(0).startsWith("&{columns:") )
            return "";
        
        ArrayList<String> lines = block.getLines();
        
        StringBuilder sb = new StringBuilder();
        block.removeLine(0);
        int n = 1;
        while( n < lines.size() ) {
            String text = lines.get(0);
            block.removeLine(0);
            
            if( text.matches(" *\\} *") ) {
                
                break;
                
            } else {
                
                if( text.indexOf('|') == -1 ) {
                    
                    // no col elements, create an empty column group element
                    sb.append("\n<colgroup");
                    sb.append(getColMods(task, text));
                    sb.append(" />");
                    
                } else {
                    
                    sb.append("\n<colgroup");
                    int m = text.indexOf('|');
                    if( m > 0 ) { // colgroup modifiers are present
                        sb.append(getColMods(task, text.substring(0 , m)));
                    }
                    sb.append(">\n");
                    m =  m == -1 ? 1 : m + 1;
                    text += " "; // to account for empty last element see String.split()
                    String[] cols = text.substring(m).split("\\|");
                    for( int i=0; i<cols.length; i++ ) {
                        sb.append("  <col");
                        if( !cols[i].matches(" *") ) {
                            sb.append(getColMods(task, cols[i]));
                        }
                        sb.append(" />\n");
                    }
                    sb.append("</colgroup>");
                }
            }
        }
        return sb.toString();
    }
    
    private static String WIDTH_REGEX = "(\\d+(\\*|%)?)|\\*";
    private static Pattern WIDTH_PATTERN =
            Pattern.compile(" +(" +WIDTH_REGEX+ ")\\z");
    
    private static String getColMods( Task task, String text ) {
        text = text.trim();
        if( text.equals(""))
            return "";
        
        String width = null;
        String cgMods = null;
        
        // get width attribute if present
        if( text.matches(WIDTH_REGEX) ) {
            width = text;
        } else {
            Matcher m = WIDTH_PATTERN.matcher(text);
            if( m.find() ) {
                width = m.group(1);
                cgMods = text.substring(0,  m.start()).trim();
            } else {
                cgMods = text;
            }
        }
        
        Modifiers.TableCol mods = new Modifiers.TableCol(task, cgMods, width);
        return mods.tagAttributes();
//
//        // get the other modifiers
//        Spec spec = null;
//        if( cgMods != null && !cgMods.equals(""))
//            spec = BlockFactory.parseSpec( cgMods );
//        if( spec == null )
//            spec = new Spec();
//        spec.columns = true;
//        if( width != null )
//            spec.colWidth = width;
//        return spec.modString();
    }
    
}
