package com.centeredwork.xilize;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * advanced regular expression support.  Instances of this class are thread safe.
 */

public class Regex {
    
    private static final int BUFFER_SIZE = 1024;
    
    private StringBuilder buffer = new StringBuilder(BUFFER_SIZE);
    
    Regex() {}
    
    /**
     * objects implementing this interface are used by
     * {@link Regex#applyTrans(Task,String,Pattern,Trans)}.
     */
    public interface Trans {
        
        /**
         * Each time the pattern given to <code>applyTrans()</code> matches, this method
         * is called.  The method body may use any of the information currently in the
         * Matcher (m.group() for example) to replace the matched string by
         * appending the replacement string(s) to <I>buffer</I>.
         * <code>applyTrans()</code> appends the unmatched text to the buffer.
         * @param task current task
         * @param buffer which accumulates the resulting string
         * @param m the pattern matcher
         * @see Regex#applyTrans(Task,String,Pattern,Trans)
         * @see Regex.Special
         */
        public void apply( Task task, StringBuilder buffer, Matcher m );
    }
    
    /**
     * an advance search and replace operation on a string.  Every occurance of <I>pattern</I> is replaced 
     * by the action of the <I>trans</I> object.
     * @param task current task
     * @param text input string to transform
     * @param pattern regular expression to match against
     * @param trans a transformation object that does something whenever the <I>pattern</I> matches
     * @return the transformed string with every occurance of <I>pattern</I> replaced 
     * by the action of the <I>trans</I> object
     * @see Regex.Special
     */
    public String applyTrans(  Task task, String text, Pattern pattern, Trans trans) {
        
        buffer.delete(0, buffer.length());
        
        Matcher m = pattern.matcher(text);
        int i = 0;
        while( m.find() ) {
            if( m.start() > 0 )
                buffer.append(text.substring(i, m.start()));
            trans.apply( task, buffer, m);
            i = m.end();
        }
        if( i==0 ) { // nothing found, return the input text
            return text;
        } else if( i<text.length()) {  // add what remains
            buffer.append(text.substring(i));
        } // else we've consumed it all
        return buffer.toString();
    }
    
    
    /**
     * same as java.lang.String.replaceAll() except uses a precompiled pattern.
     * @param text input string
     * @param pattern pattern to match against
     * @param replacement replacement string
     * @return transformed string
     */
    public static String replace( String text, Pattern pattern, String replacement ) {
        
        return pattern.matcher(text).replaceAll(replacement);
        
    }
    
    /**
     * base class for Regex.Replace and Regex.Special
     */
    protected static class Transformation {
        String marker;
        Pattern pattern;
        
        Transformation( String marker, String match ) {
            pattern = Pattern.compile(match);
            this.marker = marker;
        }
        Transformation( String marker, Pattern pattern ) {
            this.pattern = pattern;
            this.marker = marker;
        }
    }
    
    /**
     * a small enhancement over {@link Regex#replace(String,Pattern,String)}, instances
     * of this class first search for a "marker" string and do not perform any action
     * if the string is not found.
     */
    public static class Replace extends Transformation {
        
        protected String replacement;
        
        public Replace( String marker, String match, String replacement ) {
            super( marker, match );
            this.replacement = replacement;
        }
        
        public Replace( String marker, Pattern pattern, String replacement ) {
            super( marker, pattern );
            this.replacement = replacement;
        }
        
        public String replace(String text) {
            if( -1 == text.indexOf(marker) )
                return text;
            return pattern.matcher(text).replaceAll(replacement);
        }
    }
    
    /**
     * Instances of this class transform text using the <I>pattern</I> and 
     * transformation objects (implementing Regex.Trans) provided
     * to their constructors.  Instances
     * of this class first search for a "marker" string and do not perform any action
     * if the string is not found.
     * 
     * (This class is used extensively in {@link InlineMarkup}.)
     * @see Regex#applyTrans(Task,String,Pattern,Trans)
     * @see Regex.Trans
     * @see Regex.Replace contrast with Regex.Replace
     */
    public static class Special extends Transformation {
        
        protected Trans trans;
        
        public Special( String marker, String match, Trans trans ) {
            super( marker, match );
            this.trans = trans;
        }
        
        public Special( String marker, Pattern pattern, Trans trans ) {
            super( marker, pattern );
            this.trans = trans;
        }
        
        public String apply( Task task, String text ) {
            if( -1 == text.indexOf(marker) )
                return text;
            return task.getEnv().getRegex().applyTrans( task, text, pattern, trans );
        }
    }
    
}
