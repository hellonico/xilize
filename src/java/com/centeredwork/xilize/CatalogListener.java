/*
 * CatalogListener.java
 *
 * Created on June 2, 2006, 6:36 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.centeredwork.xilize;

/**
 * Listener for Catalog entries.
 *
 * @author andy
 */
public interface CatalogListener {
    
    public void entry(TaskFile task, Catalog.Item item);

    public boolean hasInterest(TaskFile task, Catalog.Item item);
}
