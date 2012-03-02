package org.tmatesoft.svn.core.wc2;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc2.ISvnCommitRunner;

public class SvnCommitPacket {
    
    private Map<SVNURL, Collection<SvnCommitItem>> items;
    private Map<String, SvnCommitItem> itemsByPath;
    private Object lockingContext;
    private ISvnCommitRunner runner;
    private Map<SVNURL, String> lockTokens;
    
    public SvnCommitPacket() {
        items = new HashMap<SVNURL, Collection<SvnCommitItem>>();
        itemsByPath = new HashMap<String, SvnCommitItem>();
        lockTokens = new HashMap<SVNURL, String>();
    }
    
    public boolean hasItem(File path) {
        return itemsByPath.containsKey(path.getAbsolutePath());
    }

    public SvnCommitItem getItem(File path) {
        return itemsByPath.get(path);
    }
    
    public Collection<SVNURL> getRepositoryRoots() {
        return Collections.unmodifiableCollection(items.keySet());
    }

    public Collection<SvnCommitItem> getItems(SVNURL url) {
        return Collections.unmodifiableCollection(items.get(url));
    }

    public void addItem(SvnCommitItem item, SVNURL repositoryRoot) {
        if (!items.containsKey(repositoryRoot)) {
            items.put(repositoryRoot, new HashSet<SvnCommitItem>());
        }

        items.get(repositoryRoot).add(item);
        itemsByPath.put(item.getPath().getAbsolutePath(), item);
    }
    
    public SvnCommitItem addItem(File path, SVNNodeKind kind, SVNURL repositoryRoot, String repositoryPath, long revision,
            String copyFromPath, long copyFromRevision, int flags) throws SVNException {
        SvnCommitItem item = new SvnCommitItem();
        item.setPath(path);
        item.setKind(kind);
        item.setUrl(repositoryRoot.appendPath(repositoryPath, false));
        item.setRevision(revision);
        if (copyFromPath != null) {
            item.setCopyFromUrl(repositoryRoot.appendPath(copyFromPath, false));
            item.setCopyFromRevision(copyFromRevision);
        } else {
            item.setCopyFromRevision(-1);
        }
        item.setFlags(flags);

        addItem(item, repositoryRoot);
        
        return item;
    }
    
    public SvnCommitItem addItem(File path, SVNURL rootUrl, SVNNodeKind kind, SVNURL url, long revision,
            SVNURL copyFromUrl, long copyFromRevision, int flags) throws SVNException {
        SvnCommitItem item = new SvnCommitItem();
        item.setPath(path);
        item.setKind(kind);
        item.setUrl(url);
        item.setRevision(revision);
        if (copyFromUrl!= null) {
            item.setCopyFromUrl(copyFromUrl);
            item.setCopyFromRevision(copyFromRevision);
        } else {
            item.setCopyFromRevision(-1);
        }
        item.setFlags(flags);
        
        if (!items.containsKey(rootUrl)) {
            items.put(rootUrl, new HashSet<SvnCommitItem>());
        }
        
        items.get(rootUrl).add(item);
        itemsByPath.put(path.getAbsolutePath(), item);
        return item;
    }

    public void setLockingContext(ISvnCommitRunner commitRunner, Object context) {
        lockingContext = context;        
        runner = commitRunner;
    }
    
    public void dispose() throws SVNException {
        try {
            if (runner != null) {
                runner.disposeCommitPacket(lockingContext);
            }
        } finally {
            runner = null;
            lockingContext = null;
        }
    }

    public void setLockTokens(Map<SVNURL, String> lockTokens) {
        this.lockTokens = lockTokens;
    }
    
    public Map<SVNURL, String> getLockTokens() {
        return lockTokens;
    }
    
    public boolean isEmpty() {
        for (SVNURL rootUrl : getRepositoryRoots()) {
            if (!isEmpty(rootUrl)) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty(SVNURL repositoryRootUrl) {
        for (SvnCommitItem item : getItems(repositoryRootUrl)) {
            if (item.getFlags() != SvnCommitItem.LOCK) {
                return false;
            }
        }
        return true;
    }

    public Object getLockingContext() {
        return lockingContext;
    }

    public ISvnCommitRunner getRunner() {
        return runner;
    }
}
