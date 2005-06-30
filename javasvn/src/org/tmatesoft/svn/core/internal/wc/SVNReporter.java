/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

import java.io.File;
import java.util.Date;
import java.util.Iterator;

public class SVNReporter implements ISVNReporterBaton {

    private SVNWCAccess myWCAccess;
    private boolean myIsRecursive;
    private boolean myIsRestore;

    public SVNReporter(SVNWCAccess wcAccess, boolean restoreFiles, boolean recursive) {
        myWCAccess = wcAccess;
        myIsRecursive = recursive;
        myIsRestore = restoreFiles;
    }

    public void report(ISVNReporter reporter) throws SVNException {
        try {
            SVNEntries targetEntries = myWCAccess.getTarget().getEntries();
            SVNEntries anchorEntries = myWCAccess.getAnchor().getEntries();
            SVNEntry targetEntry = anchorEntries.getEntry(myWCAccess.getTargetName(), true);

            if (targetEntry == null || targetEntry.isHidden() || 
                    (targetEntry.isDirectory() && targetEntry.isScheduledForAddition())) {
                DebugLog.log("deleted file reported");
                long revision = anchorEntries.getEntry("", true).getRevision();
                reporter.setPath("", null, revision, targetEntry != null ? targetEntry.isIncomplete() : true);
                reporter.deletePath("");
                reporter.finishReport();
                return;
            }
            long revision = targetEntry.isFile() ? targetEntry.getRevision() : 
                targetEntries.getEntry("", true).getRevision();
            if (revision < 0) {                
                revision = anchorEntries.getEntry("", true).getRevision();
            }
            reporter.setPath("", null, revision, targetEntry.isIncomplete());
            boolean missing = !targetEntry.isScheduledForDeletion() &&  
                !myWCAccess.getAnchor().getFile(myWCAccess.getTargetName(), false).exists();
            
            if (targetEntry.isDirectory()) {
                if (missing) {
                    reporter.deletePath("");
                } else {
                    DebugLog.log("reporting dir entries: " + myIsRecursive);
                    reportEntries(reporter, myWCAccess.getTarget(), "", targetEntry.isIncomplete(), myIsRecursive);
                }
            } else if (targetEntry.isFile()){
                if (missing) {
                    restoreFile(myWCAccess.getAnchor(), targetEntry.getName());
                }
                // report either linked path or entry path
                String url = targetEntry.getURL();
                SVNEntry parentEntry = targetEntries.getEntry("", true);
                String parentURL = parentEntry.getURL();
                String expectedURL = PathUtil.append(parentURL, PathUtil.encode(targetEntry.getName()));
                if (!expectedURL.equals(url)) {
                    reporter.linkPath(SVNRepositoryLocation.parseURL(url), "", targetEntry.getLockToken(), targetEntry.getRevision(), false);
                } else if (targetEntry.getRevision() != parentEntry.getRevision() || targetEntry.getLockToken() != null) {
                    reporter.setPath("", targetEntry.getLockToken(), targetEntry.getRevision(), false);
                }            
            }
            reporter.finishReport();
        } catch (Throwable th) {
            th.printStackTrace();
            DebugLog.error(th);
            reporter.abortReport();
            SVNErrorManager.error(0, th);
        }
    }
    
    private void reportEntries(ISVNReporter reporter, SVNDirectory directory, String dirPath, boolean reportAll, boolean recursive) throws SVNException {
        SVNEntries entries = directory.getEntries();
        long baseRevision = entries.getEntry("", true).getRevision();

        SVNExternalInfo[] externals = myWCAccess.addExternals(directory,
                directory.getProperties("", false).getPropertyValue(SVNProperty.EXTERNALS));
        for(int i = 0; externals != null && i < externals.length; i++) {
            externals[i].setOldExternal(externals[i].getNewURL(), externals[i].getNewRevision());
        }
        
        for (Iterator e = entries.entries(true); e.hasNext();) {
            SVNEntry entry = (SVNEntry) e.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            String path = "".equals(dirPath) ? entry.getName() : PathUtil.append(dirPath, entry.getName());
            if (entry.isDeleted() || entry.isAbsent()) {
                if (!reportAll) {
                    reporter.deletePath(path);
                }
                continue;
            }
            if (entry.isScheduledForAddition()) {
                continue;
            }        
            File file = directory.getFile(entry.getName(), false);
            boolean missing = !file.exists(); 
            if (entry.isFile()) {
                if (!reportAll) {
                    // check svn:special files -> symlinks that could be directory.
                    boolean special = SVNFileUtil.isWindows && 
                        directory.getProperties(entry.getName(), false).getPropertyValue(SVNProperty.SPECIAL) != null;
                    
                    if ((special && !file.exists()) || (!special && file.isDirectory())) {
                        reporter.deletePath(path);
                        continue;
                    }
                }
                if (missing && !entry.isScheduledForDeletion() && !entry.isScheduledForReplacement()) {
                    restoreFile(directory, entry.getName());
                }
                String url = entry.getURL();
                String parentURL = entries.getPropertyValue("", SVNProperty.URL);
                String expectedURL = PathUtil.append(parentURL, PathUtil.encode(entry.getName()));
                if (reportAll) {
                    if (!url.equals(expectedURL) && !entry.isScheduledForAddition() && !entry.isScheduledForReplacement()) {
                        reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, entry.getLockToken(), entry.getRevision(), false);
                    } else {
                        reporter.setPath(path, entry.getLockToken(), entry.getRevision(), false);
                    }
                } else if (!entry.isScheduledForReplacement() && !url.equals(expectedURL)) {
                    // link path
                    reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, entry.getLockToken(), entry.getRevision(), false);
                } else if (entry.getRevision() != baseRevision || entry.getLockToken() != null) {
                    reporter.setPath(path, entry.getLockToken(), entry.getRevision(), false);
                }
            } else if (entry.isDirectory() && recursive) {
                if (missing || directory.getChildDirectory(entry.getName()) == null) {
                    if (!reportAll) {
                        reporter.deletePath(path);
                    }
                    return;
                }
                if (file.isFile()) {
                    SVNErrorManager.error(3, null);
                }
                SVNDirectory childDir = directory.getChildDirectory(entry.getName());
                SVNEntry childEntry = childDir.getEntries().getEntry("", true);
                String url = childEntry.getURL();
                if (reportAll) {
                    if (!url.equals(entry.getURL())) {
                        reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.isIncomplete());
                    } else {
                        reporter.setPath(path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.isIncomplete());
                    }
                } else if (!url.equals(entry.getURL())) {
                    reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, childEntry.getLockToken(), childEntry.getRevision(), 
                            childEntry.isIncomplete());
                } else if (childEntry.getLockToken() != null || childEntry.getRevision() != baseRevision) {
                    reporter.setPath(path, childEntry.getLockToken(), childEntry.getRevision(), 
                            childEntry.isIncomplete());
                }
                reportEntries(reporter, childDir, path, childEntry.isIncomplete(), recursive);
            }
        }
    }
    
    private void restoreFile(SVNDirectory dir, String name) throws SVNException {
        if (!myIsRestore) {
            return;
        }
        SVNProperties props = dir.getProperties(name, false);
        SVNEntry entry = dir.getEntries().getEntry(name, true);
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
        
        File src = dir.getBaseFile(name, false);
        File dst = dir.getFile(name, false);
        SVNTranslator.translate(dir, name, SVNFileUtil.getBasePath(src), SVNFileUtil.getBasePath(dst), true, true);
        dir.markResolved(name, true, false);
        
        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        boolean needsLock = props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null;
        if (executable) {
            SVNFileUtil.setExecutable(dst, true);
        }
        if (needsLock) {
            SVNFileUtil.setReadonly(dst, entry.getLockToken() == null);
        }
        long tstamp = dst.lastModified();
        if (myWCAccess.getOptions().isUseCommitTimes() && !special) {
            entry.setTextTime(entry.getCommittedDate());
            tstamp = TimeUtil.parseDate(entry.getCommittedDate()).getTime();
            dst.setLastModified(tstamp);
        } else {
            entry.setTextTime(TimeUtil.formatDate(new Date(tstamp)));
        }
        dir.getEntries().save(false);
        
        myWCAccess.handleEvent(SVNEventFactory.createRestoredEvent(myWCAccess, dir, entry));
    }
}
