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
package org.tmatesoft.svn.core.internal.io.fs;

import java.io.OutputStream;
import java.io.InputStream;

import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface IFSDeltaCollector {
    public OutputStream insertWindow(SVNDiffWindow window);
    
    public SVNDiffWindow getLastWindow();
    
    public InputStream getDeltaDataStorage(SVNDiffWindow window);
    
    public void removeWindow(SVNDiffWindow window);
    
    public int getWindowsCount();
}
