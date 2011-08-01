package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.SVNAnnotationGenerator;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNPathUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 19.06.2005
 * Time: 0:42:49
 * To change this template use File | Settings | File Templates.
 */
public class SVNLogClient extends SVNBasicClient {

    public SVNLogClient() {
    }

    public SVNLogClient(ISVNEventHandler eventDispatcher) {
        super(eventDispatcher);
    }

    public SVNLogClient(ISVNOptions options, ISVNEventHandler eventDispatcher) {
        super(options, eventDispatcher);
    }

    public SVNLogClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options, ISVNEventHandler eventDispatcher) {
        super(repositoryFactory, options, eventDispatcher);
    }

    public void doAnnotate(File path, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        if (startRevision == null || !startRevision.isValid()) {
            startRevision = SVNRevision.create(1);
        }
        SVNRepository repos = createRepository(path, null, pegRevision, endRevision, null);
        long endRev = getRevisionNumber(path, null, repos, endRevision);
        long startRev = getRevisionNumber(path, null, repos, startRevision);
        if (endRev < startRev) {
            SVNErrorManager.error("svn: Start revision must precede end revision (" + startRev + ":" + endRev + ")");
        }
        File tmpFile = new File(path.getParentFile(), ".svn/tmp/text-base");
        if (!tmpFile.exists()) {
            tmpFile = new File(System.getProperty("user.home"), ".javasvn");
            tmpFile.mkdirs();
        }
        doAnnotate(path.getAbsolutePath(), startRev, tmpFile, repos, endRev, handler);
    }

    public void doAnnotate(String url, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        if (startRevision == null || !startRevision.isValid()) {
            startRevision = SVNRevision.create(1);
        }
        SVNRepository repos = createRepository(null, url, pegRevision, endRevision, null);
        long endRev = getRevisionNumber(null, url, repos, endRevision);
        long startRev = getRevisionNumber(null, url, repos, startRevision);
        if (endRev < startRev) {
            SVNErrorManager.error("svn: Start revision must precede end revision (" + startRev + ":" + endRev + ")");
        }
        File tmpFile = new File(System.getProperty("user.home"), ".javasvn");
        if (!tmpFile.exists()) {
            tmpFile.mkdirs();
        }
        doAnnotate(url, startRev, tmpFile, repos, endRev, handler);
    }

    private void doAnnotate(String path, long startRev, File tmpFile, SVNRepository repos, long endRev, ISVNAnnotateHandler handler) throws SVNException {
        SVNAnnotationGenerator generator = new SVNAnnotationGenerator(path, startRev, tmpFile);
        try {
            repos.getFileRevisions("", startRev, endRev, generator);
            generator.reportAnnotations(handler, null);
        } finally {
            generator.dispose();
            SVNFileUtil.deleteAll(tmpFile, false);
        }
    }

    public void doLog(File[] paths, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean reportPaths, long limit,
                      ISVNLogEntryHandler handler) throws SVNException {
        if (paths == null || paths.length == 0) {
            return;
        }
        if (startRevision.isValid() && !endRevision.isValid()) {
            endRevision = startRevision;
        } else if (!startRevision.isValid()) {
            startRevision = SVNRevision.BASE;
            if (!endRevision.isValid()) {
                endRevision = SVNRevision.create(0);
            }
        }
        String[] urls = new String[paths.length];
        for (int i = 0; i < paths.length; i++) {
            File path = paths[i];
            SVNEntry entry = getEntry(path);
            if (entry == null) {
                SVNErrorManager.error("svn: '" + path + "' is not under version control");
                return;
            }
            if (entry.getURL() == null) {
                SVNErrorManager.error("svn: '" + path + "' has not URL");
            }
            urls[i] = entry.getURL();
        }
        if (urls.length == 0) {
            return;
        }
        Collection targets = new TreeSet();
        String baseURL = SVNPathUtil.condenceURLs(urls, targets, true);
        if (baseURL == null || "".equals(baseURL)) {
            SVNErrorManager.error("svn: Entries belongs to different repositories");
        }
        if (targets.isEmpty()) {
            targets.add("");
        }
        SVNRepository repos = createRepository(baseURL);
        String[] targetPaths = (String[]) targets.toArray(new String[targets.size()]);
        DebugLog.log("base URL is: " + baseURL);
        for (int i = 0; i < targetPaths.length; i++) {
            String targetPath = targetPaths[i];
            DebugLog.log("target path: " + targetPath);
        }
        if (startRevision.isLocal() || endRevision.isLocal()) {
            for(int i = 0; i < paths.length; i++) {
                long startRev = getRevisionNumber(paths[i], baseURL, repos, startRevision);
                long endRev = getRevisionNumber(paths[i], baseURL, repos, endRevision);
                DebugLog.log("calling log for " + startRev + ":" + endRev);
                repos.log(targetPaths, startRev, endRev, reportPaths, stopOnCopy, limit, handler);
            }
        } else {
            long startRev = getRevisionNumber(null, baseURL, repos, startRevision);
            long endRev = getRevisionNumber(null, baseURL, repos, endRevision);
            DebugLog.log("calling log for " + startRev + ":" + endRev);
            repos.log(targetPaths, startRev, endRev, reportPaths, stopOnCopy, limit, handler);
        }
    }

    public void doLog(String url, String[] paths, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean reportPaths, long limit,
                      ISVNLogEntryHandler handler) throws SVNException {
        if (startRevision.isValid() && !endRevision.isValid()) {
            endRevision = startRevision;
        } else if (!startRevision.isValid()) {
            startRevision = SVNRevision.HEAD;
            if (!endRevision.isValid()) {
                endRevision = SVNRevision.create(0);
            }
        }
        paths = paths == null || paths.length == 0 ? new String[] {""} : paths;
        url = validateURL(url);
        SVNRepository repos = createRepository(url);
        long startRev = getRevisionNumber(null, url, repos, startRevision);
        long endRev = getRevisionNumber(null, url, repos, endRevision);
        repos.log(paths, startRev,  endRev, reportPaths, stopOnCopy, limit, handler);
    }

    public void doList(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.BASE;
        }
        SVNRepository repos = createRepository(path, null, pegRevision, revision, null);
        long rev = getRevisionNumber(path, null, repos, revision);
        doList(repos, rev, handler, recursive);
    }

    public void doList(String url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        SVNRepository repos = createRepository(null, url, pegRevision, revision, null);
        long rev = getRevisionNumber(null, url, repos, revision);
        doList(repos, rev, handler, recursive);
    }

    private void doList(SVNRepository repos, long rev, ISVNDirEntryHandler handler, boolean recursive) throws SVNException {
        if (repos.checkPath("", rev) == SVNNodeKind.FILE) {
            Map props = new HashMap();
            long size = repos.getFile("", rev, props, null);
            String name = PathUtil.tail(repos.getLocation().getPath());
            SVNDirEntry entry = new SVNDirEntry(name, SVNNodeKind.FILE, size, false, 0, new Date(0), "");
            entry.setPath(name);
            handler.handleDirEntry(entry);
        } else {
            list(repos, "", rev, recursive, handler);
        }
    }

    private static void list(SVNRepository repository, String path, long rev, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        Collection entries = new TreeSet();
        entries = repository.getDir(path, rev, null, entries);

        for (Iterator iterator = entries.iterator(); iterator.hasNext();) {
            SVNDirEntry entry = (SVNDirEntry) iterator.next();
            String childPath = "".equals(path) ? entry.getName() : PathUtil.append(path, entry.getName());
            entry.setPath(childPath);
            handler.handleDirEntry(entry);
            if (entry.getKind() == SVNNodeKind.DIR && recursive) {
                list(repository, childPath, rev, recursive, handler);
            }
        }
    }
}