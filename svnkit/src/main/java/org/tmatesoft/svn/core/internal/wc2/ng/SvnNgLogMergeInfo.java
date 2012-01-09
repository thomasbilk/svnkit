package org.tmatesoft.svn.core.internal.wc2.ng;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNMergeRangeList;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.SVNWCUtils;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.ISvnObjectReceiver;
import org.tmatesoft.svn.core.wc2.SvnLog;
import org.tmatesoft.svn.core.wc2.SvnLogMergeInfo;
import org.tmatesoft.svn.core.wc2.SvnRevisionRange;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class SvnNgLogMergeInfo extends SvnNgOperationRunner<SVNLogEntry, SvnLogMergeInfo> {

    @Override
    protected SVNLogEntry run(SVNWCContext context) throws SVNException {
        SVNURL[] root = new SVNURL[1];
        
        Map<String, Map<String, SVNMergeRangeList>> mergeInfoCatalog = 
                SvnNgMergeinfoUtil.getMergeInfo(getWcContext(), getRepositoryAccess(), getOperation().getFirstTarget(), getOperation().getDepth() == SVNDepth.INFINITY, true, root);
        
        SvnTarget target = getOperation().getFirstTarget();
        File reposRelPath = null;
        if (target.isURL()) {
            reposRelPath = SVNFileUtil.createFilePath(SVNPathUtil.getRelativePath(root[0].getPath(), target.getURL().getPath()));
        } else {
            reposRelPath = getWcContext().getNodeReposRelPath(getOperation().getFirstTarget().getFile());
        }
        
        if (mergeInfoCatalog == null) {
            if (getOperation().isFindMerged()) {
                return getOperation().first();
            }
            mergeInfoCatalog = new TreeMap<String, Map<String,SVNMergeRangeList>>();
            mergeInfoCatalog.put(SVNFileUtil.getFilePath(reposRelPath), new TreeMap<String, SVNMergeRangeList>());
        }
        Map<String, SVNMergeRangeList> history = null;
        Map<String, SVNMergeRangeList> sourceHistory = null;
        if (!getOperation().isFindMerged()) {
            history = getRepositoryAccess().getHistoryAsMergeInfo(null, target, -1, -1);
        }
        sourceHistory = getRepositoryAccess().getHistoryAsMergeInfo(null, getOperation().getSource(), -1, -1);
        String reposRelPathStr = SVNFileUtil.getFilePath(reposRelPath);
        SVNMergeRangeList masterNonInheritableRangeList = new SVNMergeRangeList((SVNMergeRange[]) null);
        SVNMergeRangeList masterInheritableRangeList = new SVNMergeRangeList((SVNMergeRange[]) null);
        Map<String, SVNMergeRangeList> inheritableSubtreeMerges = new TreeMap<String, SVNMergeRangeList>();
        
        for (String subtreePath : mergeInfoCatalog.keySet()) {
            Map<String, SVNMergeRangeList> subtreeMergeInfo = mergeInfoCatalog.get(subtreePath);
            
            Map<String, SVNMergeRangeList> subtreeHistory = null;
            Map<String, SVNMergeRangeList> subtreeSourceHistory;
            Map<String, SVNMergeRangeList> subtreeInheritableMergeInfo;
            Map<String, SVNMergeRangeList> subtreeNonInheritableMergeInfo;
            Map<String, SVNMergeRangeList> mergedNonInheritableMergeInfo;
            Map<String, SVNMergeRangeList> mergedMergeInfo;

            boolean isSubtree = !subtreePath.equals(reposRelPathStr);
            
            if (isSubtree) {
                String subtreeRelPath = reposRelPathStr.substring(subtreePath.length() + 1);
                subtreeSourceHistory = SVNMergeInfoUtil.appendSuffix(sourceHistory, subtreeRelPath);
                if (!getOperation().isFindMerged()) {
                    subtreeHistory = SVNMergeInfoUtil.appendSuffix(history, subtreeRelPath);
                }
            } else {
                subtreeSourceHistory = sourceHistory;
                if (!getOperation().isFindMerged()) {
                    subtreeHistory = history;
                }
            }
            if (!getOperation().isFindMerged()) {
                Map<String, SVNMergeRangeList> mergedViaHistory = SVNMergeInfoUtil.intersectMergeInfo(subtreeHistory, subtreeSourceHistory, true);
                subtreeMergeInfo = SVNMergeInfoUtil.mergeMergeInfos(subtreeMergeInfo, mergedViaHistory);
            }
            subtreeInheritableMergeInfo = SVNMergeInfoUtil.getInheritableMergeInfo(subtreeMergeInfo, null, -1, -1, true);
            subtreeNonInheritableMergeInfo = SVNMergeInfoUtil.getInheritableMergeInfo(subtreeMergeInfo, null, -1, -1, false);
            mergedNonInheritableMergeInfo = SVNMergeInfoUtil.intersectMergeInfo(subtreeNonInheritableMergeInfo, subtreeSourceHistory, false);
            
            if (!mergedNonInheritableMergeInfo.isEmpty()) {
                for (SVNMergeRangeList rl : mergedNonInheritableMergeInfo.values()) {
                    rl.setInheritable(false);
                    masterNonInheritableRangeList = masterNonInheritableRangeList.merge(rl);
                }
            }
            mergedMergeInfo = SVNMergeInfoUtil.intersectMergeInfo(subtreeInheritableMergeInfo, subtreeSourceHistory, false);

            SVNMergeRangeList subtreeMergeRangeList = new SVNMergeRangeList((SVNMergeRange[]) null);
            if (!mergedMergeInfo.isEmpty()) {
                for (SVNMergeRangeList rl : mergedMergeInfo.values()) {
                    rl.setInheritable(false);
                    masterInheritableRangeList = masterInheritableRangeList.merge(rl);
                    subtreeMergeRangeList = subtreeMergeRangeList.merge(rl);
                }
            }
            inheritableSubtreeMerges.put(subtreePath, subtreeMergeRangeList);
        }
        
        if (!masterInheritableRangeList.isEmpty()) {
            for (String path : inheritableSubtreeMerges.keySet()) {
                SVNMergeRangeList subtreeMergedRangeList = inheritableSubtreeMerges.get(path);
                SVNMergeRangeList deletedRanges = subtreeMergedRangeList.remove(masterInheritableRangeList, true);
                if (!deletedRanges.isEmpty()) {
                    deletedRanges.setInheritable(false);
                    masterNonInheritableRangeList = masterNonInheritableRangeList.merge(deletedRanges);
                    masterInheritableRangeList = masterInheritableRangeList.remove(deletedRanges, false);
                }
            }
        }
        if (getOperation().isFindMerged()) {
            masterInheritableRangeList = masterInheritableRangeList.merge(masterNonInheritableRangeList);
        } else {
            SVNMergeRangeList sourceMasterRangeList = new SVNMergeRangeList((SVNMergeRange[]) null);
            for(SVNMergeRangeList rl : sourceHistory.values()) {
                sourceMasterRangeList = sourceMasterRangeList.merge(rl);
            }
            sourceMasterRangeList = sourceMasterRangeList.remove(masterNonInheritableRangeList, false);
            sourceMasterRangeList = sourceMasterRangeList.merge(masterNonInheritableRangeList);
            masterInheritableRangeList = sourceMasterRangeList.remove(masterInheritableRangeList, true);
        }
        
        if (masterInheritableRangeList.isEmpty()) {
            return getOperation().first();
        }
        
        List<String> mergeSourcePaths = new ArrayList<String>();
        String logTarget = null;
        SVNMergeRange youngestRange = masterInheritableRangeList.getRanges()[masterInheritableRangeList.getSize() - 1];
        SVNMergeRangeList youngestRangeList = new SVNMergeRangeList(youngestRange.getEndRevision() - 1, youngestRange.getEndRevision(), youngestRange.isInheritable());
        for (String key : sourceHistory.keySet()) {
            SVNMergeRangeList subtreeMergedList = sourceHistory.get(key);
            SVNMergeRangeList intersection = youngestRangeList.intersect(subtreeMergedList, false);
            mergeSourcePaths.add(key);
            if (!intersection.isEmpty()) {
                logTarget = key;
            }
        }
        if (logTarget != null && logTarget.startsWith("/")) {
            logTarget = logTarget.substring(1);
        }
        
        SVNURL logTargetURL = logTarget != null ? SVNWCUtils.join(root[0], SVNFileUtil.createFilePath(logTarget)) : root[0];
        
        logForMergeInfoRangeList(logTargetURL, 
                mergeSourcePaths, 
                getOperation().isFindMerged(), 
                masterInheritableRangeList, 
                mergeInfoCatalog, 
                "/" + reposRelPathStr, 
                getOperation().isDiscoverChangedPaths(), 
                getOperation().getRevisionProperties(), 
                getOperation());
        
        return getOperation().first();
    }
    
    private void logForMergeInfoRangeList(SVNURL sourceURL, List<String> mergeSourcePaths, boolean filteringMerged, 
            SVNMergeRangeList rangelist, Map<String, Map<String, SVNMergeRangeList>> targetCatalog, String absReposTargetPath, boolean discoverChangedPaths,
            String[] revprops, ISvnObjectReceiver<SVNLogEntry> receiver) throws SVNException {
        if (rangelist.isEmpty()) {
            return;
        }
        if (targetCatalog == null) {
            targetCatalog = new TreeMap<String, Map<String,SVNMergeRangeList>>();
        }
        List<SVNMergeRange> ranges = rangelist.getRangesAsList();
        Collections.sort(ranges);
        SVNMergeRange youngestRange = ranges.get(ranges.size() - 1);
        SVNMergeRange oldestRange = ranges.get(0);
        long youngestRev = youngestRange.getEndRevision();
        long oldestRev = oldestRange.getStartRevision();
        
        LogEntryReceiver filteringReceiver = new LogEntryReceiver();
        filteringReceiver.receiver = receiver;
        filteringReceiver.rangelist = rangelist;
        filteringReceiver.isFilteringMerged = filteringMerged;
        filteringReceiver.targetCatalog = targetCatalog;
        filteringReceiver.mergeSourcePaths = mergeSourcePaths;
        filteringReceiver.reposTargertAbsPath = absReposTargetPath;
        
        SvnLog log = getOperation().getOperationFactory().createLog();
        
        log.setSingleTarget(SvnTarget.fromURL(sourceURL, SVNRevision.create(youngestRev)));
        log.setDiscoverChangedPaths(getOperation().isDiscoverChangedPaths());
        log.setRevisionProperties(getOperation().getRevisionProperties());
        log.setLimit(-1);
        log.setStopOnCopy(false);
        log.setUseMergeHistory(false);
        log.addRange(SvnRevisionRange.create(SVNRevision.create(oldestRev), SVNRevision.create(youngestRev)));
        log.setReceiver(filteringReceiver);
        
        log.run();
    }

    private static class LogEntryReceiver implements ISvnObjectReceiver<SVNLogEntry> {

        private boolean isFilteringMerged;
        private List<String> mergeSourcePaths;
        private String reposTargertAbsPath;
        private Map<String, Map<String, SVNMergeRangeList>> targetCatalog;
        private SVNMergeRangeList rangelist;
        private ISvnObjectReceiver<SVNLogEntry> receiver;
        
        public LogEntryReceiver() {
        }

        public void receive(SvnTarget target, SVNLogEntry logEntry) throws SVNException {
            if (logEntry.getRevision() == 0) {
                return;
            }
            SVNMergeRangeList thisRangeList = new SVNMergeRangeList(logEntry.getRevision() - 1, logEntry.getRevision(), true);
            SVNMergeRangeList intersection = this.rangelist.intersect(thisRangeList, false);
            
            if (intersection == null || intersection.isEmpty()) {
                return;
            }
            intersection = this.rangelist.intersect(thisRangeList, true);
            logEntry.setNonInheriable(!intersection.isEmpty());
            
            if ((logEntry.isNonInheritable() || !isFilteringMerged) && logEntry.getChangedPaths() != null) {
                boolean allSubtreesHaveThisRev = true;
                SVNMergeRangeList thisRevRangeList = new SVNMergeRangeList(logEntry.getRevision() - 1, logEntry.getRevision(), true);
                for (String changedPath : logEntry.getChangedPaths().keySet()) {
                    File mergeSourceRelTarget = null;
                    boolean inteerupted = false;
                    for (String mergeSourcePath : this.mergeSourcePaths) {
                        mergeSourceRelTarget = SVNWCUtils.skipAncestor(SVNFileUtil.createFilePath(mergeSourcePath), SVNFileUtil.createFilePath(changedPath));
                        if (mergeSourceRelTarget != null) {
                            if ("".equals(mergeSourceRelTarget.getPath()) && logEntry.getChangedPaths().get(changedPath).getType() != 'M') {
                                inteerupted = true;
                                break;
                            }
                        }
                    }
                    if (!inteerupted) {
                        continue;
                    }
                    
                }
            }
            
            receiver.receive(target, logEntry);
            
        }
    }

}
