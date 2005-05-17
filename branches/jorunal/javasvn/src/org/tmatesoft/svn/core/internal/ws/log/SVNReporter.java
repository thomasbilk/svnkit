/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.ws.log;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

class SVNReporter implements ISVNReporterBaton {

    private SVNWCAccess myWCAccess;
    private boolean myIsRecursive;

    public SVNReporter(SVNWCAccess wcAccess, boolean recursive) {
        myWCAccess = wcAccess;
        myIsRecursive = recursive;
    }

    public void report(ISVNReporter reporter) throws SVNException {
        SVNEntries targetEntries = myWCAccess.getTarget().getEntries();
        SVNEntries anchorEntries = myWCAccess.getAnchor().getEntries();
        SVNEntry targetEntry = targetEntries.getEntry(myWCAccess.getTargetName());
        
        try {
            if (targetEntry == null || targetEntry.isHidden() || 
                    (targetEntry.isDirectory() && targetEntry.isScheduledForAddition())) {
                long revision = targetEntries.getEntry("").getRevision();
                reporter.setPath("", null, revision, targetEntry != null ? targetEntry.isIncomplete() : true);
                reporter.deletePath("");
                reporter.finishReport();
                return;
            }
            long revision = targetEntry.getRevision();
            if (revision < 0) {
                revision = targetEntries.getEntry("").getRevision();
                if (revision < 0) {
                    revision = anchorEntries.getEntry("").getRevision();
                }
            }
            reporter.setPath("", null, revision, targetEntry.isIncomplete());
            boolean missing = !targetEntry.isScheduledForDeletion() &&  
                !myWCAccess.getTarget().getFile(myWCAccess.getTargetName()).exists();
            
            if (targetEntry.isDirectory()) {
                if (missing) {
                    reporter.deletePath("");
                } else {
                    reportEntries(reporter, myWCAccess.getTarget(), myWCAccess.getTargetName(), targetEntry.isIncomplete(), myIsRecursive);
                }
            } else if (targetEntry.isFile()){
                if (missing) {
                    restoreFile(myWCAccess.getTarget(), targetEntry.getName());
                }
                // report either linked path or entry path
                String url = targetEntry.getURL();
                SVNEntry parentEntry = targetEntries.getEntry("");
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
            reporter.abortReport();
            SVNErrorManager.error(0, th);
        }
    }
    
    private void reportEntries(ISVNReporter reporter, SVNDirectory directory, String dirPath, boolean reportAll, boolean recursive) throws SVNException {
        SVNEntries entries = directory.getEntries();
        long baseRevision = entries.getEntry("").getRevision();
        
        Map childDirectories = new HashMap();
        
        for (Iterator e = entries.entries(); e.hasNext();) {
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
            File file = directory.getFile(entry.getName());
            boolean missing = !file.exists(); 
            if (entry.isFile()) {
                if (!reportAll) {
                    // check svn:special files -> symlinks that could be directory.
                    boolean special = SVNFileUtil.isWindows && 
                        directory.getProperties(entry.getName()).getPropertyValue(SVNProperty.SPECIAL) != null;
                    
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
                    if (!url.equals(expectedURL)) {
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
                if (missing) {
                    if (!reportAll) {
                        reporter.deletePath(path);
                    }
                }
                if (file.isFile()) {
                    SVNErrorManager.error(3, null);
                }
                
                SVNDirectory childDir = directory.getChildDirectory(entry.getName());
                SVNEntry childEntry = childDir.getEntries().getEntry("");
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
                childDirectories.put(path, childDir);
                childDir.dispose();
            }
        }
        for (Iterator dirs = childDirectories.keySet().iterator(); dirs.hasNext();) {
            String path = (String) dirs.next();
            SVNDirectory dir = (SVNDirectory) childDirectories.get(path);
            SVNEntry childEntry = dir.getEntries().getEntry("");            
            boolean childReportAll = childEntry == null ? true : childEntry.isIncomplete();
            reportEntries(reporter, dir, path, childReportAll, recursive);
            dir.dispose();
        }
    }
    
    private void restoreFile(SVNDirectory dir, String name) throws SVNException {
        SVNProperties props = dir.getProperties(name);
        SVNEntry entry = dir.getEntries().getEntry(name);
        String eolStyle = props.getPropertyValue(SVNProperty.EOL_STYLE);
        String keywords = props.getPropertyValue(SVNProperty.KEYWORDS);

        String url = entry.getURL();
        String author = entry.getAuthor();
        String date = entry.getCommittedDate();
        long rev = entry.getCommittedRevision();
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
        
        Map keywordsMap = SVNTranslator.computeKeywords(keywords, url, author, date, rev);

        File src = dir.getBaseFile(name, false);
        File dst = dir.getBaseFile(name, true);
        File file = dir.getFile(name);
        SVNTranslator.translate(src, dst, SVNTranslator.getEOL(eolStyle), keywordsMap, special);
        try {
            SVNFileUtil.rename(dst, file);
        } catch (IOException e) {
            SVNErrorManager.error(0, e);
        } finally {
            dst.delete();
        }
        
        dir.markResolved(name, true, false);
        
        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        boolean needsLock = entry.isNeedsLock();
        if (executable) {
            SVNFileUtil.setExecutable(file, true);
        }
        if (needsLock) {
            try {
                SVNFileUtil.setReadonly(file, entry.getLockToken() == null);
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            }
        }
        long tstamp = file.lastModified();
        if (myWCAccess.getOptions().isUseCommitTimes() && !special) {
            entry.setTextTime(entry.getCommittedDate());
            tstamp = TimeUtil.parseDate(entry.getCommittedDate()).getTime();
            file.setLastModified(tstamp);
        } else {
            entry.setTextTime(TimeUtil.formatDate(new Date(tstamp)));
        }
        dir.getEntries().save(false);
    }
    
    public static void main(String[] args) {
        
        SVNOptions options = new SVNOptions();
        System.out.println("use-commit-times: " + options.isUseCommitTimes());
        System.out.println("enable-auto-props: " + options.isUseAutoProperties());
        System.out.println("ingored: " + options.isIgnored("white"));
        System.out.println("ingored: " + options.isIgnored("space"));
        System.out.println("ingored: " + options.isIgnored("white space"));
        System.out.println("ingored: " + options.isIgnored("*.java"));
        
        Map props = options.getAutoProperties("test.java", null);
        System.out.println("auto props: " + props);
        props = options.getAutoProperties("java", null);
        System.out.println("auto props: " + props);
        
        ISVNReporter r = new ISVNReporter() {
            public void setPath(String path, String lockToken, long revision, boolean startEmpty) throws SVNException {
                System.out.println("set-path '" + path + "' : " + revision);
            }
            public void deletePath(String path) throws SVNException {
                System.out.println("delete-path '" + path + "'");
            }
            public void linkPath(SVNRepositoryLocation repository, String path, String lockToken, long revison, boolean startEmtpy) throws SVNException {
            }
            public void finishReport() throws SVNException {
                System.out.println("finish-report");
            }
            public void abortReport() throws SVNException {
            }
        };

        try {
            SVNWCAccess wcAccess = SVNWCAccess.create(new File("c:/subversion/subversion/libsvn_wc"));
            SVNReporter reporter = new SVNReporter(wcAccess, true);
            reporter.report(r);
        } catch (SVNException e) {
            e.printStackTrace();
        }
        
    }
}
