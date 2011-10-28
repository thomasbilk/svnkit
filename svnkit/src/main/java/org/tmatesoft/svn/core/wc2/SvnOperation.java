package org.tmatesoft.svn.core.wc2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnOperation<V> {
    
    private SVNDepth depth;
    private Collection<SvnTarget> targets;
    private SVNRevision revision;
    private Collection<String> changelists;
    private SvnOperationFactory operationFactory;
    private boolean isSleepForTimestamp;
    
    private volatile boolean isCancelled;
    
    protected SvnOperation(SvnOperationFactory factory) {
        this.operationFactory = factory;
        initDefaults();
    }

    public ISVNEventHandler getEventHandler() {
        return getOperationFactory().getEventHandler();
    }

    public ISVNOptions getOptions() {
        return getOperationFactory().getOptions();
    }
    
    protected void initDefaults() {
        setDepth(SVNDepth.UNKNOWN);
        setSleepForTimestamp(true);
        setRevision(SVNRevision.UNDEFINED);
        this.targets = new ArrayList<SvnTarget>();
    }

    public void setDepth(SVNDepth depth) {
        this.depth = depth;
    }

    public void setSingleTarget(SvnTarget target) {
        this.targets = new ArrayList<SvnTarget>();
        if (target != null) {
            this.targets.add(target);
        }
    }
    
    public void addTarget(SvnTarget target) {
        this.targets.add(target);
    }
    
    public Collection<SvnTarget> getTargets() {
        return Collections.unmodifiableCollection(targets);
    }
    
    public SvnTarget getFirstTarget() {
        return targets != null && !targets.isEmpty() ? targets.iterator().next() : null;
    }
    
    public SVNDepth getDepth() {
        return depth;
    }
    
    public void setRevision(SVNRevision revision) {
        this.revision = revision;
    }

    public SVNRevision getRevision() {
        return revision;
    }
    
    public void setApplicalbeChangelists(Collection<String> changelists) {
        this.changelists = changelists;
    }
    
    public Collection<String> getApplicableChangelists() {
        if (this.changelists == null || this.changelists.isEmpty()) {
            return null;
        }
        return Collections.unmodifiableCollection(this.changelists);
    }
    
    public SvnOperationFactory getOperationFactory() {
        return this.operationFactory;
    }
    
    public boolean hasLocalTargets() {
        for (SvnTarget target : getTargets()) {
            if (target.isLocal()) {
                return true;
            }
        }
        return false;
    }
    
    public boolean hasRemoteTargets() {
        for (SvnTarget target : getTargets()) {
            if (!target.isLocal()) {
                return true;
            }
        }
        return false;
    }
    
    protected void ensureEnoughTargets() throws SVNException {
        int targetsCount = getTargets().size();
        
        if (targetsCount < getMinimumTargetsCount()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, 
                    "Wrong number of targets has been specified ({0}), at least {1} is required.", 
                    new Object[] {new Integer(targetsCount), new Integer(getMinimumTargetsCount())}, 
                    SVNErrorMessage.TYPE_ERROR);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (targetsCount > getMaximumTargetsCount()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, 
                    "Wrong number of targets has been specified ({0}), no more that {1} may be specified.", 
                    new Object[] {new Integer(targetsCount), 
                    new Integer(getMaximumTargetsCount())}, 
                    SVNErrorMessage.TYPE_ERROR);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }
    
    protected int getMinimumTargetsCount() {
        return 1;
    }

    protected int getMaximumTargetsCount() {
        return 1;
    }
    
    public void cancel() {
        isCancelled = true;
    }
    
    public boolean isCancelled() {
        return isCancelled;
    }
    
    @SuppressWarnings("unchecked")
    public V run() throws SVNException {
        ensureArgumentsAreValid();
        return (V) getOperationFactory().run(this);
    }
    
    protected void ensureArgumentsAreValid() throws SVNException {
        ensureEnoughTargets();
        ensureHomohenousTargets();
    }
    
    protected boolean needsHomohenousTargets() {
        return true;
    }
    
    protected void ensureHomohenousTargets() throws SVNException {
        if (getTargets().size() <= 1) {
            return;
        }
        if (!needsHomohenousTargets()) {
            return;
        }
        if (hasLocalTargets() && hasRemoteTargets()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.ILLEGAL_TARGET, "Cannot mix repository and working copy targets"), SVNLogType.WC);
        }
    }

    public ISVNRepositoryPool getRepositoryPool() {
        return getOperationFactory().getRepositoryPool();
    }

    public ISVNAuthenticationManager getAuthenticationManager() {
        return getOperationFactory().getAuthenticationManager();
    }

    public ISVNCanceller getCanceller() {
        return getOperationFactory().getCanceller();
    }

    public boolean isSleepForTimestamp() {
        return isSleepForTimestamp;
    }

    public void setSleepForTimestamp(boolean isSleepForTimestamp) {
        this.isSleepForTimestamp = isSleepForTimestamp;
    }

    public boolean hasFileTargets() {
        for (SvnTarget target : getTargets()) {
            if (target.isFile()) {
                return true;
            }
        }
        return false;
    }
}
