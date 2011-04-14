package com.centeredwork.xilize;

import com.centeredwork.xilize.Regex.Trans;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * base class for translating Xilize modifier strings.  See nested classes.
 *
 * (todo:  convert this to a set of static methods.)
 */
abstract public class Modifiers {
    
    enum Halign { left, right, center, both, unassigned };
    enum Valign { top, middle, bottom, unassigned };
    
    protected boolean changed;
    
    protected Task task;
    
    protected String mods;
    protected String remaining;
    
    protected String arbTagAttr = "";
    protected String style = "";
    protected String cssClass = "";
    protected String id = "";
    protected String lang = "";
    protected Halign halign = Halign.unassigned;        // left, right, center, justify;
    protected int lpad;
    protected int rpad;
    
    // for images and table cells
    protected Valign valign = Valign.unassigned;        // top, bottom (for cells and rows), middle for images
    
    // these only apply to table markup
    protected String colspan = "";
    protected String rowspan = "";
    
    
    private boolean image;          // see halign, valign below
    private boolean table;          // set by BlockTable
    private boolean header;         // thead row or th cell
    private boolean columns;        // for {{columns: ...} markup
    public String colWidth = "";
    
    protected Modifiers(Task task, String mods) {
        this.task = task;
        this.mods = mods==null? "" : mods.trim();
        
        if( this.mods.equals("") )
            return;
        
        parse();
    }
    
    public String toString() {
        return mods;
    }
    
    /**
     * for reporting errors to the user.
     * @return string of characters from the original string which were not consumed in parsing.
     */
    public String getRemaining() { return remaining; }
    
    //  (...#...)
    private static final String CLASS_ID_REGEX = "\\(((\\w|[#,-]|\\d)+)\\)";
    private static final Pattern CLASS_ID_PATTERN = Pattern.compile(CLASS_ID_REGEX);
    
    //  {{...}}
    private static final String ARB_TAG_ATT_REGEX = "\\{\\{([\\S &&[^}]]+)\\}\\}";
    private static final Pattern ARB_TAG_ATT_PATTERN = Pattern.compile(ARB_TAG_ATT_REGEX);
    
    //  {...}
    private static final String STYLE_REGEX = "\\{([\\S &&[^}]]+)\\}";
    private static final Pattern STYLE_PATTERN = Pattern.compile(STYLE_REGEX);
    
    //  [...]
    // see http://www.w3.org/International/articles/language-tags/
    private static final String LANG_REGEX = "\\[([a-zA-Z0-9-]+)\\]";
    private static final Pattern LANG_PATTERN = Pattern.compile(LANG_REGEX);
    
    //  \n  or  /n
    private static final String COL_SPAN_REGEX = "\\\\(\\d+)";     
    private static final String ROW_SPAN_REGEX = "/(\\d+)";        
    
    private static final String SYMS_REGEX = "_|-|\\^|~|>|<|=|\\(+|\\)+";
    
    /**
     * regular expression used by serveral other classes to identifier modifier strings
     * and used by this class for parsing.
     */
    public static final String REGEX_7 =
            "(?:"+CLASS_ID_REGEX+"|"+STYLE_REGEX+"|"+LANG_REGEX+"|"+ARB_TAG_ATT_REGEX
            +"|"+COL_SPAN_REGEX+"|"+ROW_SPAN_REGEX+"|"+SYMS_REGEX+")*";
    
    protected void parse() {
        if( mods==null || mods.equals("") )
            return;
        
        remaining = mods;
        
        // class and id, (classname#id)
        if( remaining.indexOf('(') != -1 ) {
            Matcher m = CLASS_ID_PATTERN.matcher(remaining);
            if( m.find() ) {
                String found = m.group(1);
                int hash = found.indexOf('#');
                id = hash == -1 ? "" : found.substring(hash+1);
                cssClass = hash == -1 ? found : found.substring(0,hash);
                remaining = remaining.substring(0,m.start()) + remaining.substring(m.end());
            }
        }
        
        if( remaining.length() == 0 )
            return;
        
        // arbitrary tag attributes {{...}}
        if(remaining.indexOf("{{") != -1 ) {
            Matcher m = ARB_TAG_ATT_PATTERN.matcher(remaining);
            if( m.find() ) {
                arbTagAttr = m.group(1);
                remaining = remaining.substring(0,m.start()) + remaining.substring(m.end());
            }
        }
        
        if( remaining.length() == 0 )
            return;
        
        // style attribute {...}
        if(remaining.indexOf("{") != -1 ) {
            Matcher m = STYLE_PATTERN.matcher(remaining);
            if( m.find() ) {
                style = m.group(1);
                if( !style.endsWith(";") ) style += ';';
                remaining = remaining.substring(0,m.start()) + remaining.substring(m.end());
            }
        }
        
        if( remaining.length() == 0 )
            return;
        
        // lang attribute [...]
        if(remaining.indexOf("[") != -1 ) {
            Matcher m = LANG_PATTERN.matcher(remaining);
            if( m.find() ) {
                style = m.group(1);
                remaining = remaining.substring(0,m.start()) + remaining.substring(m.end());
            }
        }
        
        if( remaining.length() == 0 )
            return;
        
        // left and right padding, count ( and )
        if( remaining.indexOf('(') != -1 || remaining.indexOf(')') != -1) {
            StringBuilder sb = new StringBuilder();
            for( int i=0; i<remaining.length(); i++ ) {
                char c = remaining.charAt(i);
                switch(c) {
                    case '(': lpad++; break;
                    case ')': rpad++; break;
                    default: sb.append(c);
                }
            }
            remaining = sb.toString();
        }
        
        if( remaining.length() == 0 )
            return;
        
        // horizontal markers
        int n = remaining.indexOf("<>");
        if( n != -1 ) {
            halign = Halign.both;
            remaining = remaining.substring(0,n) + remaining.substring(n+2);
        }
        n = remaining.indexOf('<');
        if( n != -1 ) {
            halign = Halign.left;
            remaining = remaining.substring(0,n) + remaining.substring(n+1);
        }
        n = remaining.indexOf('>');
        if( n != -1 ) {
            halign = Halign.right;
            remaining = remaining.substring(0,n) + remaining.substring(n+1);
        }
        n = remaining.indexOf('=');
        if( n != -1 ) {
            halign = Halign.center;
            remaining = remaining.substring(0,n) + remaining.substring(n+1);
        }
        
        // vertical markers
        n = remaining.indexOf('-');
        if( n != -1 ) {
            valign = Valign.middle;
            remaining = remaining.substring(0,n) + remaining.substring(n+1);
        }
        n = remaining.indexOf('^');
        if( n != -1 ) {
            valign = Valign.top;
            remaining = remaining.substring(0,n) + remaining.substring(n+1);
        }
        n = remaining.indexOf('~');
        if( n != -1 ) {
            valign = Valign.bottom;
            remaining = remaining.substring(0,n) + remaining.substring(n+1);
        }
        n = remaining.indexOf('_');
        if( n != -1 ) {
            header = true;
            remaining = remaining.substring(0,n) + remaining.substring(n+1);
        }
        
    }
    
    public void setDefaultId(String defId) {
        if( id.equals("") )
            setId(defId);
    }
    
    public boolean hasId() {
        return !id.equals("");
    }
    
    public void setId(String id) {
        this.id = id;
        changed = true;
    }
    public String getId() {
        return id;
    }
    
    public void setDefaultCssClass(String className) {
        if( cssClass.equals("") ) {
            cssClass = className;
            changed = true;
        }
    }
    
    public void addCssClass(String className) {
        if( cssClass.equals("") ) {
            cssClass = className;
        } else {
            cssClass += " "+className;
        }
        changed = true;
    }
    
    
    
    /**
     * Coverts this object into a string of XHTML tag attributes to be used in a
     * start tag.
     * @return XHTML tag attributes
     */
    public String tagAttributes() {
        if( mods==null || (mods.equals("") && !changed) )
            return "";
        
        StringBuilder attribBuf = new StringBuilder();
        
        if( !cssClass.equals("") )  attribBuf.append( " class=\"" + cssClass.replace(',', ' ') + "\"");
        if( !id.equals("") )        attribBuf.append( " id=\"" + id + "\"");
        if( !lang.equals("") )      attribBuf.append( " lang=\"" + lang + "\"");
        
        StringBuilder stybuf = new StringBuilder();
        
        if( !style.equals("") ) {
            stybuf.append( style );
            if( !style.endsWith(";") )
                stybuf.append(';');
        }
        
        styleSymbols( stybuf, attribBuf );
        
        if( stybuf.length() > 0 ) {
            attribBuf.append( " style=\"" );
            attribBuf.append(stybuf);
            attribBuf.append( "\"");
        }
        if( !arbTagAttr.equals("") ) attribBuf.append(" "+arbTagAttr);
        
        return attribBuf.length() == 0 ? "" : attribBuf.toString();
    }
    
    protected void styleSymbols(StringBuilder stybuf, StringBuilder attribBuf) {
        
        // horizontal and vertical alignment
        switch( halign ) {
            case left: stybuf.append("text-align:left;"); break;
            case right: stybuf.append("text-align:right;"); break;
            case both: stybuf.append("text-align:justify;"); break;
            case center: stybuf.append("text-align:center;"); break;
        }
        switch( valign ) {
            case top: stybuf.append("vertical-align:top;"); break;
            case middle: stybuf.append("vertical-align:middle;"); break;
            case bottom: stybuf.append("vertical-align:bottom;"); break;
        }
        
        // padding
        if( lpad>0 ) stybuf.append( "padding-left:"  + lpad + "em;" );
        if( rpad>0 ) stybuf.append( "padding-right:" + rpad + "em;" );
    }
    
    
    /**
     * translates modifier strings for signatures, table cells, and list items.
     */
    public static class Sig extends Modifiers {
        
        public Sig(Task task, String mods) { super(task, mods); }
        
    }
    
    /**
     * translates modifier strings for image (!...!) markup.
     */
    public static class Image extends Modifiers {
        
        public Image(Task task, String mods) {
            super(task, mods);
        }
        protected void styleSymbols(StringBuilder stybuf, StringBuilder attribBuf) {
            
            // horizontal and vertical alignment
            switch( halign ) {
                case left: stybuf.append("float:left;"); break;
                case right: stybuf.append("float:right;"); break;
            }
            switch( valign ) {
                case top: stybuf.append("vertical-align:text-top;"); break;
                case middle: stybuf.append("vertical-align:middle;"); break;
                case bottom: stybuf.append("vertical-align:text-bottom;"); break;
            }
            
            // padding
            if( lpad>0 ) stybuf.append( "padding-left:"  + lpad + "em;" );
            if( rpad>0 ) stybuf.append( "padding-right:" + rpad + "em;" );
        }
        
    }
    
    /**
     * translates modifier strings for span (%...%) markup.
     */
    public static class Span extends Modifiers {
        public Span(Task task, String mods) {
            super(task,mods);
        }
    }
    
    /**
     * translates modifier strings for table signatures.
     */
    public static class Table extends Modifiers {
        public Table(Task task, String mods) {
            super(task,mods);
        }
        protected void styleSymbols(StringBuilder stybuf, StringBuilder attribBuf) {
            // horizontal and vertical alignment
            if( (halign==Halign.left || halign==Halign.right) && (lpad > 0 || rpad > 0) ) {
                switch( halign ) {
                    case left: stybuf.append("float:left;"); break;
                    case right: stybuf.append("float:right;"); break;
                }
                if( lpad>0 ) stybuf.append( "margin-left:"  + lpad + "em;" );
                if( rpad>0 ) stybuf.append( "margin-right:" + rpad + "em;" );
                switch( valign ) {
                    case top: stybuf.append("vertical-align:top;"); break;
                    case middle: stybuf.append("vertical-align:middle;"); break;
                    case bottom: stybuf.append("vertical-align:bottom;"); break;
                }
            } else {
                super.styleSymbols(stybuf,attribBuf);
                if( halign==Halign.center )
                    stybuf.append("margin-right:auto;margin-left:auto;");
            }
        }
    }
    
    public boolean isHeader() { return header; }
    
    private static final Pattern COL_SPAN_PATTERN = Pattern.compile(COL_SPAN_REGEX);
    private static final Pattern ROW_SPAN_PATTERN = Pattern.compile(ROW_SPAN_REGEX);
    
    /**
     * translates modifier strings for table cells.
     */
    public static class Cell extends Modifiers {
        public Cell(Task task, String mods) {
            super(task, mods);
        }
        protected void parse() {
            super.parse();
            
            if( remaining == null )
                return;
            Matcher m = COL_SPAN_PATTERN.matcher(remaining);
            if( m.find() ) {
                colspan = m.group(1);
                remaining = remaining.substring(0,m.start()) + remaining.substring(m.end());
            }
            m = ROW_SPAN_PATTERN.matcher(remaining);
            if( m.find() ) {
                rowspan = m.group(1);
                remaining = remaining.substring(0,m.start()) + remaining.substring(m.end());
            }
        }
        
        protected void styleSymbols(StringBuilder stybuf, StringBuilder attribBuf) {
            super.styleSymbols(stybuf,attribBuf);
            if( !rowspan.equals("") ) attribBuf.append( " rowspan=\"" + rowspan + "\"");
            if( !colspan.equals("") ) attribBuf.append( " colspan=\"" + colspan + "\"");
        }
    }
    
    public static class TableCol extends Cell {
        
        private String width = "";
        
        public TableCol(Task task, String mods, String width) {
            super(task, mods);
            if( width != null )
                this.width = width;
            changed = true;
        }
        
        protected void styleSymbols(StringBuilder stybuf, StringBuilder attribBuf) {
            super.styleSymbols(stybuf,attribBuf);
            if( !colspan.equals("") ) attribBuf.append( " span=\"" + colspan + "\"");
            if( !width.equals("") ) attribBuf.append( " width=\"" + width + "\"");
        }
    }
    
}
