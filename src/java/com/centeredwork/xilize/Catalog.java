/*
 Copyright (c) 2004 - 2006 Andy Streich
 Distributed under the GNU General Public License available at http://www.gnu.org/licenses/gpl.html,
 a copy of which is provided with the Xilize source code.
 */

package com.centeredwork.xilize;

import java.util.ArrayList;

/**
 * A registry of items that go into TOC's, Sitemaps, DirMaps, and other collections of 
 * references to elements in HTML pages (someday Endnotes and Citations) which
 * implement the CatalogListener interface.
 * 
 * <p>Items which call register() are passed to the CatalogListeners which can choose
 * to include (and format) the item in their collection.
 *
 * <p>All TaskFile and TaskDir objects have a local catalog which is linked
 * up the directory tree to the project root's.
 *
 *
 * @author Andy Streich
 */
public class Catalog implements CatalogListener {
    
    public interface Item {
        String getId();
        String getText();
        String getExtra();
        int getLevel();
    }
    
    private ArrayList<CatalogListener> listeners = new ArrayList<CatalogListener>();
    private String idPrefix;
    private int idCount;
    
    public Catalog(TaskFile task) {
        idPrefix = task.value(Key._IdPrefix_);
        if( idPrefix.equals("") && task.parentDir() != null ) {
            task.error("key _IdPrefix_ has been undefined, catalog broken");
        }
        // so we can notify listeners all the way up the tree
        TaskDir td = task.parentDir();
        if( td != null )
            addListener(td.getCatalog());
    }
    
    public void register(TaskFile task, Item item) {
        for(CatalogListener listen : listeners ) {
            listen.entry(task, item);
        }
    }
    
    public boolean hasListener(TaskFile task, Item item) {
        for(CatalogListener listen : listeners ) {
            if( listen.hasInterest(task, item) )
                return true;
        }
        return false;
    }
    
    public void addListener(CatalogListener listen) {
        if( listen != null)
            listeners.add(listen);
    }
    
    public void removeListener(CatalogListener listen) {
        listeners.remove(listen);
    }
    
    public String uniqueId() {
        idCount++;
        return idPrefix + idCount;
    }
    
    // CatalogListener interface implementation:
    
    public void entry(TaskFile task, Catalog.Item item) {
        register(task,item);
    }
    
    public boolean hasInterest(TaskFile task, Catalog.Item item) {
        return hasListener(task,item);
    }
    
}
