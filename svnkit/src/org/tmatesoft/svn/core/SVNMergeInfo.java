/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import java.io.File;
import java.util.Map;

/**
 * The <code>SVNMergeInfo</code> represents information about merges to a certain repository path.
 * 
 * @version 1.2.0
 * @author  TMate Software Ltd.
 * @since   1.2.0
 */
public class SVNMergeInfo {
    private String myPath;
    private File myFile;
    private Map myMergeSrcPathsToRangeLists;

    public SVNMergeInfo(String path, Map srcsToRangeLists) {
        myPath = path;
        myMergeSrcPathsToRangeLists = srcsToRangeLists;
    }

    public SVNMergeInfo(File file, Map srcsToRangeLists) {
        myFile = file;
        myMergeSrcPathsToRangeLists = srcsToRangeLists;
    }

    public String getPath() {
        return myPath;
    }

    public File getFile() {
        return myFile;
    }
    
    /**
     * keys are String paths, values - SVNMergeRange[]
     */
    public Map getMergeSourcesToMergeLists() {
        return myMergeSrcPathsToRangeLists;
    }

    public void setMergeSourcesToMergeLists(Map srcsToMergeLists) {
        myMergeSrcPathsToRangeLists = srcsToMergeLists;
    }

	public String toString() {
		return myPath + "=" + myMergeSrcPathsToRangeLists;
	}
}
