package com.centeredwork.xilize;

import bsh.EvalError;
import bsh.TargetError;
import com.centeredwork.xilize.Regex.Trans;
import java.io.File;
import java.util.HashMap;
import java.util.regex.*;

/**
 * translates Xilize markup inside a block (as opposed to block-level markup
 * like signatures their modifiers.
 *
 * Note, this is essentially the same class used prior to v2.0beta build 34
 * adjusted to be thread safe.
 * It will eventually be replaced by a real grammar-rule-driven parser, but
 * until then it is accurate and efficient even if difficult to maintain.
 */
public class InlineMarkup {
    
    // {{{ private static fields -----------------------------------
    
    private static final String XIL_KEY 	= "`xil%";
    private static final String XIL_END 	= "%xil`";
    private static final String AMP_SUB 	= "q%`q%";
    
    private static final String PHRASE_TERMINATOR = "(?=$|<|`|\\s|([\\.,;:!\\?](\\s|$)))";
    
    private static final String CLASS 	= "\\(\\w*?(#\\w+)?\\)";
    private static final String ATTR 	= "\\{\\{[\\S &&[^{}]]+\\}\\}";
    private static final String STYLE 	= "\\{[\\S &&[^}]]+\\}";
    private static final String SYMS 	= "-|\\^|~|&lt;|&gt;|\\(+|\\)+";
    private static final String MODS_REGEX = "("+SYMS+"|"+CLASS+"|"+ATTR+"|"+STYLE+")+";
    private static final String MODS 	= "("+MODS_REGEX+" )?";
    // note>>                                             ^ note the space char
    
    
    
    // special case for image markup with modifiers, translation occurs
    // before > and < are converted to CERs
    private static final String SYMS_IMG = "-|\\^|~|<|>|\\(+|\\)+";
    private static final String MODS_IMG = "(("+SYMS_IMG+"|"+CLASS+"|"+ATTR+"|"+STYLE+")+ )?";
    
    // for link and image translation
    private static final String PREFIX_REGEX = "(?sm)(^|[\\s>`])";
    private static final String LINK_REGEX = "([^(\"]+?)";
    private static final String LINK_EMB_REGEX = "([^(]+?)";
    private static final String TITLE_REGEX = "( *\\(([^)\n]+?)\\))?";
    private static final String LINK_TITLE_REGEX = LINK_REGEX + TITLE_REGEX;
    private static final String LINK_TITLE_EMB_REGEX = LINK_EMB_REGEX + TITLE_REGEX;
    private static final String URL_REGEX = "(\\S+?)";
    private static final String URL_TITLE_REGEX = URL_REGEX + TITLE_REGEX;
    
    
    //{{{ html and other (doctype/xml decl) markup elements
    private static final String HTML_TAG_NAME =
            "A|ABBR|ACRONYM|ADDRESS|APPLET|AREA|B|BASE|BASEFONT|BDO|BIG"
            +"|BLOCKQUOTE|BODY|BR|BUTTON|CAPTION|CENTER|CITE|CODE|COL|COLGROUP|DD"
            +"|DEL|DFN|DIR|DIV|DL|DT|EM|FIELDSET|FONT|FORM|FRAME|FRAMESET|H1|H2|H3|H4"
            +"|H5|H6|HEAD|HR|HTML|I|IFRAME|IMG|INPUT|INS|ISINDEX|KBD|LABEL|LEGEND|LI"
            +"|LINK|MAP|MENU|META|NOFRAMES|NOSCRIPT|OBJECT|OL|OPTGROUP|OPTION|P|PARAM"
            +"|PRE|Q|S|SAMP|SCRIPT|SELECT|SMALL|SPAN|STRIKE|STRONG|STYLE|SUB|SUP|TABLE"
            +"|TBODY|TD|TEXTAREA|TFOOT|TH|THEAD|TITLE|TR|TT|U|UL|VAR"
            +"|a|abbr|acronym|address|applet|area|b|base|basefont|bdo|big"
            +"|blockquote|body|br|button|caption|center|cite|code|col|colgroup|dd"
            +"|del|dfn|dir|div|dl|dt|em|fieldset|font|form|frame|frameset|h1|h2|h3|h4"
            +"|h5|h6|head|hr|html|i|iframe|img|input|ins|isindex|kbd|label|legend|li"
            +"|link|map|menu|meta|noframes|noscript|object|ol|optgroup|option|p|param"
            +"|pre|q|s|samp|script|select|small|span|strike|strong|style|sub|sup|table"
            +"|tbody|td|textarea|tfoot|th|thead|title|tr|tt|u|ul|var";
    
    private static final String HTML =
            "(<("+HTML_TAG_NAME+")(\\s+\\S+\\s*?=\\s*?\".*?\")*(\\s*?/?)>"  // start tag & attributes
            +"|"
            +"</("+HTML_TAG_NAME+")\\s*?>)";  // end tag
    
    private static final String XML = "(<\\?xml.*?\\?>)";
    
    private static final String DOCTYPE = "(<!DOCTYPE\\s.*?>)";
    
    private static final String HTML_COMMENT = "(<!--\\s(.*?\\s)-->)";
    
    private static final String EXISTING_MARKUP_ELEMENT =
            "("+HTML+"|"+HTML_COMMENT+"|"+XML+"|"+DOCTYPE+")";
    
    private static final String EXISTING_MARKUP =
            EXISTING_MARKUP_ELEMENT+"(\\s*"+EXISTING_MARKUP_ELEMENT+")*";
    //}}}
    
    private static class SymPhraseTransform extends Regex.Replace {
        SymPhraseTransform( String symMarker, String symSub ) {
            super(
                    symMarker,
                    "(?sm)(^|\\s|>|`)" + Pattern.quote(symMarker)
                    + "((?:\\S{1,2})|(?:[\\S&&[^_+*-]].+?\\S))" + Pattern.quote(symMarker)
                    + PHRASE_TERMINATOR,
                    "$1<" +symSub+ ">$2</" +symSub+ ">"
                    );
        }
    }
    private static class SymPhraseEmbeddedTransform extends Regex.Replace {
        SymPhraseEmbeddedTransform( String symMarker, String symSub ) {
            super(
                    "["+symMarker,
                    "(?sm)\\[" + Pattern.quote(symMarker)
                    + "((\\S){1,2}|(\\S.+?\\S))" + Pattern.quote(symMarker)
//                    + "([^]]+?)" + Pattern.quote(symMarker)
                    + "\\]",
                    "<" +symSub+ ">$1</" +symSub+ ">"
                    );
        }
    }
    private static Regex.Special keyValueSubTrans = new Regex.Special(
            "${",
            //        "${" key                          "}"
            "(?sm)(\\$\\{)([a-zA-Z_][a-zA-Z_0-9\\.-]*)(\\})",
            new Trans() {
        public void apply( Task task, StringBuilder buffer, Matcher m ) {
            String key = m.group(2);
            if( task.isDefined(key) ){
                buffer.append(task.value(key));
            } else {
                buffer.append(m.group(0));
            }
        }
    }
    );
    
    private static Regex.Special execMacrosTrans = new Regex.Special(
            "&{",
            // "     &{beanshell code} or &{func:text}
            "(?sm)(?:\\&\\{)(?: *(\\w(?:\\w|[0-9])*) *:)?([^\\}]+)(?:\\})",
            new Trans() {
        public void apply( Task task, StringBuilder buffer, Matcher m ) {
            
            BeanShell bsh = task.getBsh();
            try {
                
                bsh.set("task", task);
                if( m.group(1) == null ) {
                    buffer.append(bsh.eval(m.group(2)).toString());
                } else {
                    bsh.set("text", m.group(2));
                    buffer.append(bsh.eval(m.group(1)+"();").toString());
                }
                
                // todo: finalize exception handling
//            } catch ( ParseException e ) {
//                ;
            } catch ( EvalError e ) {
                
                // todo: add information about the xil source file location of the call
                // requires changing Task arg to InlineMarkup
                
                String msg = null;
                if( e instanceof TargetError ) {
                    TargetError te = (TargetError)e;
                    msg = te.printTargetError(te.getTarget());
//                } else if( e instanceof ParseException ) {
//                    ParseException pe = (ParseException)e;
//                    msg = pe.getMessage();
                } else {
                    msg = e.getMessage();
                }
                
                int bshLine = 0;
                String filename = e.getErrorSourceFile();
                File f = new File(filename);
                if( f.exists() ) {
                    bshLine = e.getErrorLineNumber();
                    filename = f.getAbsolutePath();
                } else {
                    Matcher m2 = Pattern.compile("error at line (\\d+)").matcher(msg);
                    if( m2.find() )
                        bshLine = new Integer(m2.group(1));
                    filename = null;
                }
                
                if( filename != null )
                    task.error(filename+":"+bshLine+":"+msg);
                task.error(bshLine, msg);
                buffer.append("==!!MACRO ERROR!!==");
                
            }  catch ( Throwable t ) {
                task.error(0, "macro error: "+t.getMessage());
                buffer.append("==!!MACRO ERROR!!==");
            }
        }
    }
    );
    
    private static Pattern NO_MOD_PATTERN
            = Pattern.compile("(?sm)(^|\\s)==(\\S|(\\S.*?\\S))=="+PHRASE_TERMINATOR);
    
    private Regex.Special noModTrans = new Regex.Special(
            "==",
            NO_MOD_PATTERN,
            //"$1"+XIL_NOMOD+"$2"+XIL_END,
            new Trans() {
        public void apply( Task task, StringBuilder sb, Matcher m ) {
            sb.append(m.group(1));
            storeSnippet( sb, m.group(2) );
        }
    }
    );
    
    private static Pattern NO_MOD_EMBEDDED_PATTERN
            = Pattern.compile("(?sm)\\[==(\\S|(\\S.*?\\S))==\\]");
//            = Pattern.compile("(?sm)\\[==([^=\\]]+?)==\\]");
    
    private Regex.Special noModEmbeddedTrans = new Regex.Special(
            "[==",
            NO_MOD_EMBEDDED_PATTERN,
            new Trans() {
        public void apply( Task task, StringBuilder sb, Matcher m ) {
            storeSnippet( sb,  m.group(1) );
        }
    }
    );
    
    private static Pattern CODE_PATTERN
            = Pattern.compile("(?s)(^|\\s)@(\\S{1,2}|(\\S[^@]+?\\S))@"+PHRASE_TERMINATOR);
    
    private Regex.Special codeTrans = new Regex.Special(
            "@",
            CODE_PATTERN,
            new Trans() {
        public void apply( Task task, StringBuilder sb, Matcher m ) {
            sb.append(m.group(1));
            storeSnippet( sb, flowCode(m.group(2)) );
        }
    }
    );
    
    private static Pattern CODE_EMBEDDED_PATTERN
            = Pattern.compile("(?s)\\[@(.+?)@\\]");
    
    private Regex.Special codeEmbeddedTrans = new Regex.Special(
            "[@",
            CODE_EMBEDDED_PATTERN,
            new Trans() {
        public void apply( Task task, StringBuilder sb, Matcher m ) {
            storeSnippet( sb, flowCode(m.group(1)) );
            
        }
    }
    );
    
    private static Pattern EXISITING_MARKUP_PATTERN
            = Pattern.compile(EXISTING_MARKUP);
    
    private Regex.Special existingMarkupTrans = new Regex.Special(
            "<",
            EXISITING_MARKUP_PATTERN,
            new Trans() {
        public void apply( Task task, StringBuilder sb, Matcher m ) {
            storeSnippet( sb, m.group(0));
        }
    }
    );
    
    
    // link and img transformations
    
    private static Regex.Special linkEmbeddedTrans = new Regex.Special(
            "[\"",
            //      [ "1 text (3 title  )       ":4 url          ]
            "(?sm)\\[\""+LINK_TITLE_EMB_REGEX+"\":"+URL_REGEX+"\\]",
            new Trans() {
        public void apply(Task task,  StringBuilder sb, Matcher m ) {
            sb.append("<a href=\"");
            String url = task.getUrl(m.group(4));  // check for abbreviation
            if( url==null ) url = m.group(4);
            sb.append(url);
            sb.append('"');
            String title = m.group(3);
            if( title != null ) {
                sb.append(" title=\"" + title);
                sb.append('"');
            }
            String linkText = m.group(1);
            sb.append(">"+linkText+"</a>");
        }
    }
    );
    
    private static Regex.Special linkTrans = new Regex.Special(
            "\"",
            //1             "2 text (4 title )    ":5 url
            PREFIX_REGEX +"\""+LINK_TITLE_REGEX+"\":"+URL_REGEX+PHRASE_TERMINATOR,
            new Trans() {
        public void apply(Task task,  StringBuilder sb, Matcher m ) {
            sb.append(m.group(1));
            sb.append("<a href=\"");
            
            String url = task.getUrl(m.group(5));  // check for abbreviation
            if( url==null ) url = m.group(5);
            sb.append(url);
            sb.append('"');
            
            String title = m.group(4);
            if( title != null ) {
                sb.append(" title=\"" + title);
                sb.append('"');
            }
            String linkText = m.group(2);
            sb.append(">"+linkText+"</a>");
        }
    }
    );
    
    private static Regex.Special imgEmbeddedTrans = new Regex.Special(
            "[!",
            //      [!1 mods 4 url (6 alt)    !  ]
            "(?sm)\\[!"+MODS_IMG+URL_TITLE_REGEX+"!\\]",
            new Trans() {
        public void apply(Task task,  StringBuilder sb, Matcher m ) {
            sb.append("<img src=\"");
            
            String url = task.getUrl(m.group(4));  // check for abbreviation
            if( url==null ) url = m.group(4);
            sb.append(url);
            sb.append('"');
            
            String alt = m.group(6);
            if( alt != null ) {
                sb.append(" alt=\"" + alt + "\" title=\"" + alt + "\"");
            }
            
            String mods = m.group(1);
            if( mods != null ) {
//                mods = cer2gtrLess(mods); // since we've converted < and >
                Modifiers modifiers = new Modifiers.Image(task, mods);
                sb.append(modifiers.tagAttributes());
            }
            sb.append(" />");
        }
    }
    );
    
    private static Regex.Special imgTrans = new Regex.Special(
            "!",
            //1 lead       ! 2 mods 5 url   (7 alt) !
            PREFIX_REGEX +"!"+MODS_IMG+URL_TITLE_REGEX+"!"+ PHRASE_TERMINATOR,
            new Trans() {
        public void apply(Task task,  StringBuilder sb, Matcher m ) {
            sb.append(m.group(1));
            sb.append("<img src=\"");
            
            String url = task.getUrl(m.group(5));  // check for abbreviation
            if( url==null ) url = m.group(5);
            sb.append(url);
            sb.append('"');
            
            String alt = m.group(7);
            if( alt != null ) {
                sb.append(" alt=\"" + alt + "\" title=\"" + alt + "\"");
            }
            
            String mods = m.group(2);
            if( mods != null ) {
//                mods = cer2gtrLess(mods); // since we've converted < and >
                Modifiers modifiers = new Modifiers.Image(task,mods);
                sb.append(modifiers.tagAttributes());
            }
            sb.append(" />");
        }
    }
    );
    
    private static Regex.Special imgLinkEmbeddedTrans = new Regex.Special(
            "[!",
            //      [! 1 mods 4 url (6 alt)       !: 7 url         ]
            "(?sm)\\[!"+ MODS + URL_TITLE_REGEX +"!:"+URL_REGEX+"\\]",
            new Trans() {
        public void apply(Task task,  StringBuilder sb, Matcher m ) {
            
            sb.append("<a href=\"");
            String lurl = task.getUrl(m.group(7));  // check for abbreviation
            if( lurl==null ) lurl = m.group(7);
            sb.append(lurl);
            
            sb.append("\"><img src=\"");
            
            String url = task.getUrl(m.group(4));  // check for abbreviation
            if( url==null ) url = m.group(4);
            sb.append(url);
            sb.append('"');
            
            String alt = m.group(6);
            if( alt != null ) {
                sb.append(" alt=\"" + alt + "\" title=\"" + alt + "\"");
            }
            
            String mods = m.group(1);
            if( mods != null ) {
                mods = cer2gtrLess(mods); // since we've converted < and >
                Modifiers modifiers = new Modifiers.Image(task,mods);
                sb.append(modifiers.tagAttributes());
            }
            sb.append(" /></a>");
        }
    }
    );
    
    private static Regex.Special imgLinkTrans = new Regex.Special(
            "!",
            //1 lead       !2 mods 5 url  (7 alt)       !: 8 url
            PREFIX_REGEX +"!"+ MODS + URL_TITLE_REGEX +"!:"+URL_REGEX+PHRASE_TERMINATOR,
            new Trans() {
        public void apply(Task task,  StringBuilder sb, Matcher m ) {
            sb.append(m.group(1));
            
            sb.append("<a href=\"");
            String lurl = task.getUrl(m.group(8));  // check for abbreviation
            if( lurl==null ) lurl = m.group(8);
            sb.append(lurl);
            
            sb.append("\"><img src=\"");
            
            String url = task.getUrl(m.group(5));  // check for abbreviation
            if( url==null ) url = m.group(5);
            sb.append(url);
            sb.append('"');
            
            String alt = m.group(7);
            if( alt != null ) {
                sb.append(" alt=\"" + alt + "\" title=\"" + alt + "\"");
            }
            
            String mods = m.group(2);
            if( mods != null ) {
                mods = cer2gtrLess(mods); // since we've converted < and >
                Modifiers modifiers = new Modifiers.Image(task,mods);
                sb.append(modifiers.tagAttributes());
            }
            sb.append(" /></a>");
        }
    }
    );
    
    
    // xilize %, note the span markup supports modifiers hence the call to
    // BlockFactory.parseSpec()
    
    private static Regex.Special spanTrans = new Regex.Special(
            "%",
            "(?sm)(^|\\s|>|`)%"+MODS+"((?:\\S{1,2})|(?:[\\S&&[^_+*-]].+?\\S))%"+PHRASE_TERMINATOR,
            new Trans() {
        public void apply(Task task,  StringBuilder sb, Matcher m ) {
            sb.append(m.group(1));
            sb.append("<span");
            String mods = m.group(2);
            if( mods != null ) {
                mods = cer2gtrLess(mods); // since we've converted < and >
                Modifiers modifiers = new Modifiers.Span(task,mods);
                sb.append(modifiers.tagAttributes());
            }
            sb.append(">");
            sb.append(m.group(5));
            sb.append("</span>");
        }
    }
    );
    
    private static Regex.Special spanEmbeddedTrans = new Regex.Special(
            "[%",
            // todo: weaker match requirements wrt ^_*-
            "(?sm)\\[%"+MODS+"((?:\\S{1,2})|(?:[\\S&&[^_+*-]].+?))%\\]",
            new Trans() {
        public void apply(Task task,  StringBuilder sb, Matcher m ) {
            sb.append("<span");
            String mods = m.group(1);
            if( mods != null ) {
                mods = cer2gtrLess(mods); // since we've converted < and >
                Modifiers modifiers = new Modifiers.Span(task,mods);
                sb.append(modifiers.tagAttributes());
            }
            sb.append(">");
            sb.append(m.group(4));
            sb.append("</span>");
        }
    }
    );
    
    // footnote transformation
        /* footnote in-line phrase markup, e.g. [1]
                <p>one <a class="fn_mark" id="fnmk1" href="#fn1">1</a>.</p>
         
                BlockFootnote produces:
                <p class="fn_text" id="fn1">
                <a class="fn_anchor" href="#fnmk1">1</a>
                A footnote.
                </p>
         */
    private static final String FN_MATCH = "\\[(\\d+)\\]";
    private static final String FN_REPLACE_MODERN =
            "<a class=\"fn_mark\" id=\"fnmk$1\" href=\"#fn$1\">$1</a>";
    
    private static Regex.Replace fnMarkerModernTrans = new Regex.Replace(
            "[",
            FN_MATCH,
            FN_REPLACE_MODERN
            );
    private static Regex.Replace fnMarkerClassicTrans = new Regex.Replace(
            "[",
            FN_MATCH,
            "<sup>"+FN_REPLACE_MODERN+"</sup>"
            );
    
    // symmetric phrase transformations
    private static final String[] INLINE_MARKUP = {
        "__","**","*",    "_", "??",  "++",  "--",  "-",  "+",  "~",  "^"
    };
    private static final String[] INLINE_TAGS = {
        "i", "b","strong","em","cite","big","small","del","ins","sub","sup"
    };
    
    private static SymPhraseTransform phraseTrans[]
            = new SymPhraseTransform[INLINE_MARKUP.length];
    
    private static SymPhraseEmbeddedTransform phraseEmbeddedTrans[]
            = new SymPhraseEmbeddedTransform[INLINE_MARKUP.length];
    
    static {
        for(int i = 0; i < INLINE_MARKUP.length; i++) {
            phraseTrans[i] = new SymPhraseTransform( INLINE_MARKUP[i], INLINE_TAGS[i] );
            phraseEmbeddedTrans[i] = new SymPhraseEmbeddedTransform( INLINE_MARKUP[i], INLINE_TAGS[i] );
        }
    }
    
    // acronym and acronym-like transformations
    
    private static Regex.Replace acronymTrans = new Regex.Replace(
            "",
            "\\b([A-Z][A-Z0-9]{2,})(\\(([^\\)]+?)\\))",
            "<acronym title=\"$3\"><span class=\"caps\">$1</span></acronym>"
            );
    private static Regex.Replace acronymLikeTrans = new Regex.Replace(
            "",
            "(\\s|^)([A-Z][A-Z0-9]{2,})\\b",
            "$1<span class=\"caps\">$2</span>"
            );
    
    // common phrase markup
    private static String phrases(Task task, String text){
        
        if( text.length() < 3 )
            return text;
        
        text = spanTrans.apply(task, text);
        text = spanEmbeddedTrans.apply(task, text);
        
        // symmetric phrases: e.g. _em_
        for(int i = 0; i < phraseTrans.length; i++) {
            text = phraseEmbeddedTrans[i].replace(text);
            text = phraseTrans[i].replace(text);
        }
        // footnote marker: [#]
        if( task.value("_FootnoteStyle_").equals("modern") ) {
            text = fnMarkerModernTrans.replace(text);
        } else { // it's classic
            text = fnMarkerClassicTrans.replace(text);
        }
        text = acronymTrans.replace(text);
        text = acronymLikeTrans.replace(text);
        return text;
    }
    
    // Character Entity Reference Mapping
    private static String cers(String str){
        String[][] CER_MAP = {
            { "(\\s?)--(\\s?)",     "$1&#8212;$2", },// emdash
            { "\\s-\\s",            " &#8211; ", },  // endash
            { "\\b( )?\\((tm|TM)\\)","$1&#8482;", }, // trademark
            { "\\b( )?\\([rR]\\)",   "$1&#174;", },  // registered mark
            { "(\\A|\\b)( )?\\([cC]\\)( )?(\\b|\\Z)", "$2&#169;$3", }, // copyright
        };
        for(int i=0; i<CER_MAP.length; i++){
            str = str.replaceAll(CER_MAP[i][0], CER_MAP[i][1]);
        }
        return str;
    }
    
    
    // note: & is not restored, compare to replaceUnkindChar()
    private static String cer2gtrLess(String str){
        str = str.replaceAll("&gt;", ">");
        str = str.replaceAll("&lt;", "<");
        return str;
    }
    
    private void storeSnippet(StringBuilder sb, String text) {
        String key = XIL_KEY + Integer.toString(nextKeyId++) + XIL_END;
        sb.append(key);
        snippetMap.put( key, text );
    }
    
    private String flowCode(String text) {
        text = "<code>" + replaceUnkindChar(text) + "</code>";
        if( !preserveWhitespace )
            text = text.replaceAll("\\n", "<br />");
        return text;
    }
    
    // protect character entities
    private static Regex.Replace ampersandStoreTrans = new Regex.Replace(
            "&",
            "(?s)((&)(([a-zA-Z1234]{2,8})|(#\\d{2,4});))",
            AMP_SUB+"$3"
            );
    
    private static Regex.Replace ampersandRestoreTrans = new Regex.Replace(
            AMP_SUB,
            AMP_SUB,
            "&"
            );
    
    // snippets, nomod (==...==), code (@...@) and html to handle specially
    private HashMap<String,String> snippetMap = new HashMap<String,String>(64);
    
    private static int nextKeyId = 0;
    
    private static Pattern SNIPPET_RESTORE_PATTERN = Pattern.compile(
            "(?sm)(" +XIL_KEY+ "\\d+" + XIL_END + ")" );
    
    
    // {{{ instance fields and methods ------------------------------------------
    
    private String text;
    private Block block;
    private Task task;
    private Env env;
    
    // if true, preserves whitespace at beginning of lines and prevents translation of '\n' to <br />
    private boolean preserveWhitespace;
    
    InlineMarkup() {}
    
    /**
     * translates inline markup in a block
     * @param block block to translate
     * @return the translated string
     */
    public String translate(Block block) {
        init( block, false);
        return translate();
    }
    
    /**
     * translates inline markup in a block but preserves exisiting EOL's (that is,
     * no &lt;br /&gt; are generated.
     * @param block block to translate
     * @return the translated string
     */
    public String translateKeepNL(Block block) {
        init(block, true);
        return translate();
    }
    
    public String translateKeepNL(Block block, String text) {
        init(block, true);
        this.text = text;
        return translate();
    }
    
    /**
     * translates inline markup in a string.  Used, for example, for table cells in
     * the short form of the table signature.
     * @param task current task
     * @param text string to translate
     * @return translated string
     */
    public String translate(Task task, String text) {
        this.text = text;
        this.task = task;
        env = task.getEnv();
        nextKeyId = 0;
        return translate();
    }
    
    public String translateKM(Task task, String text) {
        this.text = text;
        this.task = task;
        env = task.getEnv();
        nextKeyId = 0;
        keyValueSub();
        execMacros();
        return this.text;
    }
    public String translateKMU(Task task, String text) {
        return replaceUnkindChar(translateKM(task,text));
    }
    
    
    private void init(Block block, boolean preserveWhitespace) {
        
        this.preserveWhitespace = preserveWhitespace;
        this.text = preserveWhitespace? block.linesAsString() : block.linesAsStringTrim();
        task = block.getTask();
        env = task.getEnv();
        nextKeyId = 0;
    }
    
    private void keyValueSub() {
        text = keyValueSubTrans.apply( task, text );
    }
    
    private void execMacros() {
        text = execMacrosTrans.apply( task, text );
    }
    
    private String translate() {
        
        keyValueSub();
        execMacros();
        
        // mark special no-mod, code, and html sections of text
        text = noModTrans.apply(task, text);
        text = noModEmbeddedTrans.apply(task, text);
        text = codeTrans.apply(task, text);
        text = codeEmbeddedTrans.apply(task, text);
        text = existingMarkupTrans.apply(task, text);
        
        // links and images
        text = linkEmbeddedTrans.apply(task, text);
        text = linkTrans.apply(task, text);
        text = imgEmbeddedTrans.apply(task, text);
        text = imgTrans.apply(task, text);
        text = imgLinkEmbeddedTrans.apply(task, text);
        text = imgLinkTrans.apply(task, text);
        text = existingMarkupTrans.apply(task, text);
        
        // save '&' to restore at end, preserves any existing entites
        text = ampersandStoreTrans.replace(text);
        text = replaceUnkindChar(text);
        text = ampersandRestoreTrans.replace(text);
        
        text = phrases(task, text);
        text = existingMarkupTrans.apply(task, text);
        text = cers(text);
        
        // replace stored snippets (from html, code, and noMod)
        if( snippetMap.size() > 0 ) {
            text = env.getRegex().applyTrans( task, text, SNIPPET_RESTORE_PATTERN, new Trans() {
                public void apply(Task task,  StringBuilder sb, Matcher m ) {
                    sb.append( snippetMap.get(m.group(1)) );
                }
            });
        }
        
        if( !preserveWhitespace )
            text = breakLines(text);
        return text;
    }
    
    
    /**
     * transforms <code>&gt;</code>, <code>&lt;</code> and <code>&amp;</code> to
     * the character entity representation (<code>&amp;gt;</code>,
     * <code>&amp;lt;</code>, and
     * <code>&amp;</code>).
     * @param s text to transform
     * @return resulting translation
     */
    public static String replaceUnkindChar(String s) {
        s = s.replaceAll("&", "&amp;");
        s = s.replaceAll(">", "&gt;");
        return s.replaceAll("<", "&lt;");
    }
    
    /**
     * substitues &lt;br /&gt; for newline chars.
     * @param s text of the block
     * @return text with &lt;br /&gt; substituted
     */
    public static String breakLines(String s){
        return s.replaceAll("\n", "<br />\n");
    }
    // }}}
    
}
