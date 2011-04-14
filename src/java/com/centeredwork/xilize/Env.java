package com.centeredwork.xilize;

import java.lang.reflect.InvocationTargetException;


/**
 * This class provides access to several utility objects. An instance
 * is created by the master task.
 * @see Reporter
 * @see InlineMarkup
 * @see Regex
 */
public class Env {
    
    private Reporter reporter;
    private InlineMarkup inline = new InlineMarkup();
    private Regex regex = new Regex();
    private BeanShell bsh = new BeanShell();
    private boolean halt;
    
    /**
     * creates an instance of Env with the given reporter object
     * @param reporter an object supporting the reporter interface
     */
    public Env(Reporter reporter, BeanShell bsh) {
        this.reporter = reporter;
		this.bsh = bsh;
    }
    
    void reset() {
        reporter = reporter.newInstance();
		bsh = bsh.newInstance();
        halt = false;
    }
    
    public Reporter getReporter() {
        return reporter;
    }
    
    public InlineMarkup getInline() {
        return inline;
    }
    
    public Regex getRegex() {
        return regex;
    }

    public BeanShell getBsh() { return bsh; }
    
    /**
     * called by host environment to test if translation is running.
     * @return true if user has halted translation
     */
    public boolean isHalted() {
        return halt;
    }
    
    /**
     * called by host environment to interrupt a running translation.
     */
    public void userHalt() {
        if( !halt )
            halt = true;
        
    }
    
    
}




