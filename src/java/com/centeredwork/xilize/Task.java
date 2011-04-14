package com.centeredwork.xilize;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Much like a stack frame on an execution stack, a Task object contains registries
 * (symbol tables) and convenience methods to access them.  While the
 * accessor methods search up the stack of tasks, methods which modify
 * the registries do not.  Thus, modifications by a subtask do not affect
 * ancestor tasks.
 *
 * Task objects also keep a reference to the global environment and provide convenience
 * methods to access it.
 *
 * Most task objects are of class TaskFile type or
 * a subclass of it.
 * @see Env
 * @see TaskFile
 * @see TaskDir
 */

abstract public class Task {
    
    protected Task parent;
    
    // global environment
    protected Env env;
    
    // local symbols
    protected HashMap<String,String> defReg = new HashMap<String,String>();
    protected HashMap<String,String> abbrevReg = new HashMap<String,String>();
    protected HashMap<String,Signature> sigReg = new HashMap<String,Signature>();;
    
    /**
     * Creates a new Task instance.
     * @param parent parent task
     */
    public Task(Task parent) {
        this.parent = parent;
        if( parent != null )
            env = parent.getEnv();
    }
    
    /**
     * Creates a new Task instance with no parent task.  Only the "master task" has no
     * parent.
     */
//    protected Task() {}  // for TaskFile.Master
    
    /**
     * translates xilize markup associated with this task.
     * @throws com.centeredwork.xilize.XilizeException if cannot recover from translation errors
     */
    void xilize() throws XilizeException {}
    
    public Task getParent() { return parent; }
    
    public File getFile() { return null; }
    
    public Env getEnv() {
        return env;
    }
    public boolean isHalted() {
        return env.isHalted();
    }
    public BeanShell getBsh() { return env.getBsh(); }
    public boolean isNatural() {
        return isValueTrue(Key._Natural_.name());
    }
    
    public String getDescription() {
        return "undefined task";
    }
    
    public String toString() {
        return getDescription();
    }
    
    public String getPath() {
        return "";
    }
    
    // define, enum Key versions
    public boolean isDefined( Key key ) {
        return isDefined(key.name());
    }
    public String value( Key key ) {
        return value( key.name() );
    }
    public void define( Key key, String value) {
        define( key.name(), value);
    }
    public void defineAppend(Key key, String value) {
        defineAppend(key.name(), value);
    }
    public boolean isValueTrue( Key key ) {
        return isValueTrue(key.name());
    }
    public void undef( Key key ) {
        undef(key.name());
    }
    public Signature getSignature( Key key ) {
        return getSignature(key.name());
    }
    
    // define, string versions
    public boolean isDefined( String key ) {
        if( defReg.containsKey(key)  ) {
            return defReg.get(key).equals("") ? false : true;
        }
        return parent==null? false: parent.isDefined(key);
    }
    protected boolean _isDefined( String key ) {
        return defReg.containsKey(key) && !defReg.get(key).equals("") ? true : false;
    }
    protected boolean parentHasDefined( Key key ) {
        return parent==null? false: parent._isDefined(key.name());
    }
    public String value( String key ) {
        if( defReg.containsKey(key) && defReg.get(key).equals("") ) {
            // then the key has been "undef'd"
            return "";
        }
        if( !defReg.containsKey(key) )
            return parent==null? "": parent.value(key);
        return defReg.get(key);
    }
    public void define( String key, String value) {
        if( value.startsWith("&{literal:") && value.endsWith("}") ) {
            value = value.substring("&{literal:".length(), value.length()-1);
        }
        value = markupKM(value);
        defReg.put(key, value);
    }
    
    public void undef( String key ) {
        if( key.equals("") )
            return;
        if( isDefined(key) ) {
            define(key, "");
        }
    }
    
    
    public void defineAppend(String key, String value) {
        // todo:  decide on this.  do special keys get special handling by define()?
        define( key, value(key) + value);
    }
    public void defineDefault( String key, String value) {
        if( !isDefined(key) )
            define(key,value);
    }
    public void define(HashMap<String,String> definitions) {
        if( definitions == null)
            return;
        defReg.putAll(definitions);
    }
    
    private static final Pattern IS_TRUE_PATTERN = Pattern.compile("(?i)true|yes|1|on");
    
    public boolean isValueTrue( String key ) {
        String s = value(key);
        return IS_TRUE_PATTERN.matcher(s).matches();
    }
    
    // signature
    public void addSig( Signature sig ) {
        
        Signature existingSig = _getSignature(sig.getName());
        if( existingSig != null && isValueTrue(Key._WarnOnSigOverride_)) { 
            
            if(sig instanceof SigCustom) {
                warning(((SigCustom)sig).getLineNumber(),
                        "signature override: "+sig.getName()
                        + (existingSig instanceof SigCustom ?
                            " is also defined in " +((SigCustom)existingSig).getOwner().getPath() :
                            " is also native signature")
                        );
            } else {
                throw new IllegalStateException("signature overwritten");
            }
        }
        sigReg.put(sig.getName(), sig);
    }
    
    /**
     * gets a Signature, if "name" does not denote a registered signature the
     * unsigned block signature is used.
     * @param name signature name
     * @return the signature object
     * @see Task#_getSignature(String)
     * @see Signature
     */
    public Signature getSignature( String name ) {
        Signature sig = _getSignature(name);
        if( sig==null ) {
            sig = _getSignature(value("_unsignedBlockSig_"));
        }
        if( sig == null ) {
            error("signature "+name+" is null");
            throw new IllegalStateException("null signature");
        }
        return sig.copy();
    }
    
    /**
     * gets Signature for "name".
     * @param name signature name
     * @return the signature object or null if "name" is not found
     * @see Task#getSignature(String)
     */
    Signature _getSignature( String name ) {
        if( sigReg.containsKey(name) )
            return sigReg.get(name);
        return parent==null? null: parent._getSignature(name);
    }
    
    // inline markup translation
    public String markup(String s) {
        return env.getInline().translate(this,s);
    }
    public String markupKM(String s) {
        return env.getInline().translateKM(this, s);
    }
    public String markup(Block block) {
        return env.getInline().translate(block);
    }
    public String markupKeepEOL(Block block) {
        return env.getInline().translateKeepNL(block);
    }
    public String markupKeepEOL(int wrap, Block block) {
        return env.getInline().translateKeepNL(block, block.wrapLines(wrap));
    }
    public String markupU(Block block) {
        return env.getInline().replaceUnkindChar(block.linesAsString());
    }
    public String markupU(String s) {
        return env.getInline().replaceUnkindChar(s);
    }
    
    // abbreviations
    public void addAbbrev( String abbrev, String url ){
        abbrevReg.put(abbrev, url);
    }
    public String getUrl( String abbrev ) {
        if( abbrevReg.containsKey(abbrev) )
            return abbrevReg.get(abbrev);
        return parent==null? null: parent.getUrl(abbrev);
    }
    
    // catalog
    public void addCatalogListener(CatalogListener listener) {
        if( this instanceof TaskFile ) {
            ((TaskFile)this).catalog.addListener(listener);
        }
    }
    public boolean hasListener( Catalog.Item item ) {
        return (this instanceof TaskFile) ?
            ((TaskFile)this).catalog.hasListener((TaskFile) this, item) :
            false;
    }
    public void register( Catalog.Item item ) {
        if( this instanceof TaskFile ) {
            ((TaskFile)this).catalog.register((TaskFile) this, item);
        }
    }
    public String uniqueId() {
        if( this instanceof TaskFile ) {
            return ((TaskFile)this).catalog.uniqueId();
        }
        return "";
    }
    
    // report
    // todo:  add task info to messages
    private void _report( String msg ) {
        if( isValueTrue("_Silent_") )
            return;
        env.getReporter().report(msg);
    }
    public void report( String msg ) {
        _report(msg);
    }
    
    private void _warning( String msg ) {
        if( isValueTrue("_NoWarn_") )
            return;
        env.getReporter().warn(msg);
    }
    public void warning( String msg ) {
        _warning(msg);
    }
    public void warning( int lineNumber, String msg ) {
        _warning(getPath()+":"+lineNumber+":warning: "+msg);
    }
    
    private void _debug( String s ) {
        if( isValueTrue("_Debug_") )
            env.getReporter().debug(s);
    }
    public void debug( String s ) {
        _debug(s);
    }
    public void debug(int lineNumber, String msg) {
        _debug(getPath()+":"+lineNumber+":"+msg);
    }
    
    public void _error(String msg) {
        env.getReporter().error(msg);
    }
    public void error(String msg) {
        _error(getDescription()+":"+ msg);
    }
    public void error(int lineNumber, String msg) {
        _error(getPath()+":"+lineNumber+":"+msg);
    }
    public void error( String msg, Exception e ) {
        _error(getDescription() +": "+ msg +": "+ e.getMessage());
    }
    
    public int getErrors() {
        return env.getReporter().getErrors();
    }
    public int getWarnings() {
        return env.getReporter().getWarnings();
    }
    
    public void loadProperties( File path ) throws IllegalArgumentException, IOException {
        
        Properties props = new Properties();
        props.load(new BufferedInputStream( new FileInputStream(path)));
        Enumeration e = props.propertyNames();
        while( e.hasMoreElements() ) {
            String key = (String) e.nextElement();
            define(key, (String) props.get(key));
        }
    }
    
    public boolean isDirectory() { return this instanceof TaskDir; }
    
    public TaskDir parentDir() {
        if( parent == null ) return null;
        if( parent.isDirectory() ) return (TaskDir) parent;
        return parent.parentDir();
    }
    
    public int getDepth() {
        Task p = parentDir();
        return p==null? -1: p.getDepth();
    }
    
    public String[] sigNames() {
        Collection<String> sigs = sigReg.keySet();
        String[] sa = sigs.toArray( new String[sigs.size()]);
        Arrays.sort(sa);
        return sa;
    }
}

