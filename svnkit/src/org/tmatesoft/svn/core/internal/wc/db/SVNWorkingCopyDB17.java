/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.db;

import java.util.Map;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNWorkingCopyDB17 implements ISVNWorkingCopyDB {
    
    private Map myPathsToPristineDirs;
    private boolean myIsAutoUpgrade;
    private boolean myIsEnforceEmptyWorkQueue;
    
    private SVNWorkingCopyDB17() {
        
    }
    
    public void readInfo() {
        
    }
    
    public void parseLocalAbsPath() {
        
    }
    
}
