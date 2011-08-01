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

import org.tmatesoft.svn.core.io.SVNLocationEntry;;

/**
 * Class is used as value in hash table in returning argument of detectChanged()
 * (class FSRepository)
 * */
public class FSLogChangedPath {
    
    /*'A'dd, 'D'elete, 'M'odify, 'R'eplace*/
    char action;
    /*copyfrom and revision*/
    SVNLocationEntry copyfromEntry;
    
    public FSLogChangedPath(char newAction, SVNLocationEntry newCopyfromEntry){
        action = newAction;
        copyfromEntry = newCopyfromEntry;
    }
    
    public char getAction(){
        return action; 
    }
    
    public void setAction(char newAction){
        action = newAction;
    }
    
    public SVNLocationEntry getCopyfromEntry(){
        return copyfromEntry;
    }
    
    public void setCopyfromEntry(SVNLocationEntry newCopyfromEntry){
        copyfromEntry = newCopyfromEntry;
    }
}