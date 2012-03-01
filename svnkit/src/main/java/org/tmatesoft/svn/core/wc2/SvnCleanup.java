package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.wc2.SvnOperation;

/**
 * Cleanup opeartion. Recursively cleans up the working copy, removing locks and resuming
 * unfinished operations.
 * 
 * <p/>
 * If you ever get a "working copy locked" error, use this method to remove
 * stale locks and get your working copy into a usable state again.
 * 
 * <p>
 * This method operates only on working copies and does not open any network
 * connection.
 * 
 * <p/>
 * {@link #run()} method throws  {@link SVNException} if one of the following is true:
 * <ul>
 * <li><code>path</code> does not exist <li><code>path</code>'s
 * parent directory is not under version control
 * </ul>
 *             
 * @author TMate Software Ltd.
 */
public class SvnCleanup extends SvnOperation<Void> {
	
	private boolean deleteWCProperties;

    protected SvnCleanup(SvnOperationFactory factory) {
        super(factory);
    }
    
    /**
    * Gets whether or not DAV specific <span class="javastring">"svn:wc:"</span> properties
    * should be removed from the working copy
    * 
    * @return <code>true</code> if properties will be removed, otherwise <code>false</code>
    */
    public boolean isDeleteWCProperties() {
        return deleteWCProperties;
    }

    /**
     * Sets whether or not DAV specific <span class="javastring">"svn:wc:"</span> properties
     * should be removed from the working copy
     * 
     * @param deleteWCProperties <code>true</code> if properties will be removed, otherwise <code>false</code>
     */
    public void setDeleteWCProperties(boolean deleteWCProperties) {
        this.deleteWCProperties = deleteWCProperties;
    }

}
