package com.centeredwork.xilize;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signature objects are responsible for translating any block of tocEntryText marked with
 * their name and are stored in a Task object's signature registry.  They are
 * then available to any subtask.
 *
 * <p>When the parser identifies the first line of a block, it creates a new Block
 * object.  The Block constructor looks in the signature registry for a signature
 * with a matching name.  If found, the signature instance in the registry is copied
 * and assigned to the block; any signature modifiers are assigned to the signature.
 *
 * <p>If no matching signature name is found, the "anonymous" signature is used.
 * During translation the anonymous signature uses the key
 * <CODE>_unsignedBlockTranslator_</CODE>'s value to locate a signature to translate
 * the block (by default this is the "p" signature).  The user can intervene by defining
 * that key to something else.
 *
 * <p><B>writing custom signatures</B>
 *
 * <p>A subclass typically overrides {@link Signature#translate(Task,Block)}.  If
 * it expects to handle children it should call {@link Block#translate()} on its children.
 * If not, it should report an error or warning if children are present.
 *
 * <p>It may also override {@link Signature#writes()} and {@link Signature#write(PrintWriter,Block)}
 * to take responsibility for writing its children to the output stream.  Otherwise,
 *
 * <p>{@link Block#write(PrintWriter)} will handle writing in its default fashion:  if no children,
 * the block's translation is written; otherwise only the children are written.
 *
 * <p>{@link Block#wrapChildren(String startTags, String endTags)} can be used to insert static
 * tocEntryText (untranslated, usually XML or XHTML tags) at the beginning and end of the
 * existing children.  See the implementation of the "bq" and "bqo" signatures and the class
 * Signature.Parent for examples.
 */
public class Signature implements Cloneable {
    
    protected String name;
    protected Modifiers mods;
    protected boolean immediate;
    protected boolean directive;
    protected boolean allowsChildren;
    
    Signature() {}
    
    /**
     * Creates a signature for <I>name</I>.
     * @param name name of this signature, must match the string used in the markup source.
     */
    public Signature(String name) {
        this.name = name;
    }
    
    /**
     * Creates a directive called <I>name</I>.  Directives are distinguished from
     * normal signatures by being "translated" when they are read by the parser
     * rather than during the translation phase after block assembly.
     * @see Signature.Directive
     * @param immediate set to true if creating a directive that will be executed immediately
     * on being recognized by as a "raw" block.  Such directives can not have
     * child blocks.
     * @param name name of this directive, must match that used in the markup source.
     */
    public Signature(String name, boolean immediate) {
        this.name = name;
        this.immediate = immediate;
    }
    
    /**
     * makes a shallow copy of this object.
     * @return the copy
     */
    public Signature copy() {
        Object o = null;
        try {
            o = super.clone();
        } catch (CloneNotSupportedException ex) {
            ex.printStackTrace();
        }
        return (Signature)o;
    }
    
    public boolean isImmediate() {
        return immediate;
    }
    
    public String getName() {
        return name;
    }
    
    public Modifiers getMods() {
        return mods;
    }
    
    /**
     * convenience method for getting the translated attribute string from the
     * enclosed Modifier object.
     * @return the attribute string suitable for insertion into a start tag
     */
    public String tagAttributes() {
        return mods==null? "" : mods.tagAttributes();
    }
    
    /**
     * inserts the tag attribute string into the first tag in <I>tagString</I>.
     * @param tagString a string containing one or more tags
     * @return the modified string with tag attributes inserted into the first tag found
     */
    public String insertAttributes(String tagString) {
        if( mods==null || !tagString.startsWith("<") )
            return tagString;
        int n = tagString.indexOf('>');
        if( n == -1 )
            return tagString;
        String attribs = mods.tagAttributes();
        if( attribs.equals(""))
            return tagString;
        return tagString.substring(0,n) + attribs + tagString.substring(n);
    }
    
    public String markup(Task task, String text) {
        text = insertAttributes(text);
        return task.markup(text);
    }
    
    public String markup(Task task, Block block, String startTags, String endTags) {
        return insertAttributes(startTags) + task.markup(block) + endTags;
    }
    
    /**
     * flags whether or not this signature object is responsible for writing out
     * the translated tocEntryText.  If this method returns true,
     * {@link Signature#write(PrintWriter,Block) } will be used to write
     * the output instead of {@link Block#write(PrintWriter) }.  Useful for signatures
     * that handle their children in a special way.
     *
     * @return true unless overriden in subclass
     * @see Block#write(PrintWriter)
     * @see Signature#write(PrintWriter,Block)
     */
    public boolean writes() { return false; }
    
    /**
     * writes a block's translation to the output stream.  Called only if @{link
     * Signature#writes()} is overriden to return true;
     * @param pw the output stream
     * @param block the block whose translation will be written
     * @see Signature#writes()
     */
    public void write(PrintWriter pw, Block block) {}
    
    public void setMods(Modifiers mods) {
        this.mods = mods;
    }
    
    void setMods(Task task, String modifiers) {
        this.mods = new Modifiers.Sig(task,modifiers);
    }
    
    
    /**
     * Override this method to implement the action of a directive.  It is called
     * by translate() method which returns a null string.
     *
     * @param task current task
     * @param block block of tocEntryText being "translated"
     */
    public void exec(Task task, Block block) throws XilizeException {
    }
    /**
     * translates a block of tocEntryText and possibly its children
     *
     * @param task current task
     * @param block block to translate
     * @return the translation
     */
    public String translate(Task task, Block block) {
        return block.linesAsString();
    }
    public String translateLast(Task task, Block block) {
        return null;
    }
    
    public static class Simple extends Signature {
        String startTags;
        String endTags;
        
        Simple( String name, String startTags, String endTags ) {
            super(name);
            this.startTags = startTags;
            this.endTags = endTags;
        }
        
        public String translate(Task task, Block block) {
            return markup(task, block, startTags, endTags);
        }
        
    }
    
    public static class Heading extends Signature implements Catalog.Item {
        
        private int level;
        private String tocEntryText;
        private String extra = "";
        
        Heading( String levelString ) {
            super( "h"+levelString );
            this.level = new Integer(levelString);
        }
        
        private static final Pattern TOC_PATTERN
                = Pattern.compile("(\\&\\{toc(Entry)?:)([^}]+)(\\})");
        
        public String translate(Task task, Block block) {
            
            if( block.isParent() ) {
                task.error(block.getLineNumber(), "child blocks not allowed here, ignoring them");
            }
            if( task.hasListener(this) && !getMods().hasId() ) {
                getMods().setId(task.uniqueId());
            }
            String text = block.linesAsString();
            String translation = null;
            Matcher m = TOC_PATTERN.matcher(text);
            if( m.find() ) {
                
                tocEntryText = task.markup(m.group(3));
                
                if( m.group(2) == null || m.group(2).equals("") ) {
                    // matched {{toc: ... }}, remove the extra markup
                    text = text.substring(0, m.start())		// before {{
                    + text.substring(m.start(3), m.end(3))      // toc entry content
                    + text.substring(m.end()); 			// after }}
                } else {
                    // matched {{tocEntry: ... }}, remove the whole match
                    text = text.substring(0, m.start()) + text.substring(m.end());
                }
                translation = task.markup(text);
                
            } else {
                translation = task.markup(text);
                tocEntryText = translation;
            }
            
            task.register(this);
            return "<"+name+tagAttributes()+">"+translation+"</"+name+">";
        }
        
        public int getLevel() {
            return level;
        }
        
        public String getText() {
            return tocEntryText;
        }
        
        public String getId() {
            return getMods().getId();
        }
        
        public String getExtra() {
            return extra;
        }
    }
    
    /**
     * a convenience class for writing simple, common types of signatures.
     */
    public static class Parent extends Signature {
        
        protected String startTags;
        protected String endTags;
        protected boolean childrenRequired;
        
        /**
         * creates a signature called <I>name</I> with the following scheme:
         *
         * <p>If its block has no children
         * it adds any tag attributes given in its signature modifiers to <I>startTags</I> and
         * writes that to the output stream, followed by the translation of the block
         * body and <I>endTags</I>.  <I>Prelude</I> is typically
         * one or more start tags and <I>endTags</I> one or more corresponding end tags.
         * However <I>startTags</I> and <I>endTags</I> may be any strings.
         *
         * <p>If its block has children, the <I>startTags</I> is modified and written followed by the
         * the children, then the <I>endTags</I>.
         *
         * @param name signature name, must match the name used in the source markup
         * @param startTags usually one or more start tags
         * @param endTags usually one or more end tags
         */
        public Parent(String name, String startTags, String endTags, boolean childrenRequired) {
            super(name);
            this.startTags = startTags;
            this.endTags = endTags;
            this.childrenRequired = childrenRequired;
        }
        
        public Parent(String name, String startTags, String endTags) {
            this(name, startTags, endTags, false);
        }
        
        /**
         * See the class comment for details.
         * @param task current task
         * @param block block to translate
         * @return the translated block, may be null
         * @see Signature.Parent
         */
        public String translate(Task task, Block block) {
            if( block.getChildren()==null ) {
                if( childrenRequired ) {
                    task.error(block.getLineNumber(), "'"+name+"' requires child blocks");
                    return null;
                }
                return markup(task, startTags+task.markupKeepEOL(block)+endTags);
            }
            for( Block c : block.getChildren() ) {
                c.translate();
            }
            block.wrapChildren(insertAttributes(startTags), endTags);
            return null;
        }
        
    }
    
    /**
     * a convenience class for creating directives which are a subset of signatures.
     * Directives are distinguished from normal signatures by being "translated" when
     * they are read by the parser rather than during the translation phase after
     * block assembly.  That is, they are used to control the environment in which
     * subsequent translation takes place.  Directive do not (should not) directly
     * generate output, i.e. they translate to the null string.
     */
    public static class Directive extends Signature {
        
        public Directive(String name) { super(name,true); }
        public Directive(String name, boolean immediate) { super(name,immediate); }
        
        /**
         * Calls exec() and returns the null string.
         *
         * @param task current task
         * @param block the block of tocEntryText associated with this directive.
         * @return null
         */
        public String translate(Task task, Block block) {
//            exec(task, block);
            return null;
        }
        
    }
    
    static class Define extends Directive {
        
        boolean append;
        
        Define() { super("define"); }
        
        Define(boolean append) {
            super("defadd");
            this.append = true;
        }
        
        Define(String name) {
            super(name);
        }
        
        public void exec(Task task, Block block) throws XilizeException {
            
            String line = block.getLine(0);
            if( line.matches("\\S+")) {
                
                if( block.getLines().size() > 1 ) {
                    String key = line;
                    String value = block.linesAsString(1);
                    if( append )
                        task.defineAppend(key,value);
                    else
                        task.define(key,value);
                } else {
                    task.warning(block.getLineNumber(), "key without value");
                }
                
            } else {
                for( String s : block.getLines() ) {
                    String[] kv = s.split("\\s+", 2);
                    if( kv.length == 2 ) {
                        if( append )
                            task.defineAppend(kv[0], kv[1]);
                        else
                            task.define(kv[0], kv[1]);
                    } else {
                        task.warning(block.getLineNumber(s), "key and value required");
                    }
                }
            }
        }
    }
    
    static class Abbrev extends Directive {
        
        private static final String ABBREV_SIG_REGEX
                = "^ *\\[(\\w[a-zA-Z0-9_\\.-]+)\\] *(\\S+)$";
        
        private static final Pattern ABBREV_SIG_PATTERN = Pattern.compile(ABBREV_SIG_REGEX);
        
        public static boolean isAbbrevLine(String s) {
            return ABBREV_SIG_PATTERN.matcher(s).matches();
        }
        
        Abbrev() { super("abbreviation"); }
        
        public void exec(Task task, Block block) throws XilizeException {
            for(String line : block.getLines()) {
                Matcher m = ABBREV_SIG_PATTERN.matcher(line);
                if( m.matches() ) {
                    task.addAbbrev(m.group(1), m.group(2));
                } else {
                    task.warning(block.getLineNumber(), "skipping malformed URL abbreviation");
                }
            }
        }
    }
    
    
    // prolog and epilog are mostly a copy from the pre-v2.0beta build 34 code base
    private static final String NL = "\n";
    
    // javascript
    private static final String SCRIPT_START = "<script type=\"text/javascript\">"+NL+"<!-- "+NL;
    private static final String SCRIPT_END = NL+"// -->"+NL+"</script>"+NL;
    
    //{{{ doctype
    private static final String DOCTYPE_X10TRANS =
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"+NL;
    private static final String DOCTYPE_X10STRICT =
            "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">"+NL;
    
    // html element and head start tag
    private static final String XHTML_START =
            "<html xmlns=\"http://www.w3.org/1999/xhtml\">"+NL+
            "<head>"+NL;
    
    // tocEntryText that will be replaced is in ALL CAPS
    private static final String META_CHARSET =
            "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=USERCHARSET\" />"+NL;
    private static final String META_KEYWORDS =
            "  <meta name=\"keywords\" content=\"USERKEYWORDS\" />"+NL;
    private static final String STYLESHEET =
            "  <link href=\"USERCSS\" rel=\"stylesheet\" type=\"text/css\" />"+NL;
    private static final String STYLESHEET_PREFERRED =
            "  <link href=\"USERCSS\" title=\"TITLE\" rel=\"stylesheet\" type=\"text/css\" />"+NL;
    private static final String STYLESHEET_ALTERNATE =
            "  <link href=\"USERCSS\" title=\"TITLE\" rel=\"alternate stylesheet\" type=\"text/css\" />"+NL;
    
    static class Prolog extends Signature {
        
        Prolog() { super("prolog"); }
        
        // todo: take care of the keys
        public String translate(Task task, Block block) {
            if( task.isDefined("customProlog"))
                return task.value("customProlog") + NL;
            
            if( !task.isValueTrue("prolog") )
                return "";
            
            StringBuilder sb = new StringBuilder();
            
            String doctype = task.value("doctype");
            if( doctype.equals("strict"))
                sb.append( DOCTYPE_X10STRICT );
            else if( doctype.equals("trans"))
                sb.append( DOCTYPE_X10TRANS );
            else {
                // todo: warn about doctype
            }
            
            sb.append( XHTML_START );
            
            if( task.isDefined("charset") )
                sb.append( META_CHARSET.replaceFirst("USERCHARSET", task.value("charset")) );
            if( task.isDefined("keywords") )
                sb.append( META_KEYWORDS.replaceFirst("USERKEYWORDS", task.value("keywords")) );
            if( task.isDefined("title") )
                sb.append( "  <title>" + task.value("title") + "</title>\n" );
            if( task.isDefined(Key.css) ) {
                String[] csses = task.value(Key.css).split("\\s+");
                for( String s : csses ) {
                    sb.append( STYLESHEET.replaceFirst("USERCSS", s) );
                }
            }
            if( task.isDefined("cssPreferred") ) {
                String[] csses = task.value("cssPreferred").split("\n");
                for( String line : csses ) {
                    String[] part = line.split("\\s+");
                    String title = "";
                    String file = "";
                    switch( part.length ) {
                        case 1: file = part[0]; break;
                        default: title = part[0]; file = part[1];
                    }
                    String tmp = STYLESHEET_PREFERRED.replaceFirst("TITLE", title);
                    sb.append( tmp.replaceFirst("USERCSS", file) );
                }
            }
            if( task.isDefined("cssAlternate") ) {
                String[] csses = task.value("cssAlternate").split("\n+");
                for( String line : csses ) {
                    String[] part = line.split("\\s+");
                    String title = "";
                    String file = "";
                    switch( part.length ) {
                        case 1: file = part[0]; break;
                        default: title = part[0]; file = part[1];
                    }
                    String tmp = STYLESHEET_ALTERNATE.replaceFirst("TITLE", title);
                    sb.append( tmp.replaceFirst("USERCSS", file) );
                }
            }
            if( task.isDefined("favicon") ) {
                sb.append( "  <link rel=\"shortcut icon\" href=\"" );
                sb.append( task.value("favicon") );
                sb.append("\" />\n");
            }
            if( task.isDefined("style") ) {
                sb.append("  <style type=\"text/css\">"+ NL);
                sb.append( task.value("style") + NL );
                sb.append("  </style>\n");
            }
            if( task.isDefined("script") ) {
                sb.append(SCRIPT_START);
                sb.append( task.value("script") + NL );
                sb.append(SCRIPT_END);
            }
            if( task.isDefined("headElementAdd") )
                sb.append( task.value("headElementAdd") + NL );
            if( task.isDefined("headAppend") )
                sb.append( task.value("headAppend") + NL );
            
            sb.append("</head>\n");
            
            // the <body> start tag is given mods from the body or xilize signature.
            sb.append("<body" + task.value(Key._BodyTagAttributes_) + ">");
            
            return sb.toString();
        }
    }
    
    private static final String EPILOG = "</body>"+NL+"</html>"+NL;
    
    static class Epilog extends Signature {
        
        Epilog() { super("epilog"); }
        
        public String translate(Task task, Block block) {
            if( task.isDefined("customEpilog") )
                return task.value("customEpilog") + NL;
            if( !task.isValueTrue("epilog") )
                return "";
            return EPILOG;
        }
    }
    
    /**
     * for debugging
     * @return a synopsis of this signature
     */
    public String toString() {
        if( immediate ) {
            return "["+name+"]";
        }
        assert name != null;
        return name + (mods==null? "" : mods.toString());
    }
    
    static class Pre extends Parent {
        
        public Pre() {
            super("pre", "<pre>", "</pre>");
        }
        
        public Pre(String name) {
            super(name, "<pre>", "</pre>");
        }
        public Pre(String name, String startTags, String endTags) {
            super(name, startTags, endTags);
        }
        
        protected String markup(Task task, Block block, int wrap) {
            return task.markupKeepEOL(wrap,block);
        }
        public String translate(Task task, Block block) {
            block.setWriteChildren(false);
            int wrap = 0;
            if( task.isDefined(Key._PreStringWrap_)) {
                try {
                    wrap = new Integer(task.value(Key._PreStringWrap_));
                } catch (NumberFormatException ex) {
                    task.warning(block.getLineNumber(),
                            "_PreStringWrap_ key is set to something that is not a number");
                }
            }
            return insertAttributes(startTags) + markup(task,block,wrap) + endTags;
        }
    }
    
    static class Prex extends Pre {
        
        public Prex() {
            super("prex");
        }
        
        public Prex(String name, String startTags, String endTags) {
            super(name, startTags, endTags);
        }
        
        protected String markup(Task task, Block block, int wrap) {
            return task.markupU(block.wrapLines(wrap));
        }
    }
    
    public static class Footnote extends Signature {
        
        private String fnNum;
        
        public Footnote(String fnNum) {
            super("footnote");
            this.fnNum = fnNum;
        }
        
        public String translate(Task task, Block block) {
            if( block.isParent() ) {
                fixup(task, block.getChildren().get(0));
                for( Block child : block.getChildren() ) {
                    child.translate();
                }
                return null;
            }
            fixup(task, block);
            block.translate();
            return block.getTranslation();
        }
        
        public void fixup(Task task, Block block) {
            String fntag = "<a class=\"fn_anchor\" href=\"#fnmk"
                    + fnNum+ "\">" + fnNum + "</a> ";
            
            if( !task.value(Key._FootnoteStyle_).equals("modern") ) {
                fntag = "<sup>" + fntag + "</sup>";
            }
            getMods().setId("fn"+fnNum);
            getMods().addCssClass("fn_note");
            Signature sig = task.getSignature("p");
            sig.setMods(getMods());
            block.setSignature(sig);
            block.setLine(0, fntag + block.getLine(0));
        }
        
//        public String translate(Task task, Block block) {
//
//            String fntag = "<a class=\"fn_anchor\" href=\"#fnmk"
//                    + fnNum+ "\">" + fnNum + "</a> ";
//
//            if( !task.value(Key._FootnoteStyle_).equals("modern") ) {
//                fntag = "<sup>" + fntag + "</sup>";
//            }
//            getMods().setId("fn"+fnNum);
//			getMods().setDefaultCssClass("fn_note");
//            return "<p"+tagAttributes()+">" + fntag + task.markup(block) + "</p>";
//        }
    }
    
    /**
     * creates the default set of signatures and directives and adds them the
     * current task. Used to initialize the "master task" in the Xilize2 class
     * as part of the bootstrap process.
     * When added to the master task, the default set is available to all tasks
     * yet can be overridden by user-supplied custom signatures discovered in
     * subtasks.
     *
     * @param task task in which to put the default signature set,
     * should be the "master task"
     */
    static void addStdSigSet(Task task) {
        
        //______________________________________________________________________
        //
        // signatures
        //______________________________________________________________________
        
        
        // the anonymous signature
        task.addSig(new Signature( task.value(Key._UnsignedBlockSigName_)) {
            
            public String translate(Task task, Block block) {
                
                if( task.isDefined(Key._UnsignedBlockSigSubstitute_) ) {
                    
                    Signature sig = task._getSignature( task.value(Key._UnsignedBlockSigSubstitute_) );
                    if( sig != null ) {
                        return sig.translate(task, block);
                    } else {
                        // todo: warning sig not defined
                        task.warning(block.getLineNumber(), "_unsignedBlockSigSubstitute_ is not defined");
                    }
                }
                
                return "<p>"+block.linesAsString()+"</p>";
            }
        });
        
        task.addSig(new Signature("p") {
            public String translate(Task task, Block block) {
                if( block.getChildren()==null ) {
                    return "<p"+tagAttributes()+">"+task.markup(block)+"</p>";
                }
                task.error(block.getLineNumber(), "'p' signature should not have child blocks");
                for( Block c : block.getChildren() ) {
                    c.translate();
                }
                return "<p"+tagAttributes()+">"+task.markup(block)+"</p>";
            }
        });
        
        task.addSig(new Signature("raw"));      // no translation, tocEntryText passed through unchanged
        
        task.addSig(new Signature("xilcom") {   // does nothing, xilize source file comment
            public String translate(Task task, Block block) {
                block.setWriteChildren(false);
                return null;
            }
        });
        
//        task.addSig(new Prex("xmlcom", "<!-- ", " -->"));   // xml comment with unkind char replacement
        
        task.addSig(new Signature("xmlcom" ) {
            protected String markup(Task task, Block block, int wrap) {
                return task.markupKeepEOL(wrap,block);
            }
            public String translate(Task task, Block block) {
                block.setWriteChildren(false);
                return "<!-- " +
                        task.getEnv().getInline().translateKMU(task, block.wrapLines(0))
                        + " -->";
            }
        });
        
        task.addSig(new Heading("1"));
        task.addSig(new Heading("2"));
        task.addSig(new Heading("3"));
        task.addSig(new Heading("4"));
        task.addSig(new Heading("5"));
        task.addSig(new Heading("6"));
        
        task.addSig(new Signature("hr"){
            public String translate(Task task, Block block) {
                return "<hr"+tagAttributes()+" />";
            }
        });
        
        task.addSig(new Parent("div", "<div>", "</div>", true) );
        
        task.addSig( new Signature("divStart") {                // legacy
            public String translate(Task task, Block block) {
                return "<div"+tagAttributes()+">";
            }
        });
        task.addSig( new Signature("divEnd") {                  // legacy
            public String translate(Task task, Block block) {
                return "</div>";
            }
        });
        
        task.addSig(new Parent("block", "", "", true) );    // holder of child blocks
        
        // bqo does not add a <p> element to a childless block as does bq.
        task.addSig(new Parent("bqo", "<blockquote>", "</blockquote>"));
        
        task.addSig(new Signature("bq"){
            public String translate(Task task, Block block) {
                String firstTag = insertAttributes("<blockquote>");
                if( block.getChildren()==null ) {
                    return firstTag+"<p>"+task.markup(block)+"</p></blockquote>";
                }
                for( Block c : block.getChildren() ) {
                    c.translate();
                }
                block.wrapChildren(firstTag, "</blockquote>");
                return null;
            }
        });
        
        
        task.addSig(new Pre());
        task.addSig(new Prex() {
            protected String markup(Task task, Block block, int wrap) {
                return task.markupU(block.wrapLines(wrap));
            }
        });
        
        task.addSig(new Pre("bc", "<pre><code>", "</code></pre>"));
        task.addSig(new Prex("bcx", "<pre><code>", "</code></pre>"));
        
        task.addSig(new Signature("km") {       // keys and marcos only
            public String translate(Task task, Block block) {
                if( block.isParent() ) {
                    Signature sig = task.getSignature("km");
                    for( Block child : block.getChildren() ) {
                        child.setSignature(sig);
                        child.translate();
                    }
                }
                return task.markupKM(block.linesAsString());
            }
        });
        
        task.addSig(new Signature("imo") {      // inline markup only
            public String translate(Task task, Block block) {
                if( block.isParent() ) {
                    Signature sig = task.getSignature("imo");
                    for( Block c : block.getChildren() ) {
                        c.setSignature(sig);
                        c.translate();
                    }
                }
                return task.markup(block);
            }
        });
        
        task.addSig(new SigIf());
        task.addSig(new Signature("else") {
            public String translate(Task task, Block block) {
                task.error(block.getLineNumber(), "\"else.\" is only meaningful as the last child of an 'if.' block");
                return "";
            }
        });
        task.addSig(new SigIf.Def("ifdef", false));
        task.addSig(new SigIf.Def("ifndef", true));
        
        // table-related signatures
        task.addSig(new SigTable());
        task.addSig(new Signature("row") {
            public String translate(Task task, Block block) {
                task.error(block.getLineNumber(), "\"row.\" is only meaningful as table child block");
                return "";
            }
        });
        task.addSig(new Signature("cell") {
            public String translate(Task task, Block block) {
                task.error(block.getLineNumber(), "\"cell.\" is only meaningful as table-row child block");
                return "";
            }
        });
        
        // definition list
        task.addSig(new SigDefList());
        
        task.addSig(new Signature("clear") {
            public String translate(Task task, Block block) {
                String modString = getMods().toString();
                if( modString.equals("") )
                    return "<div style=\"clear:both\" ></div>";
                if( modString.equals(">") )
                    return "<div style=\"clear:right\" ></div>";
                if( modString.equals("<") )
                    return "<div style=\"clear:left\" ></div>";
                task.error(block.getLineNumber(), "only '>' and '<' are valid signature modifiers here");
                return "";
            }
        });
        
        task.addSig(new Signature("javascript") {
            public String translate(Task task, Block block) {
                // todo: handle child blocks
                return "<script type=\"text/javascript\">\n<!-- \n"
                        + block.linesAsString()
                        + "\n// -->\n</script>\n";
            }
        });
        
        
        // special sigs
        task.addSig(new Prolog());
        task.addSig(new Epilog());
        
        //______________________________________________________________________
        //
        // directives
        //______________________________________________________________________
        
        task.addSig(new Define());      // regular define
        task.addSig(new Define(true));  // define with append
        
        task.addSig(new Directive("undef") {
            public void exec(Task task, Block block) throws XilizeException {
                String[] keys = block.linesAsStringTrim().split("\\s+");
                if( keys.length == 1 && keys[0].equals("") ) {
                    task.warning(block.getLineNumber(), "nothing to undefine");
                    return;
                }
                for( String k : keys )
                    task.undef(k);
            }
        });
        
        task.addSig(new Directive("include") {
            public void exec(Task task, Block block) throws XilizeException {
                if( !(task instanceof TaskFile )) {
                    task.warning(block.getLineNumber(), "file task required");
                    return;
                }
                String s = task.markupKM(block.linesAsStringTrim());
                String[] fnames = s.split("\\s+");
                if( fnames == null || fnames[0].equals("") ) {
                    task.warning(block.getLineNumber(), "nothing to include");
                    return;
                }
                try {
                    for( String filename : fnames ) {
                        ((TaskFile)task).include(block.getLineNumber(), filename);
                    }
                } catch (XilizeException ex) {
                    task.error(0, "include file exception: "+ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });
        
        task.addSig(new Signature("includeRaw") {
            public String translate(Task task, Block block) {
                if( !(task instanceof TaskFile )) {
                    task.warning(block.getLineNumber(), "file task required");
                    return "";
                }
                String[] fnames = block.linesAsStringTrim().split("\\s+");
                if( fnames == null || fnames[0].equals("") ) {
                    task.warning(block.getLineNumber(), "nothing to include");
                    return "";
                }
                StringBuilder sb = new StringBuilder();
                for( String filename : fnames ) {
                    File f = Files.localFile( filename, task.getParent().getPath());
                    try {
                        sb.append(Files.read(f,0));
                    } catch (IOException ex) {
                        task.error(block.getLineNumber(), "file read failed");
                        ex.printStackTrace();
                    }
                }
                return sb.toString();
            }
        });
        
        task.addSig(new Directive("propfile") {
            public void exec(Task task, Block block) throws XilizeException {
                if( block.isParent() ) {
                    task.warning(block.getLineNumber(), "this directive may not have child blocks");
                    return;
                }
                File path = Files.localFile(block.getLine(0).trim(), task.getFile().getParent());
                try {
                    task.loadProperties(path);
                } catch (IllegalArgumentException ex) {
                    task.error(block.getLineNumber(), "loading property file "+path.getAbsolutePath());
                    ex.printStackTrace();
                } catch (IOException ex) {
                    task.error(block.getLineNumber(), "loading property file "+path.getAbsolutePath());
                    ex.printStackTrace();
                }
            }
        });
        
        task.addSig(new Directive("signature") {
            public void exec(Task task, Block block) throws XilizeException {
                if( block.getLines().size() < 2 ) {
                    task.error(block.getLineNumber(), "signature must have at least two lines");
                    return;
                }
                try {
                    Signature cs = new SigCustom(task, block);
                    task.addSig(cs);
                } catch (XilizeException ex) {
                    task.error(block.getLineNumber(), ex.getMessage());
                    task.error(block.getLineNumber(), "custom signature ignored");
                }
            }
        });
        
        task.addSig(new Directive("body") {
            public void exec(Task task, Block block) throws XilizeException {
                task.define(Key._BodyTagAttributes_, tagAttributes());
            }
        });
        
        task.addSig(new Directive("xilize") {
            public void exec(Task task, Block block) throws XilizeException {
                task.getSignature("define").exec(task,block);
                task.define(Key._BodyTagAttributes_, tagAttributes());
            }
        });
        
        task.addSig(new SigToc());
    }
    
    
}  // class Signature


