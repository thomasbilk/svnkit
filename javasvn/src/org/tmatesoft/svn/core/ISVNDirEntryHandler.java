/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core;

/**
 * The <b>ISVNDirEntryHandler</b> interface is used to handle information
 * about directory entries while retrieving dir contents.    
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 */
public interface ISVNDirEntryHandler {
    /**
     * Handles a directory entry passed.
     * 
     * @param dirEntry 		a directory entry
     * @see 				SVNDirEntry 
     */
    public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException; 

}
