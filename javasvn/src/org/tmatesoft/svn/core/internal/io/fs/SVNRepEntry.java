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

import org.tmatesoft.svn.core.SVNNodeKind;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNRepEntry {
    private SVNID myId;
    private SVNNodeKind myType;
    private String myName;
    
    public SVNRepEntry(){
        
    }
    
    public SVNRepEntry(SVNID id, SVNNodeKind type, String name){
        myId = id;
        myType = type;
        myName = name;
    }
    
    public void setId(SVNID id){
        myId = id;
    }

    public void setType(SVNNodeKind type){
        myType = type;
    }

    public void setName(String name){
        myName = name;
    }
    
    public SVNID getId(){
        return myId;
    }

    public SVNNodeKind getType(){
        return myType;
    }

    public String getName(){
        return myName;
    }
}
