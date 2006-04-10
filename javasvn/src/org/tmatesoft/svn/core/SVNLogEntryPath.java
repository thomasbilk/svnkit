/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core;

import java.io.Serializable;

/**
 * The <b>SVNLogEntryPath</b> class encapsulates information about a single 
 * item changed in a revision. This information includes an item's path, a 
 * type of the changes made to the item, and if the item is a copy of another
 * one - information about the item's ancestor. 
 * 
 * <p>
 * <b>SVNLogEntryPath</b> objects are held by an <b>SVNLogEntry</b> object - 
 * they are representations of all the changed paths in the revision represented
 * by that <b>SVNLogEntry</b> object.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	SVNLogEntry
 */
public class SVNLogEntryPath implements Serializable {
    
    public static final char TYPE_ADDED = 'A';
    public static final char TYPE_DELETED = 'D';
    public static final char TYPE_MODIFIED = 'M';
    public static final char TYPE_REPLACED = 'R';
    
    private String myPath;
    private char myType;
    private String myCopyPath;
    private long myCopyRevision;
    
    /**
     * Constructs an <b>SVNLogEntryPath</b> object. 
     *  
     * @param path				a path that was changed in a revision
     * @param type				a type of the path change; it can be one of the following: 
     *                          <span class="javastring">'M'</span> - Modified, <span class="javastring">'A'</span> - Added, 
     *                          <span class="javastring">'D'</span> - Deleted, <span class="javastring">'R'</span> - Replaced 
     * @param copyPath			the path of the ancestor of the item represented 
     *                          by <code>path</code> (in that case if <code>path</code> 
     *                          was copied), or <span class="javakeyword">null</span> if
     *                          <code>path</code>
     * @param copyRevision		the ancestor's revision if the <code>path</code> is a branch,
     * 							or -1 if not
     */
    public SVNLogEntryPath(String path, char type, String copyPath,
            long copyRevision) {
        myPath = path;
        myType = type;
        myCopyPath = copyPath;
        myCopyRevision = copyRevision;
    }
    
    /**
     * Returns the path of the ancestor of the item represented 
     * by this object.
     * 
     * @return	the origin path from where the item, represented by this
     *          object, was copied, or <span class="javakeyword">null</span> 
     *          if it wasn't copied
     */
    public String getCopyPath() {
        return myCopyPath;
    }
    
    /**
     * Returns the revision of the ancestor of the item represented by this 
     * object.
     * 
     * @return	the revision of the origin path from where the item, 
     *          represented by this object, was copied, or -1 if the item
     *          was not copied
     */
    public long getCopyRevision() {
        return myCopyRevision;
    }
    
    /**
     * Returns the path of the item represented by this object.
     * 
     * @return  the changed path represented by this object
     */
    public String getPath() {
        return myPath;
    }
    /**
     * Gets the type of the change applied to the item represented by this
     * object. This type can be one of the following: 
     * <span class="javastring">'M'</span> - Modified, 
     * <span class="javastring">'A'</span> - Added, 
     * <span class="javastring">'D'</span> - Deleted,
     * <span class="javastring">'R'</span> - Replaced (what means that the 
     * object is first deleted, then another object of the same name is 
     * added, all within a single revision).
     * 
     * @return a type of the change as a char label
     */
    public char getType() {
        return myType;
    }
    
    /**
     * Sets the path of the item represented by this object.
     * 
     * @param path a path of an item that was changed (regarding a definite
     *             revision)
     */
    public void setPath(String path) {
    	myPath = path;
    }

    protected void setChangeType(char type){
        myType = type;
    }
    
    protected void setCopyRevision(long revision) {
        myCopyRevision = revision;
    }
    
    protected void setCopyPath(String path) {
        myCopyPath = path;
    }

    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((myPath == null) ? 0 : myPath.hashCode());
        result = PRIME * result + myType;
        result = PRIME * result + ((myCopyPath == null) ? 0 : myCopyPath.hashCode());
        result = PRIME * result + (int) (myCopyRevision ^ (myCopyRevision >>> 32));
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !(obj instanceof SVNLogEntryPath)) {
            return false;
        }
        final SVNLogEntryPath other = (SVNLogEntryPath) obj;
        return myCopyRevision == other.myCopyRevision &&
            myType == other.myType &&
            SVNLogEntry.compare(myPath, other.myPath) &&
            SVNLogEntry.compare(myCopyPath, other.myCopyPath);
    }
    
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append(myType);
        result.append(' ');
        result.append(myPath);
        if (myCopyPath != null) {
            result.append("(from ");
            result.append(myCopyPath);
            result.append(':');
            result.append(myCopyRevision);
            result.append(')');
        }
        return result.toString();
    }
    
    
}
