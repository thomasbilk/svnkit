/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNDirectory;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNProperties;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;

/**
 * The <b>SVNMoveClient</b> provides an extra client-side functionality over
 * standard (i.e. compatible with the SVN command line client) move 
 * operations. This class helps to overcome the SVN limitations regarding
 * move operations. Using <b>SVNMoveClient</b> you can easily:
 * <ul>
 * <li>move versioned items to other versioned ones  
 * within the same Working Copy, what even allows to replace items 
 * scheduled for deletion, or those that are missing but are still under
 * version control and have a node kind different from the node kind of the 
 * source (!);  
 * <li>move versioned items belonging to one Working Copy to versioned items
 * that belong to absolutely different Working Copy; 
 * <li>move versioned items to unversioned ones;
 * <li>move unversioned items to versioned ones;
 * <li>move unversioned items to unversioned ones;
 * <li>revert any of the kinds of moving listed above;
 * <li>complete a copy/move operation for a file, that is if you have
 * manually copied/moved a versioned file to an unversioned file in a Working
 * copy, you can run a 'virtual' copy/move on these files to copy/move
 * all the necessary administrative version control information.
 * </ul>
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNMoveClient extends SVNBasicClient {

    private SVNWCClient myWCClient;
    /**
     * Constructs and initializes an <b>SVNMoveClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNMoveClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNMoveClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNMoveClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
        myWCClient = new SVNWCClient(authManager, options);
    }

    protected SVNMoveClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
        myWCClient = new SVNWCClient(repositoryPool, options);
    }
    
    /**
     * Moves a source item to a destination one. 
     * 
     * <p>
     * <code>dst</code> should not exist. Furher it's considered to be versioned if
     * its parent directory is under version control, otherwise <code>dst</code>
     * is considered to be unversioned.
     * 
     * <p>
     * If both <code>src</code> and <code>dst</code> are unversioned, then simply 
     * moves <code>src</code> to <code>dst</code> in the filesystem.
     *
     * <p>
     * If <code>src</code> is versioned but <code>dst</code> is not, then 
     * exports <code>src</code> to <code>dst</code> in the filesystem and
     * removes <code>src</code> from version control.
     * 
     * <p>
     * If <code>dst</code> is versioned but <code>src</code> is not, then 
     * moves <code>src</code> to <code>dst</code> (even if <code>dst</code>
     * is scheduled for deletion).
     * 
     * <p>
     * If both <code>src</code> and <code>dst</code> are versioned and located
     * within the same Working Copy, then moves <code>src</code> to 
     * <code>dst</code> (even if <code>dst</code> is scheduled for deletion),
     * or tries to replace <code>dst</code> with <code>src</code> if the former
     * is missing and has a node kind different from the node kind of the source.
     * If <code>src</code> is scheduled for addition with history, 
     * <code>dst</code> will be set the same ancestor URL and revision from which
     * the source was copied. If <code>src</code> and <code>dst</code> are located in 
     * different Working Copies, then this method copies <code>src</code> to 
     * <code>dst</code>, tries to put the latter under version control and 
     * finally removes <code>src</code>.
     *  
     * @param  src            a source path
     * @param  dst            a destination path
     * @throws SVNException   if one of the following is true:
     *                        <ul>
     *                        <li><code>dst</code> already exists
     *                        <li><code>src</code> does not exist
     *                        </ul>
     */ 
    public void doMove(File src, File dst) throws SVNException {
        if (dst.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "File ''{0}'' already exists", dst);
            SVNErrorManager.error(err);
        } else if (!src.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Path ''{0}'' does not exist", src);
            SVNErrorManager.error(err);
        }
        // src considered as unversioned when it is not versioned
        boolean srcIsVersioned = isVersionedFile(src);
        // dst is considered as unversioned when its parent is not versioned.
        boolean dstParentIsVersioned = isVersionedFile(dst.getParentFile());

        if (!srcIsVersioned && !dstParentIsVersioned) {
            // world:world
            SVNFileUtil.rename(src, dst);
        } else if (!dstParentIsVersioned) {
            // wc:world
            // 1. export to world
            SVNFileUtil.copy(src, dst, false, false);

            // 2. delete in wc.
            myWCClient.doDelete(src, true, false);
        } else if (!srcIsVersioned) {
            // world:wc (add, if dst is 'deleted' it will be replaced)
            SVNFileUtil.rename(src, dst);
            myWCClient.doAdd(dst, false, false, false, true);
        } else {
            // wc:wc.

            // 1. collect information on src and dst entries.
            SVNWCAccess srcAccess = SVNWCAccess.create(src);
            SVNWCAccess dstAccess = SVNWCAccess.create(dst);
            SVNEntry srcEntry = srcAccess.getTargetEntry();
            SVNEntry dstEntry = dstAccess.getTargetEntry();

            SVNProperties srcProps = srcAccess.getAnchor().getProperties(
                    srcAccess.getTargetName(), false);
            SVNProperties dstProps = dstAccess.getAnchor().getProperties(
                    dstAccess.getTargetName(), false);

            SVNEntry dstParentEntry = dstAccess.getAnchor().getEntries()
                    .getEntry("", false);

            File srcWCRoot = SVNWCUtil.getWorkingCopyRoot(src, true);
            File dstWCRoot = SVNWCUtil.getWorkingCopyRoot(dst, true);
            boolean sameWC = srcWCRoot != null && srcWCRoot.equals(dstWCRoot);

            if (sameWC
                    && dstEntry != null
                    && (dstEntry.isScheduledForDeletion() || dstEntry.getKind() != srcEntry
                            .getKind())) {
                // attempt replace.
                SVNFileUtil.copy(src, dst, false, false);
                try {
                    myWCClient.doAdd(dst, false, false, false, true);
                } catch (SVNException e) {
                    // will be thrown on obstruction.
                }
                myWCClient.doDelete(src, true, false);
                return;
            }

            // 2. do manual copy of the file or directory
            SVNFileUtil.copy(src, dst, false, sameWC);

            // 3. update dst dir and dst entry in parent.
            if (!sameWC) {
                // just add dst (at least try to add, files already there).
                try {
                    myWCClient.doAdd(dst, false, false, false, true);
                } catch (SVNException e) {
                    // obstruction
                }
            } else if (srcEntry.isFile()) {
                if (dstEntry == null) {
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(
                            dst.getName());
                }

                String srcURL = srcEntry.getURL();
                String srcCFURL = srcEntry.getCopyFromURL();
                long srcRevision = srcEntry.getRevision();
                long srcCFRevision = srcEntry.getCopyFromRevision();
                // copy props!
                srcProps.copyTo(dstProps);

                if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                    dstEntry.scheduleForAddition();
                    dstEntry.setCopyFromRevision(srcCFRevision);
                    dstEntry.setCopyFromURL(srcCFURL);
                    dstEntry.setKind(SVNNodeKind.FILE);
                    dstEntry.setRevision(srcRevision);
                    dstEntry.setCopied(true);
                } else if (!srcEntry.isCopied()
                        && !srcEntry.isScheduledForAddition()) {
                    dstEntry.setCopied(true);
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.FILE);
                    dstEntry.setCopyFromRevision(srcRevision);
                    dstEntry.setCopyFromURL(srcURL);
                } else {
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.FILE);
                    if (!dstEntry.isScheduledForReplacement()) {
                        dstEntry.setRevision(0);
                    }
                }
                dstAccess.getAnchor().getEntries().save(true);

            } else if (srcEntry.isDirectory()) {
                if (dstEntry == null) {
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(
                            dst.getName());
                }

                srcEntry = srcAccess.getTarget().getEntries().getEntry("",
                        false);

                String srcURL = srcEntry.getURL();
                String srcCFURL = srcEntry.getCopyFromURL();
                String dstURL = dstParentEntry.getURL();
                String repositoryRootURL = dstParentEntry.getRepositoryRoot();
                long srcRevision = srcEntry.getRevision();
                long srcCFRevision = srcEntry.getCopyFromRevision();

                dstURL = SVNPathUtil
                        .append(dstURL, SVNEncodingUtil.uriEncode(dst.getName()));
                if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                    srcProps.copyTo(dstProps);
                    dstEntry.scheduleForAddition();
                    dstEntry.setCopyFromRevision(srcCFRevision);
                    dstEntry.setCopyFromURL(srcCFURL);
                    dstEntry.setKind(SVNNodeKind.DIR);
                    dstEntry.setRevision(srcRevision);
                    dstEntry.setCopied(true);
                    dstAccess.getAnchor().getEntries().save(true);
                    // update URL in children.
                    try {
                        dstAccess = SVNWCAccess.create(dst);
                        dstAccess.open(false, true);
                        SVNDirectory dstDir = dstAccess.getTarget();
                        dstDir.updateURL(dstURL, true);
                    } finally {
                        dstAccess.close(false);
                    }
                } else if (!srcEntry.isCopied()
                        && !srcEntry.isScheduledForAddition()) {
                    // versioned (deleted, replaced, or normal).
                    srcProps.copyTo(dstProps);
                    dstEntry.setCopied(true);
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.DIR);
                    dstEntry.setCopyFromRevision(srcRevision);
                    dstEntry.setCopyFromURL(srcURL);
                    dstAccess.getAnchor().getEntries().save(true);
                    // update URL, CF-URL and CF-REV in children.
                    try {
                        dstAccess = SVNWCAccess.create(dst);
                        dstAccess.open(false, true);
                        SVNDirectory dstDir = dstAccess.getTarget();
                        dstEntry = dstDir.getEntries().getEntry("", false);
                        dstEntry.setCopied(true);
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.DIR);
                        dstEntry.setCopyFromRevision(srcRevision);
                        dstEntry.setURL(dstURL);
                        dstEntry.setCopyFromURL(srcURL);
                        dstEntry.setRepositoryRoot(repositoryRootURL);

                        SVNCopyClient.updateCopiedDirectory(dstDir, "", dstURL, repositoryRootURL, null, -1);
                        dstDir.getEntries().save(true);
                    } finally {
                        dstAccess.close(false);
                    }
                } else {
                    // unversioned entry (copied or added)
                    dstAccess.getAnchor().getEntries().deleteEntry(
                            dst.getName());
                    dstAccess.getAnchor().getEntries().save(true);
                    SVNFileUtil.deleteAll(dst, this);
                    SVNFileUtil.copy(src, dst, false, false);
                    myWCClient.doAdd(dst, false, false, false, true);
                }
            }

            srcAccess.close(false);
            dstAccess.close(false);

            // now delete src (if it is not the same as dst :))
            try {
                myWCClient.doDelete(src, true, false);
            } catch (SVNException e) {
                //
            }
        }
    }

    /**
     * Reverts a previous move operation back. Provided in pair with {@link #doMove(File, File) doMove()} 
     * and used to roll back move operations. In this case <code>src</code> is
     * considered to be the target of the previsous move operation, and <code>dst</code>
     * is regarded to be the source of that same operation which have been moved
     * to <code>src</code> and now is to be restored. 
     * 
     * <p>
     * <code>dst</code> could exist in that case if it has been a WC directory
     * that was scheduled for deletion during the previous move operation. Furher 
     * <code>dst</code> is considered to be versioned if its parent directory is 
     * under version control, otherwise <code>dst</code> is considered to be unversioned.
     * 
     * <p>
     * If both <code>src</code> and <code>dst</code> are unversioned, then simply 
     * moves <code>src</code> back to <code>dst</code> in the filesystem.
     *
     * <p>
     * If <code>src</code> is versioned but <code>dst</code> is not, then 
     * unmoves <code>src</code> to <code>dst</code> in the filesystem and
     * removes <code>src</code> from version control.
     * 
     * <p>
     * If <code>dst</code> is versioned but <code>src</code> is not, then 
     * first tries to make a revert on <code>dst</code> - if it has not been committed
     * yet, it will be simply reverted. However in the case <code>dst</code> has been already removed 
     * from the repository, <code>src</code> will be copied back to <code>dst</code>
     * and scheduled for addition. Then <code>src</code> is removed from the filesystem.
     * 
     * <p>
     * If both <code>src</code> and <code>dst</code> are versioned then the 
     * following situations are possible:
     * <ul>
     * <li>If <code>dst</code> is still scheduled for deletion, then it is
     * reverted back and <code>src</code> is scheduled for deletion.
     * <li>in the case if <code>dst</code> exists but is not scheduled for 
     * deletion, <code>src</code> is cleanly exported to <code>dst</code> and
     * removed from version control.
     * <li>if <code>dst</code> and <code>src</code> are from different repositories
     * (appear to be in different Working Copies), then <code>src</code> is copied
     * to <code>dst</code> (with scheduling <code>dst</code> for addition, but not
     * with history since copying is made in the filesystem only) and removed from
     * version control.
     * <li>if both <code>dst</code> and <code>src</code> are in the same 
     * repository (appear to be located in the same Working Copy) and: 
     *    <ul style="list-style-type: lower-alpha">
     *    <li>if <code>src</code> is scheduled for addition with history, then
     *    copies <code>src</code> to <code>dst</code> specifying the source
     *    ancestor's URL and revision (i.e. the ancestor of the source is the
     *    ancestor of the destination);
     *    <li>if <code>src</code> is already under version control, then
     *    copies <code>src</code> to <code>dst</code> specifying the source
     *    URL and revision as the ancestor (i.e. <code>src</code> itself is the
     *    ancestor of <code>dst</code>);
     *    <li>if <code>src</code> is just scheduled for addition (without history),
     *    then simply copies <code>src</code> to <code>dst</code> (only in the filesystem,
     *    without history) and schedules <code>dst</code> for addition;  
     *    </ul>
     * then <code>src</code> is removed from version control.
     * </ul>
     * 
     * @param  src            a source path
     * @param  dst            a destination path
     * @throws SVNException   if <code>src</code> does not exist
     * 
     */
    // move that considered as move undo.
    public void undoMove(File src, File dst) throws SVNException {
        // dst could exists, if it is deleted directory.
        if (!src.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "Path ''{0}'' does not exist", src);
            SVNErrorManager.error(err);
        }
        // src considered as unversioned when it is not versioned
        boolean srcIsVersioned = isVersionedFile(src);
        // dst is considered as unversioned when its parent is not versioned.
        boolean dstParentIsVersioned = isVersionedFile(dst.getParentFile());

        if (!srcIsVersioned && !dstParentIsVersioned) {
            // world:world
            SVNFileUtil.rename(src, dst);
        } else if (!dstParentIsVersioned) {
            // wc:world
            // 1. export to world
            SVNFileUtil.copy(src, dst, false, false);

            // 2. delete in wc.
            myWCClient.doDelete(src, true, false);
        } else if (!srcIsVersioned) {
            // world:wc (add, if dst is 'deleted' it will be replaced)
            SVNFileUtil.rename(src, dst);
            // dst should probably be deleted, in this case - revert it
            SVNWCAccess dstAccess = SVNWCAccess.create(dst);
            SVNEntry dstEntry = dstAccess.getTargetEntry();
            if (dstEntry != null && dstEntry.isScheduledForDeletion()) {
                myWCClient.doRevert(dst, true);
            } else {
                myWCClient.doAdd(dst, false, false, false, true);
            }
        } else {
            // wc:wc.
            SVNWCAccess srcAccess = SVNWCAccess.create(src);
            SVNWCAccess dstAccess = SVNWCAccess.create(dst);
            SVNEntry srcEntry = srcAccess.getTargetEntry();
            SVNEntry dstEntry = dstAccess.getTargetEntry();
            SVNEntry dstParentEntry = dstAccess.getAnchor().getEntries().getEntry("", false);

            if (dstEntry != null && dstEntry.isScheduledForDeletion()) {
                // clear undo.
                myWCClient.doRevert(dst, true);
                myWCClient.doDelete(src, true, false);
                return;
            }

            boolean sameWC = dstParentEntry.getUUID() != null
                    && dstParentEntry.getUUID().equals(srcEntry.getUUID());

            // 2. do manual copy of the file or directory
            SVNFileUtil.copy(src, dst, false, sameWC);

            // obstruction assertion.
            if (dstEntry != null && dstEntry.getKind() != srcEntry.getKind()) {
                // ops have no sence->target is obstructed, just export src to
                // dst and delete src.
                myWCClient.doDelete(src, true, false);
                return;
            }

            // 3. update dst dir and dst entry in parent.
            if (!sameWC) {
                // just add dst (at least try to add, files already there).
                try {
                    myWCClient.doAdd(dst, false, false, false, true);
                } catch (SVNException e) {
                    // obstruction
                }
            } else if (srcEntry.isFile()) {
                if (dstEntry == null) {
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(
                            dst.getName());
                }

                String srcURL = srcEntry.getURL();
                String srcCFURL = srcEntry.getCopyFromURL();
                long srcRevision = srcEntry.getRevision();
                long srcCFRevision = srcEntry.getCopyFromRevision();

                if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                    dstEntry.scheduleForAddition();
                    dstEntry.setCopyFromRevision(srcCFRevision);
                    dstEntry.setCopyFromURL(srcCFURL);
                    dstEntry.setKind(SVNNodeKind.FILE);
                    dstEntry.setRevision(srcRevision);
                    dstEntry.setCopied(true);
                } else if (!srcEntry.isCopied()
                        && !srcEntry.isScheduledForAddition()) {
                    dstEntry.setCopied(true);
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.FILE);
                    dstEntry.setCopyFromRevision(srcRevision);
                    dstEntry.setCopyFromURL(srcURL);
                } else {
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.FILE);
                    if (!dstEntry.isScheduledForReplacement()) {
                        dstEntry.setRevision(0);
                    }
                }
                dstAccess.getAnchor().getEntries().save(true);

            } else if (srcEntry.isDirectory()) {
                if (dstEntry == null) {
                    dstEntry = dstAccess.getAnchor().getEntries().addEntry(
                            dst.getName());
                }

                String srcURL = srcEntry.getURL();
                String srcCFURL = srcEntry.getCopyFromURL();
                String dstURL = dstParentEntry.getURL();
                long srcRevision = srcEntry.getRevision();
                long srcCFRevision = srcEntry.getCopyFromRevision();
                String repositoryRootURL = srcEntry.getRepositoryRoot();

                dstURL = SVNPathUtil
                        .append(dstURL, SVNEncodingUtil.uriEncode(dst.getName()));
                if (srcEntry.isScheduledForAddition() && srcEntry.isCopied()) {
                    dstEntry.scheduleForAddition();
                    dstEntry.setCopyFromRevision(srcCFRevision);
                    dstEntry.setCopyFromURL(srcCFURL);
                    dstEntry.setKind(SVNNodeKind.DIR);
                    dstEntry.setRevision(srcRevision);
                    dstEntry.setCopied(true);
                    dstAccess.getAnchor().getEntries().save(true);
                    // update URL in children.
                    try {
                        dstAccess = SVNWCAccess.create(dst);
                        dstAccess.open(false, true);
                        SVNDirectory dstDir = dstAccess.getTarget();
                        dstDir.updateURL(dstURL, true);
                    } finally {
                        dstAccess.close(false);
                    }
                } else if (!srcEntry.isCopied()
                        && !srcEntry.isScheduledForAddition()) {
                    dstEntry.setCopied(true);
                    dstEntry.scheduleForAddition();
                    dstEntry.setKind(SVNNodeKind.DIR);
                    dstEntry.setCopyFromRevision(srcRevision);
                    dstEntry.setCopyFromURL(srcURL);
                    dstAccess.getAnchor().getEntries().save(true);
                    // update URL, CF-URL and CF-REV in children.
                    try {
                        dstAccess = SVNWCAccess.create(dst);
                        dstAccess.open(false, true);
                        SVNDirectory dstDir = dstAccess.getTarget();
                        dstEntry = dstDir.getEntries().getEntry("", false);
                        dstEntry.setCopied(true);
                        dstEntry.scheduleForAddition();
                        dstEntry.setKind(SVNNodeKind.DIR);
                        dstEntry.setCopyFromRevision(srcRevision);
                        dstEntry.setURL(dstURL);
                        dstEntry.setCopyFromURL(srcURL);
                        dstEntry.setRepositoryRoot(repositoryRootURL);

                        SVNCopyClient.updateCopiedDirectory(dstDir, "", dstURL, repositoryRootURL,  null, -1);
                        dstDir.getEntries().save(true);
                    } finally {
                        dstAccess.close(false);
                    }
                } else {
                    // replay
                    dstAccess.getAnchor().getEntries().deleteEntry(dst.getName());
                    dstAccess.getAnchor().getEntries().save(true);
                    SVNFileUtil.deleteAll(dst, this);
                    SVNFileUtil.copy(src, dst, false, false);
                    myWCClient.doAdd(dst, false, false, false, true);
                }
            }

            srcAccess.close(false);
            dstAccess.close(false);

            // now delete src.
            try {
                myWCClient.doDelete(src, true, false);
            } catch (SVNException e) {
                //
            }
        }
    }
    
    /**
     * Copies/moves administrative version control information of a source file 
     * to administrative information of a destination file.
     * For example, if you have manually copied/moved a source file to a target one 
     * (manually means just in the filesystem, not using version control operations) and then
     * would like to turn this copying/moving into a complete version control copy
     * or move operation, use this method that will finish all the work for you - it
     * will copy/move all the necessary administrative information (kept in the source
     * <i>.svn</i> directory) to the target <i>.svn</i> directory. 
     * 
     * <p>
     * In that case when you have your files copied/moved in the filesystem, you
     * can not perform standard (version control) copying/moving - since the target already exists and
     * the source may be already deleted. Use this method to overcome that restriction.  
     * 
     * @param  src           a source file path (was copied/moved to <code>dst</code>)
     * @param  dst           a destination file path
     * @param  move          if <span class="javakeyword">true</span> then
     *                       completes moving <code>src</code> to <code>dst</code>,
     *                       otherwise completes copying <code>src</code> to <code>dst</code>
     * @throws SVNException  if one of the following is true:
     *                       <ul>
     *                       <li><code>move = </code><span class="javakeyword">true</span> and <code>src</code>
     *                       still exists
     *                       <li><code>dst</code> does not exist
     *                       <li><code>dst</code> is a directory 
     *                       <li><code>src</code> is a directory
     *                       <li><code>src</code> is not under version control
     *                       <li><code>dst</code> is already under version control
     *                       <li>if <code>src</code> is copied but not scheduled for
     *                       addition, and JavaSVN is not able to locate the copied
     *                       directory root for <code>src</code>
     *                       </ul>
     */
    public void doVirtualCopy(File src, File dst, boolean move) throws SVNException {
        SVNFileType srcType  = SVNFileType.getType(src);
        SVNFileType dstType  = SVNFileType.getType(dst);
        
        String opName = move ? "move" : "copy";
        if (move && srcType != SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "Cannot perform 'virtual' {0}: ''{1}'' still exists", new Object[] {opName, src});
            SVNErrorManager.error(err);
        }
        if (dstType == SVNFileType.NONE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Cannot perform 'virtual' {0}: ''{1}'' does not exist", new Object[] {opName, dst});
            SVNErrorManager.error(err);
        }
        if (dstType == SVNFileType.DIRECTORY) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot perform 'virtual' {0}: ''{1}'' is a directory", new Object[] {opName, dst});
            SVNErrorManager.error(err);
        }
        if (!move && srcType == SVNFileType.DIRECTORY) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot perform 'virtual' {0}: ''{1}'' is a directory", new Object[] {opName, src});
            SVNErrorManager.error(err);
        }
        
        SVNWCAccess srcAccess = createWCAccess(src);
        String cfURL = null;
        boolean added = false;
        long cfRevision = -1;
        try {
            srcAccess.open(false, false);
            SVNEntry srcEntry = srcAccess.getTargetEntry();
            if (srcEntry == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", src);
                SVNErrorManager.error(err);
            }
            if (srcEntry.isCopied() && !srcEntry.isScheduledForAddition()) {
                cfURL = getCopyFromURL(src.getParentFile(), SVNEncodingUtil.uriEncode(src.getName()));
                cfRevision = getCopyFromRevision(src.getParentFile());
                if (cfURL == null || cfRevision < 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "Cannot locate copied directory root for ''{0}''", src);
                    SVNErrorManager.error(err);
                }
                added = false;
            } else {
                cfURL = srcEntry.isCopied() ? srcEntry.getCopyFromURL() : srcEntry.getURL();
                cfRevision = srcEntry.isCopied() ? srcEntry.getCopyFromRevision() : srcEntry.getRevision();
                added = srcEntry.isScheduledForAddition() && !srcEntry.isCopied();
            }
        } finally {
            srcAccess.close(false);
        }
        if (!move) {
            myWCClient.doDelete(src, true, false);
        }
        if (added) {
            myWCClient.doAdd(dst, true, false, false, false);            
            return;
        }

        SVNWCAccess dstAccess = createWCAccess(dst);
        try {
            dstAccess.open(true, false);
            SVNEntry dstEntry = dstAccess.getTargetEntry();
            if (dstEntry != null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_EXISTS, "''{0}'' is already under version control", dst);
                SVNErrorManager.error(err);                
            }
            dstEntry = dstAccess.getAnchor().getEntries().addEntry(dst.getName());
            dstEntry.setCopyFromURL(cfURL);
            dstEntry.setCopyFromRevision(cfRevision);
            dstEntry.setCopied(true);
            dstEntry.setKind(SVNNodeKind.FILE);
            dstEntry.scheduleForAddition();
            
            dstAccess.getAnchor().getEntries().save(true);
        } finally {
            dstAccess.close(true);
        }
        
    }

    private static boolean isVersionedFile(File file) {
        SVNWCAccess wcAccess;
        try {
            wcAccess = SVNWCAccess.create(file);
        } catch (SVNException e) {
            return false;
        }
        try {
            return wcAccess != null && wcAccess.getTargetEntry() != null;
        } catch (SVNException e) {
            return false;
        }
    }
    
    private static String getCopyFromURL(File path, String urlTail) throws SVNException {
        if (path == null) {
            return null;
        }
        SVNWCAccess wcAccess = null;
        try {
            wcAccess = SVNWCAccess.create(path);
        } catch (SVNException e) {
            return null;
        }
        // urlTail is either name of an entry
        SVNEntry entry = wcAccess.getTargetEntry();
        if (entry == null) {
            return null;
        }
        String cfURL = entry.getCopyFromURL();
        if (cfURL != null) {
            return SVNPathUtil.append(cfURL, urlTail);
        }
        urlTail = SVNPathUtil.append(SVNEncodingUtil.uriEncode(path.getName()), urlTail);
        path = path.getParentFile();
        return getCopyFromURL(path, urlTail);
    }

    private static long getCopyFromRevision(File path) throws SVNException {
        if (path == null) {
            return -1;
        }
        SVNWCAccess wcAccess = null;
        try {
            wcAccess = SVNWCAccess.create(path);
        } catch (SVNException e) {
            return -1;
        }
        // urlTail is either name of an entry
        SVNEntry entry = wcAccess.getTargetEntry();
        if (entry == null) {
            return -1;
        }
        long rev = entry.getCopyFromRevision();
        if (rev >= 0) {
            return rev;
        }
        path = path.getParentFile();
        return getCopyFromRevision(path);
    }
}
