/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import static org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb.isAbsolute;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetDb;
import org.tmatesoft.svn.core.internal.db.SVNSqlJetStatement;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNSkel;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNChecksum;
import org.tmatesoft.svn.core.internal.wc.SVNChecksumKind;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNChecksumInputStream;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.core.internal.wc17.SVNStatus17.ConflictedInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbOpenMode;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbAdditionInfo.AdditionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbBaseInfo.BaseInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDeletionInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDeletionInfo.DeletionInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbDirDeletedInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbInfo.InfoField;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb.WCDbRepositoryInfo.RepositoryInfoField;
import org.tmatesoft.svn.core.internal.wc17.db.statement.SVNWCDbStatements;
import org.tmatesoft.svn.core.internal.wc17.db.SVNWCDb;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNMergeFileSet;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.4
 * @author TMate Software Ltd.
 */
public class SVNWCContext {

    public static final long INVALID_REVNUM = -1;
    private static final int STREAM_CHUNK_SIZE = 16384;

    public interface CleanupHandler {

        void cleanup() throws SVNException;
    }

    public static boolean isAdminDirectory(String name) {
        return name != null && (SVNFileUtil.isWindows) ? SVNFileUtil.getAdminDirectoryName().equalsIgnoreCase(name) : SVNFileUtil.getAdminDirectoryName().equals(name);
    }

    public static class SVNEolStyleInfo {

        public static final byte[] NATIVE_EOL_STR = System.getProperty("line.separator").getBytes();
        public static final byte[] LF_EOL_STR = {
            '\n'
        };
        public static final byte[] CR_EOL_STR = {
            '\r'
        };
        public static final byte[] CRLF_EOL_STR = {
                '\r', '\n'
        };

        public SVNEolStyle eolStyle;
        public byte[] eolStr;

        public SVNEolStyleInfo(SVNEolStyle style, byte[] str) {
            this.eolStyle = style;
            this.eolStr = str;
        }

        public static SVNEolStyleInfo fromValue(String value) {
            if (value == null) {
                /* property doesn't exist. */
                return new SVNEolStyleInfo(SVNEolStyle.None, null);
            } else if ("native".equals(value)) {
                return new SVNEolStyleInfo(SVNEolStyle.Native, NATIVE_EOL_STR);
            } else if ("LF".equals(value)) {
                return new SVNEolStyleInfo(SVNEolStyle.Fixed, LF_EOL_STR);
            } else if ("CR".equals(value)) {
                return new SVNEolStyleInfo(SVNEolStyle.Fixed, CR_EOL_STR);
            } else if ("CRLF".equals(value)) {
                return new SVNEolStyleInfo(SVNEolStyle.Fixed, CRLF_EOL_STR);
            } else {
                return new SVNEolStyleInfo(SVNEolStyle.Unknown, null);
            }
        }

    }

    public enum SVNEolStyle {
        /** An unrecognized style */
        Unknown,
        /** EOL translation is "off" or ignored value */
        None,
        /** Translation is set to client's native eol */
        Native,
        /** Translation is set to one of LF, CR, CRLF */
        Fixed
    }

    private ISVNWCDb db;
    private boolean closeDb;
    private ISVNEventHandler eventHandler;
    private List<CleanupHandler> cleanupHandlers = new LinkedList<CleanupHandler>();

    public SVNWCContext(ISVNOptions config, ISVNEventHandler eventHandler) throws SVNException {
        this(SVNWCDbOpenMode.ReadWrite, config, false, true, eventHandler);
    }

    public SVNWCContext(SVNWCDbOpenMode mode, ISVNOptions config, boolean autoUpgrade, boolean enforceEmptyWQ, ISVNEventHandler eventHandler) throws SVNException {
        this.db = new SVNWCDb();
        this.db.open(mode, config, autoUpgrade, enforceEmptyWQ);
        this.closeDb = true;
        this.eventHandler = eventHandler;
    }

    public SVNWCContext(ISVNWCDb db, ISVNEventHandler eventHandler) {
        this.db = db;
        this.closeDb = false;
        this.eventHandler = eventHandler;
    }

    public ISVNEventHandler getEventHandler() {
        return eventHandler;
    }

    public void close() throws SVNException {
        try {
            cleanup();
        } finally {
            if (closeDb) {
                db.close();
            }
        }
    }

    public void registerCleanupHandler(CleanupHandler ch) {
        cleanupHandlers.add(ch);
    }

    private void cleanup() throws SVNException {
        for (CleanupHandler ch : cleanupHandlers) {
            ch.cleanup();
        }
        cleanupHandlers.clear();
    }

    public ISVNWCDb getDb() {
        return db;
    }

    public void checkCancelled() throws SVNCancelException {
        if (eventHandler != null) {
            eventHandler.checkCancelled();
        }
    }

    private ISVNOptions getOptions() {
        return db.getConfig();
    }

    public SVNNodeKind readKind(File localAbsPath, boolean showHidden) throws SVNException {
        try {
            final WCDbInfo info = db.readInfo(localAbsPath, InfoField.status, InfoField.kind);
            /* Make sure hidden nodes return svn_node_none. */
            if (!showHidden) {
                switch (info.status) {
                    case NotPresent:
                    case Absent:
                    case Excluded:
                        return SVNNodeKind.NONE;

                    default:
                        break;
                }
            }
            return info.kind.toNodeKind();
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                return SVNNodeKind.NONE;
            }
            throw e;
        }
    }

    public boolean isNodeAdded(File path) throws SVNException {
        final WCDbInfo info = db.readInfo(path, InfoField.status);
        final SVNWCDbStatus status = info.status;
        return status == SVNWCDbStatus.Added || status == SVNWCDbStatus.ObstructedAdd;
    }

    /** Equivalent to the old notion of "entry->schedule == schedule_replace" */
    public boolean isNodeReplaced(File path) throws SVNException {
        final WCDbInfo info = db.readInfo(path, InfoField.status, InfoField.haveBase);
        final SVNWCDbStatus status = info.status;
        final boolean haveBase = info.haveBase;
        SVNWCDbStatus baseStatus = null;
        if (haveBase) {
            final WCDbBaseInfo baseInfo = db.getBaseInfo(path, BaseInfoField.status);
            baseStatus = baseInfo.status;
        }
        return ((status == SVNWCDbStatus.Added || status == SVNWCDbStatus.ObstructedAdd) && haveBase && baseStatus != SVNWCDbStatus.NotPresent);
    }

    public long getRevisionNumber(SVNRevision revision, long[] latestRevisionNumber, SVNRepository repository, File path) throws SVNException {
        if (repository == null && (revision == SVNRevision.HEAD || revision.getDate() != null)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_RA_ACCESS_REQUIRED);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (revision.getNumber() >= 0) {
            return revision.getNumber();
        } else if (revision.getDate() != null) {
            return repository.getDatedRevision(revision.getDate());
        } else if (revision == SVNRevision.HEAD) {
            if (latestRevisionNumber != null && latestRevisionNumber.length > 0 && SVNRevision.isValidRevisionNumber(latestRevisionNumber[0])) {
                return latestRevisionNumber[0];
            }
            long latestRevision = repository.getLatestRevision();
            if (latestRevisionNumber != null && latestRevisionNumber.length > 0) {
                latestRevisionNumber[0] = latestRevision;
            }
            return latestRevision;
        } else if (!revision.isValid()) {
            return -1;
        } else if (revision == SVNRevision.WORKING || revision == SVNRevision.BASE) {
            if (path == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_VERSIONED_PATH_REQUIRED);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            long revnum = -1;
            try {
                revnum = getNodeCommitBaseRev(path);
            } catch (SVNException e) {
                /*
                 * Return the same error as older code did (before and at
                 * r935091). At least svn_client_proplist3 promises
                 * SVN_ERR_ENTRY_NOT_FOUND.
                 */
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND);
                    SVNErrorManager.error(err, SVNLogType.WC);
                } else {
                    throw e;
                }
            }
            if (!SVNRevision.isValidRevisionNumber(revnum)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Path ''{0}'' has no committed revision", path);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            return revnum;
        } else if (revision == SVNRevision.COMMITTED || revision == SVNRevision.PREVIOUS) {
            if (path == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_VERSIONED_PATH_REQUIRED);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            final WCDbInfo info = getNodeChangedInfo(path);
            if (!SVNRevision.isValidRevisionNumber(info.changedRev)) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Path ''{0}'' has no committed revision", path);
                SVNErrorManager.error(err, SVNLogType.WC);

            }
            return revision == SVNRevision.PREVIOUS ? info.changedRev - 1 : info.changedRev;
        } else {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION, "Unrecognized revision type requested for ''{0}''", path != null ? path : (Object) repository.getLocation());
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        return -1;
    }

    private WCDbInfo getNodeChangedInfo(File path) throws SVNException {
        return db.readInfo(path, InfoField.changedRev, InfoField.changedDate, InfoField.changedAuthor);
    }

    private long getNodeCommitBaseRev(File local_abspath) throws SVNException {
        final WCDbInfo info = db.readInfo(local_abspath, InfoField.status, InfoField.revision, InfoField.haveBase);
        /*
         * If this returned a valid revnum, there is no WORKING node. The node
         * is cleanly checked out, no modifications, copies or replaces.
         */
        long commitBaseRevision = info.revision;
        if (SVNRevision.isValidRevisionNumber(commitBaseRevision)) {
            return commitBaseRevision;
        }
        final SVNWCDbStatus status = info.status;
        final boolean haveBase = info.haveBase;
        if (status == SVNWCDbStatus.Added) {
            /*
             * If the node was copied/moved-here, return the copy/move source
             * revision (not this node's base revision). If it's just added,
             * return INVALID_REVNUM.
             */
            final WCDbAdditionInfo addInfo = db.scanAddition(local_abspath, AdditionInfoField.originalRevision);
            commitBaseRevision = addInfo.originalRevision;
            if (!SVNRevision.isValidRevisionNumber(commitBaseRevision) && haveBase) {
                /*
                 * It is a replace that does not feature a copy/move-here.
                 * Return the revert-base revision.
                 */
                commitBaseRevision = getNodeBaseRev(local_abspath);
            }
        } else if (status == SVNWCDbStatus.Deleted) {
            final WCDbDeletionInfo delInfo = db.scanDeletion(local_abspath, DeletionInfoField.workDelAbsPath);
            final File workDelAbspath = delInfo.workDelAbsPath;
            if (workDelAbspath != null) {
                /*
                 * This is a deletion within a copied subtree. Get the
                 * copied-from revision.
                 */
                final File parentAbspath = SVNFileUtil.getFileDir(workDelAbspath);
                final WCDbInfo parentInfo = db.readInfo(parentAbspath, InfoField.status);
                final SVNWCDbStatus parentStatus = parentInfo.status;
                assert (parentStatus == SVNWCDbStatus.Added || parentStatus == SVNWCDbStatus.ObstructedAdd);
                final WCDbAdditionInfo parentAddInfo = db.scanAddition(parentAbspath, AdditionInfoField.originalRevision);
                commitBaseRevision = parentAddInfo.originalRevision;
            } else
                /* This is a normal delete. Get the base revision. */
                commitBaseRevision = getNodeBaseRev(local_abspath);
        }
        return commitBaseRevision;
    }

    private long getNodeBaseRev(File local_abspath) throws SVNException {
        final WCDbInfo info = db.readInfo(local_abspath, InfoField.status, InfoField.revision, InfoField.haveBase);
        long baseRevision = info.revision;
        if (SVNRevision.isValidRevisionNumber(baseRevision)) {
            return baseRevision;
        }
        final boolean haveBase = info.haveBase;
        if (haveBase) {
            /* The node was replaced with something else. Look at the base. */
            final WCDbBaseInfo baseInfo = db.getBaseInfo(local_abspath, BaseInfoField.revision);
            baseRevision = baseInfo.revision;
        }
        return baseRevision;
    }

    public enum SVNWCSchedule {
        /** Nothing special here */
        normal,

        /** Slated for addition */
        add,

        /** Slated for deletion */
        delete,

        /** Slated for replacement (delete + add) */
        replace

    }

    private class ScheduleInternalInfo {

        public SVNWCSchedule schedule;
        public boolean copied;
    }

    private ScheduleInternalInfo getNodeScheduleInternal(File localAbsPath, boolean schedule, boolean copied) throws SVNException {
        final ScheduleInternalInfo info = new ScheduleInternalInfo();

        if (schedule)
            info.schedule = SVNWCSchedule.normal;
        if (copied)
            info.copied = false;

        WCDbInfo readInfo = db.readInfo(localAbsPath, InfoField.status, InfoField.originalReposRelpath, InfoField.haveBase);
        SVNWCDbStatus status = readInfo.status;
        File copyFromRelpath = readInfo.originalReposRelpath;
        boolean hasBase = readInfo.haveBase;

        switch (status) {
            case NotPresent:
            case Absent:
            case Excluded:
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_NOT_FOUND, "''{0}'' is not under version control", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
                return null;

            case Normal:
            case Incomplete:
            case Obstructed:
                break;

            case Deleted:
            case ObstructedDelete: {

                if (schedule)
                    info.schedule = SVNWCSchedule.delete;

                if (!copied)
                    break;

                /* Find out details of our deletion. */
                File work_del_abspath = db.scanDeletion(localAbsPath, DeletionInfoField.workDelAbsPath).workDelAbsPath;

                if (work_del_abspath == null)
                    break; /* Base deletion */

                /*
                 * We miss the 4th tree to properly find out if this is the root
                 * of a working-delete. Only in that case should copied be set
                 * to true. See entries.c for details.
                 */

                info.copied = false; /* Until we can fix this test */
                break;
            }
            case Added:
            case ObstructedAdd: {
                File op_root_abspath;
                File parent_abspath;
                File parent_copyfrom_relpath;
                String child_name;

                if (schedule)
                    info.schedule = SVNWCSchedule.add;

                if (copyFromRelpath != null) {
                    status = SVNWCDbStatus.Copied; /* Or moved */
                    op_root_abspath = localAbsPath;
                } else {
                    WCDbAdditionInfo scanAddition = db.scanAddition(localAbsPath, AdditionInfoField.status, AdditionInfoField.opRootAbsPath, AdditionInfoField.originalReposRelPath);
                    status = scanAddition.status;
                    op_root_abspath = scanAddition.opRootAbsPath;
                    copyFromRelpath = scanAddition.originalReposRelPath;
                }

                if (copied && status != SVNWCDbStatus.Added)
                    info.copied = true;

                if (!schedule)
                    break;

                if (hasBase) {
                    SVNWCDbStatus base_status = db.getBaseInfo(localAbsPath, BaseInfoField.status).status;
                    if (base_status != SVNWCDbStatus.NotPresent)
                        info.schedule = SVNWCSchedule.replace;
                }

                if (status == SVNWCDbStatus.Added)
                    break; /* Local addition */

                /*
                 * Determine the parent status to check if we should show the
                 * schedule of a child of a copy as normal.
                 */
                if (!op_root_abspath.equals(localAbsPath)) {
                    info.schedule = SVNWCSchedule.normal;
                    break; /* Part of parent copy */
                }

                /*
                 * When we used entries we didn't see just a different revision
                 * as a new operational root, so we have to check if the parent
                 * is from the same copy origin
                 */
                parent_abspath = SVNFileUtil.getFileDir(localAbsPath);

                WCDbInfo parentReadInfo = db.readInfo(parent_abspath, InfoField.status, InfoField.originalReposRelpath);
                status = parentReadInfo.status;
                parent_copyfrom_relpath = parentReadInfo.originalReposRelpath;

                if (status != SVNWCDbStatus.Added)
                    break; /* Parent was not added */

                if (parent_copyfrom_relpath == null) {
                    WCDbAdditionInfo scanAddition = db.scanAddition(parent_abspath, AdditionInfoField.status, AdditionInfoField.opRootAbsPath, AdditionInfoField.originalReposRelPath);
                    status = scanAddition.status;
                    op_root_abspath = scanAddition.opRootAbsPath;
                    parent_copyfrom_relpath = scanAddition.originalReposRelPath;

                    if (parent_copyfrom_relpath == null)
                        break; /* Parent is a local addition */

                    parent_copyfrom_relpath = SVNFileUtil.createFilePath(parent_copyfrom_relpath, SVNPathUtil.getPathAsChild(op_root_abspath.toString(), parent_abspath.toString()));

                }

                child_name = SVNPathUtil.getPathAsChild(parent_copyfrom_relpath.toString(), copyFromRelpath.toString());

                if (child_name == null || !child_name.equals(SVNFileUtil.getFileName(localAbsPath)))
                    break; /* Different operation */

                info.schedule = SVNWCSchedule.normal;
                break;
            }
            default:
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.ASSERTION_FAIL), SVNLogType.WC);
                return null;
        }

        return info;
    }

    public SVNStatus assembleUnversioned(File localAbspath, SVNNodeKind pathKind, boolean isIgnored) throws SVNException {
        final SVNStatus17 stat17 = assembleUnversioned17(localAbspath, pathKind, isIgnored);
        if (stat17 == null) {
            return null;
        }
        return stat17.getStatus16();
    }

    public SVNStatus17 assembleUnversioned17(File localAbspath, SVNNodeKind pathKind, boolean isIgnored) throws SVNException {

        /*
         * Find out whether the path is a tree conflict victim. This function
         * will set tree_conflict to NULL if the path is not a victim.
         */
        SVNTreeConflictDescription tree_conflict = db.opReadTreeConflict(localAbspath);

        SVNStatus17 stat = new SVNStatus17();
        stat.setLocalAbsPath(localAbspath);
        stat.setKind(SVNNodeKind.UNKNOWN); /* not versioned */
        stat.setDepth(SVNDepth.UNKNOWN);
        stat.setNodeStatus(SVNStatusType.STATUS_NONE);
        stat.setTextStatus(SVNStatusType.STATUS_NONE);
        stat.setPropStatus(SVNStatusType.STATUS_NONE);
        stat.setReposNodeStatus(SVNStatusType.STATUS_NONE);
        stat.setReposTextStatus(SVNStatusType.STATUS_NONE);
        stat.setReposPropStatus(SVNStatusType.STATUS_NONE);

        /*
         * If this path has no entry, but IS present on disk, it's unversioned.
         * If this file is being explicitly ignored (due to matching an
         * ignore-pattern), the node_status is set to svn_wc_status_ignored.
         * Otherwise the node_status is set to svn_wc_status_unversioned.
         */
        if (pathKind != SVNNodeKind.NONE) {
            if (isIgnored) {
                stat.setNodeStatus(SVNStatusType.STATUS_IGNORED);
            } else {
                stat.setNodeStatus(SVNStatusType.STATUS_UNVERSIONED);
            }
        } else if (tree_conflict != null) {
            /*
             * If this path has no entry, is NOT present on disk, and IS a tree
             * conflict victim, count it as missing.
             */
            stat.setNodeStatus(SVNStatusType.STATUS_MISSING);
        }

        stat.setRevision(INVALID_REVNUM);
        stat.setChangedRev(INVALID_REVNUM);
        stat.setOodChangedRev(INVALID_REVNUM);
        stat.setOodKind(SVNNodeKind.NONE);

        /*
         * For the case of an incoming delete to a locally deleted path during
         * an update, we get a tree conflict.
         */
        stat.setConflicted(tree_conflict != null);
        stat.setChangelist(null);
        stat.setTreeConflict(tree_conflict);

        return stat;
    }

    public SVNStatus assembleStatus(File localAbsPath, SVNURL parentReposRootUrl, File parentReposRelPath, SVNNodeKind pathKind, boolean pathSpecial, boolean getAll, SVNLock repositoryLock)
            throws SVNException {
        final SVNStatus17 stat17 = assembleStatus17(localAbsPath, parentReposRootUrl, parentReposRelPath, pathKind, pathSpecial, getAll, repositoryLock);
        if (stat17 == null) {
            return null;
        }
        return stat17.getStatus16();
    }

    public SVNStatus17 assembleStatus17(File localAbsPath, SVNURL parentReposRootUrl, File parentReposRelPath, SVNNodeKind pathKind, boolean pathSpecial, boolean getAll, SVNLock repositoryLock)
            throws SVNException {

        boolean switched_p, copied = false;

        /* Defaults for two main variables. */
        SVNStatusType node_status = SVNStatusType.STATUS_NORMAL;
        SVNStatusType text_status = SVNStatusType.STATUS_NORMAL;
        SVNStatusType prop_status = SVNStatusType.STATUS_NONE;

        final WCDbInfo info = db.readInfo(localAbsPath, InfoField.status, InfoField.kind, InfoField.revision, InfoField.reposRelPath, InfoField.reposRootUrl, InfoField.changedRev,
                InfoField.changedDate, InfoField.changedAuthor, InfoField.depth, InfoField.changelist, InfoField.propsMod, InfoField.haveBase, InfoField.conflicted, InfoField.lock);

        if (info.reposRelPath == null) {
            /* The node is not switched, so imply from parent if possible */

            if (parentReposRelPath != null) {
                info.reposRelPath = SVNFileUtil.createFilePath(parentReposRelPath, SVNFileUtil.getFileName(localAbsPath));
            } else if (info.status == SVNWCDbStatus.Added) {
                final WCDbAdditionInfo scanAddition = db.scanAddition(localAbsPath, AdditionInfoField.reposRelPath, AdditionInfoField.reposRootUrl);
                info.reposRelPath = scanAddition.reposRelPath;
                info.reposRootUrl = scanAddition.reposRootUrl;
            } else if (info.haveBase) {
                final WCDbRepositoryInfo baseRepo = db.scanBaseRepository(localAbsPath, RepositoryInfoField.relPath, RepositoryInfoField.rootUrl);
                info.reposRelPath = baseRepo.relPath;
                info.reposRootUrl = baseRepo.rootUrl;
            }

            switched_p = false;
        } else if (parentReposRelPath == null) {
            switched_p = false;
        } else {
            /* A node is switched if it doesn't have the implied repos_relpath */
            final String name = getPathAsChild(parentReposRelPath, info.reposRelPath);
            switched_p = name == null || !name.equals(SVNFileUtil.getFileName(localAbsPath));
        }

        if (info.reposRootUrl == null && parentReposRootUrl != null)
            info.reposRootUrl = parentReposRootUrl;

        /*
         * Examine whether our directory metadata is present, and compensate if
         * it is missing.
         *
         * There are a several kinds of obstruction that we detect here:
         *
         * - versioned subdir is missing - the versioned subdir's admin area is
         * missing - the versioned subdir has been replaced with a file/symlink
         *
         * Net result: the target is obstructed and the metadata is unavailable.
         *
         * Note: wc_db can also detect a versioned file that has been replaced
         * with a versioned subdir (moved from somewhere). We don't look for
         * that right away because the file's metadata is still present, so we
         * can examine properties and conflicts and whatnot.
         *
         * ### note that most obstruction concepts disappear in single-db mode
         */
        if (info.kind == SVNWCDbKind.Dir) {
            if (info.status == SVNWCDbStatus.Incomplete) {
                /* Highest precedence. */
                node_status = SVNStatusType.STATUS_INCOMPLETE;
            } else if (info.status == SVNWCDbStatus.Deleted) {
                node_status = SVNStatusType.STATUS_DELETED;
                copied = getNodeScheduleInternal(localAbsPath, false, true).copied;
            } else if (pathKind == null || pathKind != SVNNodeKind.DIR) {
                /*
                 * A present or added directory should be on disk, so it is
                 * reported missing or obstructed.
                 */
                if (pathKind == null || pathKind == SVNNodeKind.NONE)
                    node_status = SVNStatusType.STATUS_MISSING;
                else
                    node_status = SVNStatusType.STATUS_OBSTRUCTED;
            }
        } else {
            if (info.status == SVNWCDbStatus.Deleted) {
                node_status = SVNStatusType.STATUS_DELETED;
                copied = getNodeScheduleInternal(localAbsPath, false, true).copied;
            } else if (pathKind == null || pathKind != SVNNodeKind.FILE) {
                /*
                 * A present or added file should be on disk, so it is reported
                 * missing or obstructed.
                 */
                if (pathKind == null || pathKind == SVNNodeKind.NONE)
                    node_status = SVNStatusType.STATUS_MISSING;
                else
                    node_status = SVNStatusType.STATUS_OBSTRUCTED;
            }
        }

        /*
         * If NODE_STATUS is still normal, after the above checks, then we
         * should proceed to refine the status.
         *
         * If it was changed, then the subdir is incomplete or
         * missing/obstructed. It means that no further information is
         * available, and we should skip all this work.
         */
        if (node_status == SVNStatusType.STATUS_NORMAL || (node_status == SVNStatusType.STATUS_MISSING && info.kind != SVNWCDbKind.Dir)) {
            boolean has_props;
            boolean text_modified_p = false;
            boolean wc_special;

            /* Implement predecence rules: */

            /*
             * 1. Set the two main variables to "discovered" values first (M,
             * C). Together, these two stati are of lowest precedence, and C has
             * precedence over M.
             */

            /* Does the node have props? */
            if (info.status == SVNWCDbStatus.Deleted)
                has_props = false; /* Not interesting */
            else if (info.propsMod)
                has_props = true;
            else {
                SVNProperties props = getPristineProperties(localAbsPath);
                if (props != null && props.size() > 0)
                    has_props = true;
                else {
                    props = getActialProperties(localAbsPath);
                    has_props = (props != null && props.size() > 0);
                }
            }
            if (has_props)
                prop_status = SVNStatusType.STATUS_NORMAL;

            /* If the entry has a property file, see if it has local changes. */
            /*
             * ### we could compute this ourself, based on the prop hashes ###
             * fetched above. but for now, there is some trickery we may ###
             * need to rely upon in ths function. keep it for now.
             */
            /* ### see r944980 as an example of the brittleness of this stuff. */
            if (has_props) {
                prop_status = info.propsMod ? SVNStatusType.STATUS_MODIFIED : SVNStatusType.STATUS_NORMAL;
            }

            if (has_props)
                wc_special = isSpecial(localAbsPath);
            else
                wc_special = false;

            /* If the entry is a file, check for textual modifications */
            if ((info.kind == SVNWCDbKind.File || info.kind == SVNWCDbKind.Symlink) && (wc_special == pathSpecial)) {
                try {
                    text_modified_p = isTextModified(localAbsPath, false, true);
                } catch (SVNException e) {
                    if (!isErrorAccess(e))
                        throw e;
                    /*
                     * An access denied is very common on Windows when another
                     * application has the file open. Previously we ignored this
                     * error in svn_wc__text_modified_internal_p, where it
                     * should have really errored.
                     */
                }
            } else if (wc_special != pathSpecial)
                node_status = SVNStatusType.STATUS_OBSTRUCTED;

            if (text_modified_p)
                text_status = SVNStatusType.STATUS_MODIFIED;
        }

        /*
         * While tree conflicts aren't stored on the node themselves, check
         * explicitly for tree conflicts to allow our users to ignore this
         * detail
         */
        SVNTreeConflictDescription tree_conflict = null;
        ConflictedInfo conflictInfo = null;
        if (!info.conflicted) {
            tree_conflict = db.opReadTreeConflict(localAbsPath);
            info.conflicted = (tree_conflict != null);
        } else {
            /*
             * ### Check if the conflict was resolved by removing the marker
             * files. ### This should really be moved to the users of this API
             */
            conflictInfo = getConflicted(localAbsPath, true, true, true);
            if (!conflictInfo.textConflicted && !conflictInfo.propConflicted && !conflictInfo.treeConflicted)
                info.conflicted = false;
            else if (conflictInfo.treeConflicted)
                tree_conflict = conflictInfo.treeConflict;
        }

        if (node_status == SVNStatusType.STATUS_NORMAL) {
            /*
             * 2. Possibly overwrite the text_status variable with "scheduled"
             * states from the entry (A, D, R). As a group, these states are of
             * medium precedence. They also override any C or M that may be in
             * the prop_status field at this point, although they do not
             * override a C text status.
             */

            /*
             * ### db_status, base_shadowed, and fetching base_status can ###
             * fully replace entry->schedule here.
             */

            if (info.status == SVNWCDbStatus.Added) {
                ScheduleInternalInfo scheduleInfo = getNodeScheduleInternal(localAbsPath, true, true);
                copied = scheduleInfo.copied;
                if (scheduleInfo.schedule == SVNWCSchedule.add) {
                    node_status = SVNStatusType.STATUS_ADDED;
                } else if (scheduleInfo.schedule == SVNWCSchedule.replace) {
                    node_status = SVNStatusType.STATUS_REPLACED;
                }
            }
        }

        if (node_status == SVNStatusType.STATUS_NORMAL)
            node_status = text_status;

        if (node_status == SVNStatusType.STATUS_NORMAL && prop_status != SVNStatusType.STATUS_NONE)
            node_status = prop_status;

        /*
         * 5. Easy out: unless we're fetching -every- entry, don't bother to
         * allocate a struct for an uninteresting entry.
         */

        if (!getAll)
            if (((node_status == SVNStatusType.STATUS_NONE) || (node_status == SVNStatusType.STATUS_NORMAL)) && (!switched_p) && (info.lock == null) && (repositoryLock == null)
                    && (info.changelist == null) && (!info.conflicted)) {
                return null;
            }

        /* 6. Build and return a status structure. */

        SVNStatus17 stat = new SVNStatus17();
        stat.setLocalAbsPath(localAbsPath);

        switch (info.kind) {
            case Dir:
                stat.setKind(SVNNodeKind.DIR);
                break;
            case File:
            case Symlink:
                stat.setKind(SVNNodeKind.FILE);
                break;
            case Unknown:
            default:
                stat.setKind(SVNNodeKind.UNKNOWN);
        }

        if (info.lock != null) {
            stat.setLock(new SVNLock(info.reposRelPath.toString(), info.lock.token, info.lock.owner, info.lock.comment, info.lock.date, null));
        }

        stat.setDepth(info.depth);
        stat.setNodeStatus(node_status);
        stat.setTextStatus(text_status);
        stat.setPropStatus(prop_status);
        stat.setReposNodeStatus(SVNStatusType.STATUS_NONE); /* default */
        stat.setReposTextStatus(SVNStatusType.STATUS_NONE); /* default */
        stat.setReposPropStatus(SVNStatusType.STATUS_NONE); /* default */
        stat.setSwitched(switched_p);
        stat.setCopied(copied);
        stat.setReposLock(repositoryLock);
        stat.setRevision(info.revision);
        stat.setChangedRev(info.changedRev);
        stat.setChangedAuthor(info.changedAuthor);
        stat.setChangedDate(info.changedDate);

        stat.setOodKind(SVNNodeKind.NONE);
        stat.setOodChangedRev(INVALID_REVNUM);
        stat.setOodChangedDate(null);
        stat.setOodChangedAuthor(null);

        stat.setConflicted(info.conflicted);
        stat.setConflictedInfo(conflictInfo);
        stat.setVersioned(true);
        stat.setChangelist(info.changelist);
        stat.setReposRootUrl(info.reposRootUrl);
        stat.setReposRelpath(info.reposRelPath);
        stat.setTreeConflict(tree_conflict);

        if (stat.isConflicted() && stat.getConflictedInfo() != null) {

            if (stat.getConflictedInfo().textConflicted)
                stat.setTextStatus(SVNStatusType.STATUS_CONFLICTED);

            if (stat.getConflictedInfo().propConflicted)
                stat.setPropStatus(SVNStatusType.STATUS_CONFLICTED);

            /* ### Also set this for tree_conflicts? */
            if (stat.getConflictedInfo().textConflicted || stat.getConflictedInfo().propConflicted)
                stat.setNodeStatus(SVNStatusType.STATUS_CONFLICTED);
        }

        if (!SVNRevision.isValidRevisionNumber(stat.getRevision()) && !stat.isCopied()) {
            /* Retrieve some data from the original version of the replaced node */
            NodeWorkingRevInfo wrkRevInfo = getNodeWorkingRevInfo(localAbsPath);
            stat.setRevision(wrkRevInfo.revision);
            stat.setChangedRev(wrkRevInfo.changedRev);
            stat.setChangedDate(wrkRevInfo.changedDate);
            stat.setChangedAuthor(wrkRevInfo.changedAuthor);
        }

        return stat;
    }

    private static class NodeWorkingRevInfo {

        public long revision;
        public long changedRev;
        public Date changedDate;
        public String changedAuthor;
    }

    private NodeWorkingRevInfo getNodeWorkingRevInfo(File localAbsPath) throws SVNException {

        final NodeWorkingRevInfo info = new NodeWorkingRevInfo();

        final WCDbInfo readInfo = db.readInfo(localAbsPath, InfoField.status, InfoField.revision, InfoField.changedRev, InfoField.changedDate, InfoField.changedAuthor, InfoField.haveBase);
        info.revision = readInfo.revision;
        info.changedRev = readInfo.changedRev;
        info.changedDate = readInfo.changedDate;
        info.changedAuthor = readInfo.changedAuthor;

        if (SVNRevision.isValidRevisionNumber(info.changedRev) && SVNRevision.isValidRevisionNumber(info.revision))
            return info; /* We got everything we need */

        if (readInfo.status == SVNWCDbStatus.Deleted) {
            WCDbDeletionInfo scanDeletion = db.scanDeletion(localAbsPath, DeletionInfoField.baseDelAbsPath, DeletionInfoField.workDelAbsPath);
            if (scanDeletion.workDelAbsPath != null) {
                final WCDbInfo readInfo2 = db.readInfo(scanDeletion.workDelAbsPath, InfoField.status, InfoField.revision, InfoField.changedRev, InfoField.changedDate, InfoField.changedAuthor,
                        InfoField.haveBase);
                info.revision = readInfo2.revision;
                info.changedRev = readInfo2.changedRev;
                info.changedDate = readInfo2.changedDate;
                info.changedAuthor = readInfo2.changedAuthor;
            } else {
                final WCDbBaseInfo baseInfo = db.getBaseInfo(scanDeletion.baseDelAbsPath, BaseInfoField.revision, BaseInfoField.changedRev, BaseInfoField.changedDate, BaseInfoField.changedAuthor);
                info.revision = baseInfo.revision;
                info.changedRev = baseInfo.changedRev;
                info.changedDate = baseInfo.changedDate;
                info.changedAuthor = baseInfo.changedAuthor;
            }
        } else if (readInfo.haveBase) {
            final WCDbBaseInfo baseInfo = db.getBaseInfo(localAbsPath, BaseInfoField.status, BaseInfoField.revision, BaseInfoField.changedRev, BaseInfoField.changedDate, BaseInfoField.changedAuthor);
            info.changedRev = baseInfo.changedRev;
            info.changedDate = baseInfo.changedDate;
            info.changedAuthor = baseInfo.changedAuthor;

            if (!SVNRevision.isValidRevisionNumber(info.revision) && baseInfo.status != SVNWCDbStatus.NotPresent) {
                /*
                 * When we used entries we reset the revision to 0 when we added
                 * a new node over an existing not present node
                 */
                info.revision = baseInfo.revision;
            }
        }
        return info;
    }

    private SVNProperties getPristineProperties(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));

        /*
         * Certain node stats do not have properties defined on them. Check the
         * state, and return NULL for these situations.
         */

        final WCDbInfo info = db.readInfo(localAbsPath, InfoField.status);

        if (info.status == SVNWCDbStatus.Added) {
            /*
             * Resolve the status. copied and moved_here arrive with properties,
             * while a simple add does not.
             */
            final WCDbAdditionInfo addition = db.scanAddition(localAbsPath, AdditionInfoField.status);
            info.status = addition.status;
        }
        if (info.status == SVNWCDbStatus.Added
        // #if 0
                /*
                 * ### the update editor needs to fetch properties while the
                 * directory ### is still marked incomplete
                 */
                // || status == svn_wc__db_status_incomplete
                // #endif
                || info.status == SVNWCDbStatus.Excluded || info.status == SVNWCDbStatus.Absent || info.status == SVNWCDbStatus.NotPresent) {
            return null;
        }

        /*
         * The node is obstructed:
         *
         * - subdir is missing, obstructed by a file, or missing admin area - a
         * file is obstructed by a versioned subdir (### not reported)
         *
         * Thus, properties are not available for this node. Returning NULL
         * would indicate "not defined" for its state. For obstructions, we
         * cannot *determine* whether properties should be here or not.
         *
         * ### it would be nice to report an obstruction, rather than simply ###
         * PROPERTY_NOT_FOUND. but this is transitional until single-db.
         */
        if (info.status == SVNWCDbStatus.ObstructedDelete || info.status == SVNWCDbStatus.Obstructed || info.status == SVNWCDbStatus.ObstructedAdd) {

            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.PROPERTY_NOT_FOUND, "Directory ''{0}'' is missing on disk, so the " + "properties are not available.", localAbsPath);
            SVNErrorManager.error(err, SVNLogType.WC);

        }

        /* status: normal, moved_here, copied, deleted */
        /* After the above checks, these pristines should always be present. */
        return db.readPristineProperties(localAbsPath);
    }

    private boolean isSpecial(File path) throws SVNException {
        final String property = getProperty(path, SVNProperty.SPECIAL);
        return property != null;
    }

    private boolean isPropertiesDiff(SVNProperties pristineProperties, SVNProperties actualProperties) {
        if (pristineProperties == null && actualProperties == null) {
            return false;
        }
        if (pristineProperties == null || actualProperties == null) {
            return true;
        }
        final SVNProperties diff = actualProperties.compareTo(pristineProperties);
        return diff != null && !diff.isEmpty();
    }

    public boolean isTextModified(File localAbsPath, boolean forceComparison, boolean compareTextBases) throws SVNException {

        /* No matter which way you look at it, the file needs to exist. */
        if (!localAbsPath.exists() || !localAbsPath.isFile()) {
            /*
             * There is no entity, or, the entity is not a regular file or link.
             * So, it can't be modified.
             */
            return false;
        }

        if (!forceComparison) {

            /*
             * We're allowed to use a heuristic to determine whether files may
             * have changed. The heuristic has these steps:
             *
             *
             * 1. Compare the working file's size with the size cached in the
             * entries file 2. If they differ, do a full file compare 3. Compare
             * the working file's timestamp with the timestamp cached in the
             * entries file 4. If they differ, do a full file compare 5.
             * Otherwise, return indicating an unchanged file.
             *
             * There are 2 problematic situations which may occur:
             *
             * 1. The cached working size is missing --> In this case, we forget
             * we ever tried to compare and skip to the timestamp comparison.
             * This is because old working copies do not contain cached sizes
             *
             * 2. The cached timestamp is missing --> In this case, we forget we
             * ever tried to compare and skip to full file comparison. This is
             * because the timestamp will be removed when the library updates a
             * locally changed file. (ie, this only happens when the file was
             * locally modified.)
             */

            boolean compareThem = false;

            /* Read the relevant info */
            WCDbInfo readInfo = null;
            try {
                readInfo = db.readInfo(localAbsPath, InfoField.lastModTime, InfoField.translatedSize);
            } catch (SVNException e) {
                compareThem = true;
            }

            if (!compareThem) {
                /* Compare the sizes, if applicable */
                if (readInfo.translatedSize != ISVNWCDb.ENTRY_WORKING_SIZE_UNKNOWN && localAbsPath.length() != readInfo.translatedSize) {
                    compareThem = true;
                }
            }

            if (!compareThem) {
                /*
                 * Compare the timestamps
                 *
                 * Note: text_time == 0 means absent from entries, which also
                 * means the timestamps won't be equal, so there's no need to
                 * explicitly check the 'absent' value.
                 */
                if (compareTimestamps(localAbsPath, readInfo)) {
                    compareThem = true;
                }
            }

            if (!compareThem) {
                return false;
            }
        }

        // compare_them:
        /*
         * If there's no text-base file, we have to assume the working file is
         * modified. For example, a file scheduled for addition but not yet
         * committed.
         */
        /*
         * We used to stat for the working base here, but we just give
         * compare_and_verify a try; we'll check for errors afterwards
         */
        InputStream pristineStream;
        try {
            pristineStream = getPristineContents(localAbsPath);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                return true;
            }
            throw e;
        }

        if (pristineStream == null) {
            return true;
        }

        /* Check all bytes, and verify checksum if requested. */
        return compareAndVerify(localAbsPath, pristineStream, compareTextBases, forceComparison);
    }

    private boolean compareTimestamps(File localAbsPath, WCDbInfo readInfo) {
        return SVNFileUtil.roundTimeStamp(readInfo.lastModTime) != SVNFileUtil.roundTimeStamp(localAbsPath.lastModified() * (SVNFileUtil.isWindows ? 1000 : 1));
    }

    private InputStream getPristineContents(File localAbspath) throws SVNException {

        final WCDbInfo readInfo = db.readInfo(localAbspath, InfoField.status, InfoField.kind, InfoField.checksum);

        /* Sanity */
        if (readInfo.kind != SVNWCDbKind.File) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "Can only get the pristine contents of files;" + "  '{0}' is not a file", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (readInfo.status == SVNWCDbStatus.Added) {
            /*
             * For an added node, we return "no stream". Make sure this is not
             * copied-here or moved-here, in which case we return the copy/move
             * source's contents.
             */
            final WCDbAdditionInfo scanAddition = db.scanAddition(localAbspath, AdditionInfoField.status);

            if (scanAddition.status == SVNWCDbStatus.Added) {
                /* Simply added. The pristine base does not exist. */
                return null;
            }
        } else if (readInfo.status == SVNWCDbStatus.NotPresent) {
            /*
             * We know that the delete of this node has been committed. This
             * should be the same as if called on an unknown path.
             */
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Cannot get the pristine contents of '{0}' " + "because its delete is already committed", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else if (readInfo.status == SVNWCDbStatus.Absent || readInfo.status == SVNWCDbStatus.Excluded || readInfo.status == SVNWCDbStatus.Incomplete) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_UNEXPECTED_STATUS, "Cannot get the pristine contents of '{0}' " + "because it has an unexpected status", localAbspath);
            SVNErrorManager.error(err, SVNLogType.WC);
        } else {
            /*
             * We know that it is a file, so we can't hit the _obstructed stati.
             * Also, we should never see _base_deleted here.
             */
            SVNErrorManager.assertionFailure(readInfo.status != SVNWCDbStatus.Obstructed && readInfo.status != SVNWCDbStatus.ObstructedAdd && readInfo.status != SVNWCDbStatus.ObstructedDelete
                    && readInfo.status != SVNWCDbStatus.BaseDeleted, null, SVNLogType.WC);
        }

        if (readInfo.checksum == null) {
            return null;
        }

        return db.readPristine(localAbspath, readInfo.checksum);
    }

    /**
     * Return TRUE if (after translation) VERSIONED_FILE_ABSPATH differs from
     * PRISTINE_STREAM, else to FALSE if not. Also verify that PRISTINE_STREAM
     * matches the stored checksum for VERSIONED_FILE_ABSPATH, if
     * verify_checksum is TRUE. If checksum does not match, throw the error
     * SVN_ERR_WC_CORRUPT_TEXT_BASE.
     * <p>
     * If COMPARE_TEXTBASES is true, translate VERSIONED_FILE_ABSPATH's EOL
     * style and keywords to repository-normal form according to its properties,
     * and compare the result with PRISTINE_STREAM. If COMPARE_TEXTBASES is
     * false, translate PRISTINE_STREAM's EOL style and keywords to working-copy
     * form according to VERSIONED_FILE_ABSPATH's properties, and compare the
     * result with VERSIONED_FILE_ABSPATH.
     * <p>
     * PRISTINE_STREAM will be closed before a successful return.
     *
     */
    private boolean compareAndVerify(File versionedFileAbsPath, InputStream pristineStream, boolean compareTextBases, boolean verifyChecksum) throws SVNException {
        InputStream vStream = null; /* versioned_file */
        try {
            boolean same = false;

            assert (versionedFileAbsPath != null && versionedFileAbsPath.isAbsolute());

            final SVNEolStyleInfo eolStyle = getEolStyle(versionedFileAbsPath);
            final Map keywords = getKeyWords(versionedFileAbsPath, null);
            final boolean special = isSpecial(versionedFileAbsPath);
            final boolean needTranslation = isTranslationRequired(eolStyle.eolStyle, eolStyle.eolStr, keywords, special, true);

            if (verifyChecksum || needTranslation) {
                /* Reading files is necessary. */
                SVNChecksum checksum = null;
                SVNChecksum nodeChecksum = null;

                if (verifyChecksum) {
                    /*
                     * Need checksum verification, so read checksum from entries
                     * file and setup checksummed stream for base file.
                     */
                    final WCDbInfo nodeInfo = db.readInfo(versionedFileAbsPath, InfoField.checksum);
                    /*
                     * SVN_EXPERIMENTAL_PRISTINE: node_checksum is originally
                     * MD-5 but will later be SHA-1. To allow for this, we
                     * calculate CHECKSUM as the same kind so that we can
                     * compare them.
                     */
                    if (nodeInfo.checksum != null) {
                        pristineStream = new SVNChecksumInputStream(pristineStream, nodeInfo.checksum.getKind().toString());
                    }
                }

                if (special) {
                    vStream = readSpecialFile(versionedFileAbsPath);
                } else {
                    vStream = SVNFileUtil.openFileForReading(versionedFileAbsPath);

                    if (compareTextBases && needTranslation) {
                        if (eolStyle.eolStyle == SVNEolStyle.Native)
                            eolStyle.eolStr = SVNEolStyleInfo.NATIVE_EOL_STR;
                        else if (eolStyle.eolStyle != SVNEolStyle.Fixed && eolStyle.eolStyle != SVNEolStyle.None) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_UNKNOWN_EOL);
                            SVNErrorManager.error(err, SVNLogType.WC);
                        }

                        /*
                         * Wrap file stream to detranslate into normal form,
                         * "repairing" the EOL style if it is inconsistent.
                         */
                        vStream = SVNTranslator.getTranslatingInputStream(vStream, null, eolStyle.eolStr, true, keywords, false);
                    } else if (needTranslation) {
                        /*
                         * Wrap base stream to translate into working copy form,
                         * and arrange to throw an error if its EOL style is
                         * inconsistent.
                         */
                        pristineStream = SVNTranslator.getTranslatingInputStream(pristineStream, null, eolStyle.eolStr, false, keywords, true);
                    }
                }

                try {
                    same = isSameContents(pristineStream, vStream);
                } catch (IOException e) {
                    SVNTranslator.translationError(versionedFileAbsPath, e);
                }

                if (verifyChecksum && nodeChecksum != null) {
                    // TODO unreachable code
                    if (checksum != null && !isChecksumMatch(checksum, nodeChecksum)) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_CORRUPT_TEXT_BASE, "Checksum mismatch indicates corrupt text base for file: "
                                + "'{0}':\n   expected:  {1}\n     actual:  {2}\n", new Object[] {
                                versionedFileAbsPath, nodeChecksum, checksum
                        });
                        SVNErrorManager.error(err, SVNLogType.WC);
                    }
                }
            } else {
                /* Translation would be a no-op, so compare the original file. */
                vStream = SVNFileUtil.openFileForReading(versionedFileAbsPath);
                try {
                    same = isSameContents(vStream, pristineStream);
                } catch (IOException e) {
                    SVNTranslator.translationError(versionedFileAbsPath, e);
                }
            }

            return (!same);

        } finally {
            SVNFileUtil.closeFile(pristineStream);
            SVNFileUtil.closeFile(vStream);
        }

    }

    private boolean isChecksumMatch(SVNChecksum checksum1, SVNChecksum checksum2) {
        if (checksum1 == null || checksum2 == null)
            return true;
        if (checksum1.getKind() != checksum2.getKind())
            return false;
        return checksum1.getDigest() == null || checksum2.getDigest() == null || checksum1.getDigest() == checksum2.getDigest();
    }

    private boolean isSameContents(InputStream stream1, InputStream stream2) throws IOException {
        byte[] buf1 = new byte[STREAM_CHUNK_SIZE];
        byte[] buf2 = new byte[STREAM_CHUNK_SIZE];
        long bytes_read1 = STREAM_CHUNK_SIZE;
        long bytes_read2 = STREAM_CHUNK_SIZE;

        boolean same = true; /* assume TRUE, until disproved below */
        while (bytes_read1 == STREAM_CHUNK_SIZE && bytes_read2 == STREAM_CHUNK_SIZE) {
            try {
                bytes_read1 = stream1.read(buf1);
            } catch (IOException e) {
                break;
            }
            try {
                bytes_read2 = stream2.read(buf2);
            } catch (IOException e) {
                break;
            }

            if ((bytes_read1 != bytes_read2) || !(Arrays.equals(buf1, buf2 /*
                                                                            * ,
                                                                            * bytes_read1
                                                                            */))) {
                same = false;
                break;
            }
        }

        return same;
    }

    private InputStream readSpecialFile(File localAbsPath) throws SVNException {
        /*
         * First determine what type of special file we are detranslating.
         */
        final SVNFileType filetype = SVNFileType.getType(localAbsPath);
        if (SVNFileType.FILE == filetype) {
            /*
             * Nothing special to do here, just create stream from the original
             * file's contents.
             */
            return SVNFileUtil.openFileForReading(localAbsPath, SVNLogType.WC);
        } else if (SVNFileType.SYMLINK == filetype) {
            /* Determine the destination of the link. */
            String linkPath = SVNFileUtil.getSymlinkName(localAbsPath);
            if (linkPath == null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot detranslate symbolic link ''{0}''; file does not exist or not a symbolic link", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            String symlinkContents = "link " + linkPath;
            return new ByteArrayInputStream(symlinkContents.getBytes());
        } else {
            SVNErrorManager.assertionFailure(false, "ERR_MALFUNCTION", SVNLogType.WC);
        }
        return null;
    }

    private boolean isTranslationRequired(SVNEolStyle style, byte[] eol, Map keywords, boolean special, boolean force_eol_check) {
        return (special || keywords != null || (style != SVNEolStyle.None && force_eol_check)
        // || (style == SVNEolStyle.Native && strcmp(APR_EOL_STR,
        // SVN_SUBST_NATIVE_EOL_STR) != 0)
        || (style == SVNEolStyle.Fixed && !Arrays.equals(SVNEolStyleInfo.NATIVE_EOL_STR, eol)));

    }

    private SVNEolStyleInfo getEolStyle(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));

        /* Get the property value. */
        final String propVal = getProperty(localAbsPath, SVNProperty.EOL_STYLE);

        /* Convert it. */
        return SVNEolStyleInfo.fromValue(propVal);
    }

    private Map getKeyWords(File localAbsPath, String forceList) throws SVNException {
        assert (isAbsolute(localAbsPath));

        String list;
        /*
         * Choose a property list to parse: either the one that came into this
         * function, or the one attached to PATH.
         */
        if (forceList == null) {
            list = getProperty(localAbsPath, SVNProperty.KEYWORDS);

            /* The easy answer. */
            if (list == null) {
                return null;
            }

        } else
            list = forceList;

        final SVNURL url = getNodeUrl(localAbsPath);
        final WCDbInfo readInfo = db.readInfo(localAbsPath, InfoField.changedRev, InfoField.changedDate, InfoField.changedAuthor);
        return SVNTranslator.computeKeywords(list, url.toString(), Long.toString(readInfo.changedRev), readInfo.changedDate.toString(), readInfo.changedAuthor, getOptions());
    }

    private boolean isFileExternal(File path) throws SVNException {
        final String serialized = db.getFileExternalTemp(path);
        return serialized != null;
    }

    public SVNURL getNodeUrl(File path) throws SVNException {

        final WCDbInfo readInfo = db.readInfo(path, InfoField.status, InfoField.reposRelPath, InfoField.reposRootUrl, InfoField.haveBase);

        if (readInfo.reposRelPath == null) {
            if (readInfo.status == SVNWCDbStatus.Normal || readInfo.status == SVNWCDbStatus.Incomplete
                    || (readInfo.haveBase && (readInfo.status == SVNWCDbStatus.Deleted || readInfo.status == SVNWCDbStatus.ObstructedDelete))) {
                final WCDbRepositoryInfo repos = db.scanBaseRepository(path, RepositoryInfoField.relPath, RepositoryInfoField.rootUrl);
                readInfo.reposRelPath = repos.relPath;
                readInfo.reposRootUrl = repos.rootUrl;
            } else if (readInfo.status == SVNWCDbStatus.Added) {
                final WCDbAdditionInfo scanAddition = db.scanAddition(path, AdditionInfoField.reposRelPath, AdditionInfoField.reposRootUrl);
                readInfo.reposRelPath = scanAddition.reposRelPath;
                readInfo.reposRootUrl = scanAddition.reposRootUrl;
            } else if (readInfo.status == SVNWCDbStatus.Absent || readInfo.status == SVNWCDbStatus.Excluded || readInfo.status == SVNWCDbStatus.NotPresent
                    || (!readInfo.haveBase && (readInfo.status == SVNWCDbStatus.Deleted || readInfo.status == SVNWCDbStatus.ObstructedDelete))) {
                File parent_abspath = SVNFileUtil.getFileDir(path);
                readInfo.reposRelPath = SVNFileUtil.createFilePath(SVNFileUtil.getFileName(path));
                readInfo.reposRootUrl = getNodeUrl(parent_abspath);
            } else {
                /* Status: obstructed, obstructed_add */
                return null;
            }
        }

        assert (readInfo.reposRootUrl != null && readInfo.reposRelPath != null);
        return SVNURL.parseURIDecoded(SVNPathUtil.append(readInfo.reposRootUrl.toDecodedString(), readInfo.reposRelPath.toString()));
    }

    public ConflictedInfo getConflicted(File localAbsPath, boolean isTextNeed, boolean isPropNeed, boolean isTreeNeed) throws SVNException {
        final WCDbInfo readInfo = db.readInfo(localAbsPath, InfoField.kind, InfoField.conflicted);
        final ConflictedInfo info = new ConflictedInfo();
        if (!readInfo.conflicted) {
            return info;
        }
        final File dir_path = (readInfo.kind == SVNWCDbKind.Dir) ? localAbsPath : SVNFileUtil.getFileDir(localAbsPath);
        final List<SVNConflictDescription> conflicts = db.readConflicts(localAbsPath);
        for (final SVNConflictDescription cd : conflicts) {
            final SVNMergeFileSet cdf = cd.getMergeFiles();
            if (isTextNeed && cd.isTextConflict()) {
                /*
                 * Look for any text conflict, exercising only as much effort as
                 * necessary to obtain a definitive answer. This only applies to
                 * files, but we don't have to explicitly check that entry is a
                 * file, since these attributes would never be set on a
                 * directory anyway. A conflict file entry notation only counts
                 * if the conflict file still exists on disk.
                 */
                if (cdf.getBaseFile() != null) {
                    final File path = SVNFileUtil.createFilePath(dir_path, cdf.getBaseFile());
                    final SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path));
                    if (kind == SVNNodeKind.FILE) {
                        info.textConflicted = true;
                        info.baseFile = path;
                    }
                }
                if (cdf.getRepositoryFile() != null) {
                    final File path = SVNFileUtil.createFilePath(dir_path, cdf.getRepositoryFile());
                    final SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path));
                    if (kind == SVNNodeKind.FILE) {
                        info.textConflicted = true;
                        info.repositoryFile = path;
                    }
                }
                if (cdf.getLocalFile() != null) {
                    final File path = SVNFileUtil.createFilePath(dir_path, cdf.getLocalFile());
                    final SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path));
                    if (kind == SVNNodeKind.FILE) {
                        info.textConflicted = true;
                        info.localFile = path;
                    }
                }
            } else if (isPropNeed && cd.isPropertyConflict()) {
                if (cdf.getRepositoryFile() != null) {
                    final File path = SVNFileUtil.createFilePath(dir_path, cdf.getRepositoryFile());
                    final SVNNodeKind kind = SVNFileType.getNodeKind(SVNFileType.getType(path));
                    if (kind == SVNNodeKind.FILE) {
                        info.propConflicted = true;
                        info.repositoryFile = path;
                    }
                }
            } else if (isTreeNeed && cd.isTreeConflict()) {
                info.treeConflicted = true;
                info.treeConflict = (SVNTreeConflictDescription) cd;
            }
        }
        return info;
    }

    public String getProperty(File localAbsPath, String name) throws SVNException {
        assert (isAbsolute(localAbsPath));
        assert (!SVNProperty.isEntryProperty(name));

        SVNProperties properties = null;

        final SVNWCDbKind wcKind = db.readKind(localAbsPath, true);

        if (wcKind == SVNWCDbKind.Unknown) {
            /*
             * The node is not present, or not really "here". Therefore, the
             * property is not present.
             */
            return null;
        }

        final boolean hidden = db.isNodeHidden(localAbsPath);
        if (hidden) {
            /*
             * The node is not present, or not really "here". Therefore, the
             * property is not present.
             */
            return null;
        }

        if (SVNProperty.isWorkingCopyProperty(name)) {
            /*
             * If no dav cache can be found, just set VALUE to NULL (for
             * compatibility with pre-WC-NG code).
             */
            try {
                properties = db.getBaseDavCache(localAbsPath);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_PATH_NOT_FOUND) {
                    return null;
                }
                throw e;
            }
        } else {
            /* regular prop */
            properties = getActialProperties(localAbsPath);
        }

        if (properties != null) {
            return properties.getStringValue(name);
        }

        return null;

    }

    private SVNProperties getActialProperties(File localAbsPath) throws SVNException {
        assert (isAbsolute(localAbsPath));
        /*
         * ### perform some state checking. for example, locally-deleted nodes
         * ### should not have any ACTUAL props.
         */
        return db.readProperties(localAbsPath);
    }

    public SVNEntry getEntry(File localAbsPath, boolean allowUnversioned, SVNNodeKind kind, boolean needParentStub) throws SVNException {

        /* Can't ask for the parent stub if the node is a file. */
        assert (!needParentStub || kind != SVNNodeKind.FILE);

        final EntryAccessInfo entryAccessInfo = getEntryAccessInfo(localAbsPath, kind, needParentStub);

        /*
         * NOTE: if KIND is UNKNOWN and we decided to examine the *parent*
         * directory, then it is possible we moved out of the working copy. If
         * the on-disk node is a DIR, and we asked for a stub, then we obviously
         * can't provide that (parent has no info). If the on-disk node is a
         * FILE/NONE/UNKNOWN, then it is obstructing the real LOCAL_ABSPATH (or
         * it was never a versioned item). In all these cases, the
         * read_entries() will (properly) throw an error.
         *
         * NOTE: if KIND is a DIR and we asked for the real data, but it is
         * obstructed on-disk by some other node kind (NONE, FILE, UNKNOWN),
         * then this will throw an error.
         */

        SVNEntry entry = null;

        try {
            EntryPair entryPair = readEntryPair(entryAccessInfo.dirAbspath, entryAccessInfo.entryName);
            entry = entryPair.entry;
        } catch (SVNException e) {

            if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_MISSING || kind != SVNNodeKind.UNKNOWN || !"".equals(entryAccessInfo.entryName)) {
                throw e;
            }

            /*
             * The caller didn't know the node type, we saw a directory there,
             * we attempted to read IN that directory, and then wc_db reports
             * that it is NOT a working copy directory. It is possible that one
             * of two things has happened:
             *
             * 1) a directory is obstructing a file in the parent 2) the
             * (versioned) directory's contents have been removed
             *
             * Let's assume situation (1); if that is true, then we can just
             * return the newly-found data.
             *
             * If we assumed (2), then a valid result still won't help us since
             * the caller asked for the actual contents, not the stub (which is
             * why we read *into* the directory). However, if we assume (1) and
             * get back a stub, then we have verified a missing, versioned
             * directory, and can return an error describing that.
             *
             * Redo the fetch, but "insist" we are trying to find a file. This
             * will read from the parent directory of the "file".
             */

            try {
                entry = getEntry(localAbsPath, allowUnversioned, SVNNodeKind.FILE, false);
                return entry;
            } catch (SVNException e1) {
                if (e1.getErrorMessage().getErrorCode() != SVNErrorCode.NODE_UNEXPECTED_KIND) {
                    throw e;
                }

                /*
                 * We asked for a FILE, but the node found is a DIR. Thus, we
                 * are looking at a stub. Originally, we tried to read into the
                 * subdir because NEED_PARENT_STUB is FALSE. The stub we just
                 * read is not going to work for the caller, so inform them of
                 * the missing subdirectory.
                 */
                // assert (entry != null && entry.getKind() == SVNNodeKind.DIR);

                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "Admin area of '{0}' is missing", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

        }

        if (entry == null) {
            if (allowUnversioned)
                return null;

            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_PATH_NOT_FOUND, "'{0}' is not under version control", localAbsPath);
            SVNErrorManager.error(err, SVNLogType.WC);

        }

        /* The caller had the wrong information. */
        if ((kind == SVNNodeKind.FILE && entry.getKind() != SVNNodeKind.FILE) || (kind == SVNNodeKind.DIR && entry.getKind() != SVNNodeKind.DIR)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "'{0}' is not of the right kind", localAbsPath);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (kind == SVNNodeKind.UNKNOWN) {
            /* They wanted a (directory) stub, but this isn't a directory. */
            if (needParentStub && entry.getKind() != SVNNodeKind.DIR) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "'{0}' is not of the right kind", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            /* The actual (directory) information was wanted, but we got a stub. */
            if (!needParentStub && entry.getKind() == SVNNodeKind.DIR && !"".equals(entry.getName())) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNEXPECTED_KIND, "'{0}' is not of the right kind", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }

        return entry;
    }

    private class EntryAccessInfo {

        public File dirAbspath;
        public String entryName;
    }

    private EntryAccessInfo getEntryAccessInfo(File localAbsPath, SVNNodeKind kind, boolean needParentStub) {
        boolean readFromSubdir = false;
        EntryAccessInfo info = new EntryAccessInfo();

        /* Can't ask for the parent stub if the node is a file. */
        assert (!needParentStub || kind != SVNNodeKind.FILE);

        /*
         * If the caller didn't know the node kind, then stat the path. Maybe it
         * is really there, and we can speed up the steps below.
         */
        if (kind == SVNNodeKind.UNKNOWN) {
            /* What's on disk? */
            SVNNodeKind onDisk = SVNFileType.getNodeKind(SVNFileType.getType(localAbsPath));

            if (onDisk != SVNNodeKind.DIR) {
                /*
                 * If this is *anything* besides a directory (FILE, NONE, or
                 * UNKNOWN), then we cannot treat it as a versioned directory
                 * containing entries to read. Leave READ_FROM_SUBDIR as FALSE,
                 * so that the parent will be examined.
                 *
                 * For NONE and UNKNOWN, it may be that metadata exists for the
                 * node, even though on-disk is unhelpful.
                 *
                 * If NEED_PARENT_STUB is TRUE, and the entry is not a
                 * DIRECTORY, then we'll error.
                 *
                 * If NEED_PARENT_STUB if FALSE, and we successfully read a
                 * stub, then this on-disk node is obstructing the read.
                 */
            } else {
                /*
                 * We found a directory for this UNKNOWN node. Determine whether
                 * we need to read inside it.
                 */
                readFromSubdir = !needParentStub;
            }
        } else if (kind == SVNNodeKind.DIR && !needParentStub) {
            readFromSubdir = true;
        }

        if (readFromSubdir) {
            /*
             * KIND must be a DIR or UNKNOWN (and we found a subdir). We want
             * the "real" data, so treat LOCAL_ABSPATH as a versioned directory.
             */
            info.dirAbspath = localAbsPath;
            info.entryName = "";
        } else {
            /*
             * FILE node needs to read the parent directory. Or a DIR node needs
             * to read from the parent to get at the stub entry. Or this is an
             * UNKNOWN node, and we need to examine the parent.
             */
            info.dirAbspath = SVNFileUtil.getParentFile(localAbsPath);
            info.entryName = SVNFileUtil.getBasePath(localAbsPath);
        }
        return info;
    }

    private static class EntryPair {

        public SVNEntry parentEntry;
        public SVNEntry entry;
    }

    private EntryPair readEntryPair(File dirAbspath, String name) throws SVNException {

        EntryPair pair = new EntryPair();

        pair.parentEntry = readOneEntry(dirAbspath, "" /* name */, null /* parent_entry */);

        /*
         * If we need the entry for "this dir", then return the parent_entry in
         * both outputs. Otherwise, read the child node.
         */
        if ("".equals(name)) {
            /*
             * If the retrieved node is a FILE, then we have a problem. We asked
             * for a directory. This implies there is an obstructing,
             * unversioned directory where a FILE should be. We navigated from
             * the obstructing subdir up to the parent dir, then returned the
             * FILE found there.
             *
             * Let's return WC_MISSING cuz the caller thought we had a dir, but
             * that (versioned subdir) isn't there.
             */
            if (pair.parentEntry.getKind() == SVNNodeKind.FILE) {
                pair.parentEntry = null;
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_MISSING, "'{0}' is not a versioned working copy", dirAbspath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }

            pair.entry = pair.parentEntry;
        } else {
            /* Default to not finding the child. */
            pair.entry = null;

            /*
             * Determine whether the parent KNOWS about this child. If it does
             * not, then we should not attempt to look for it.
             *
             * For example: the parent doesn't "know" about the child, but the
             * versioned directory *does* exist on disk. We don't want to look
             * into that subdir.
             */
            for (String child : db.readChildren(dirAbspath)) {
                if (name.equals(child)) {
                    try {
                        pair.entry = readOneEntry(dirAbspath, name, pair.parentEntry);
                        /* Found it. No need to keep searching. */
                        break;
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                            throw e;
                        }
                        /*
                         * No problem. Clear the error and leave the default
                         * value of "missing".
                         */
                    }
                }
            }
            /*
             * if the loop ends without finding a child, then we have the
             * default ENTRY value of NULL.
             */
        }

        return pair;
    }

    private SVNEntry readOneEntry(File dirAbsPath, String name, SVNEntry parentEntry) throws SVNException {

        final File entryAbsPath = (name != null && !"".equals(name)) ? SVNFileUtil.createFilePath(dirAbsPath, name) : dirAbsPath;

        final WCDbInfo info = db.readInfo(entryAbsPath, InfoField.values());

        final SVNEntry17 entry = new SVNEntry17(entryAbsPath);
        entry.setName(name);
        entry.setRevision(info.revision);
        entry.setRepositoryRootURL(info.reposRootUrl);
        entry.setUUID(info.reposUuid);
        entry.setCommittedRevision(info.changedRev);
        entry.setCommittedDate(info.changedDate != null ? info.changedDate.toString() : null);
        entry.setAuthor(info.changedAuthor);
        entry.setTextTime(Long.toString(info.lastModTime));
        entry.setDepth(info.depth);
        entry.setChangelistName(info.changelist);
        entry.setCopyFromRevision(info.originalRevision);

        if (entry.isThisDir()) {
            /* get the tree conflict data. */
            Map<String, SVNConflictDescription> tree_conflicts = null;

            final List<String> conflict_victims = db.readConflictVictims(dirAbsPath);

            for (String child_name : conflict_victims) {

                File child_abspath = SVNFileUtil.createFilePath(dirAbsPath, child_name.toString());

                final List<SVNConflictDescription> child_conflicts = db.readConflicts(child_abspath);

                for (SVNConflictDescription conflict : child_conflicts) {
                    if (conflict.isTreeConflict()) {
                        if (tree_conflicts == null) {
                            tree_conflicts = new HashMap<String, SVNConflictDescription>();
                        }
                        tree_conflicts.put(child_name, conflict);
                    }
                }
            }

            if (tree_conflicts != null) {
                entry.setTreeConflicts(tree_conflicts);
            }
        }

        if (info.status == SVNWCDbStatus.Normal || info.status == SVNWCDbStatus.Incomplete) {
            boolean haveRow = false;

            /*
             * Ugh. During a checkout, it is possible that we are constructing a
             * subdirectory "over" a not-present directory. The read_info() will
             * return information out of the wc.db in the subdir. We need to
             * detect this situation and create a DELETED entry instead.
             */
            if (info.kind == SVNWCDbKind.Dir) {
                SVNSqlJetDb sdb = db.borrowDbTemp(dirAbsPath, SVNWCDbOpenMode.ReadOnly);
                SVNSqlJetStatement stmt = sdb.getStatement(SVNWCDbStatements.SELECT_NOT_PRESENT);
                try {
                    haveRow = stmt.next();
                } finally {
                    stmt.reset();
                }
            }

            if (haveRow) {
                /*
                 * Just like a normal "not-present" node: schedule=normal and
                 * DELETED.
                 */
                entry.setSchedule(null);
                entry.setDeleted(true);
            } else {
                /* Plain old BASE node. */
                entry.setSchedule(null);

                /* Grab inherited repository information, if necessary. */
                if (info.reposRelPath == null) {
                    final WCDbRepositoryInfo baseRepos = db.scanBaseRepository(entryAbsPath, RepositoryInfoField.values());
                    info.reposRelPath = baseRepos.relPath;
                    entry.setRepositoryRootURL(baseRepos.rootUrl);
                    entry.setUUID(baseRepos.uuid);
                }

                entry.setIncomplete(info.status == SVNWCDbStatus.Incomplete);
            }
        } else if (info.status == SVNWCDbStatus.Deleted || info.status == SVNWCDbStatus.ObstructedDelete) {
            /* ### we don't have to worry about moves, so this is a delete. */
            entry.scheduleForDeletion();

            /* ### keep_local ... ugh. hacky. */
            /*
             * We only read keep_local in the directory itself, because we can't
             * rely on the actual record being available in the parent stub when
             * the directory is recorded as deleted in the directory itself.
             * (This last value is the status that brought us in this if block).
             *
             * This is safe because we will only write this flag in the
             * directory itself (see mark_deleted() in adm_ops.c), and also
             * because we will never use keep_local in the final version of
             * WC-NG. With a central db and central pristine store we can remove
             * working copy directories directly. So any left over directories
             * after the delete operation are always kept locally.
             */

            /*
             * If there is still a directory on-disk we keep it, if not it is
             * already deleted. Simple, isn't it?
             *
             * Before single-db we had to keep the administative area alive
             * until after the commit really deletes it. Setting keep alive
             * stopped the commit processing from deleting the directory. We
             * don't delete it any more, so all we have to do is provide some
             * 'sane' value.
             */
            SVNNodeKind pathKind = SVNFileType.getNodeKind(SVNFileType.getType(entryAbsPath));
            entry.setKeepLocal(pathKind == SVNNodeKind.DIR);

        } else if (info.status == SVNWCDbStatus.Added || info.status == SVNWCDbStatus.ObstructedAdd) {
            SVNWCDbStatus workStatus;
            File opRootAbsPath = null;
            File scannedOriginalRelPath;
            long originalRevision = INVALID_REVNUM;

            /* For child nodes, pick up the parent's revision. */
            if (!"".equals(entry.getName())) {
                assert (parentEntry != null);
                assert (!SVNRevision.isValidRevisionNumber(entry.getRevision()));

                entry.setRevision(parentEntry.getRevision());
            }

            if (info.haveBase) {
                SVNWCDbStatus baseStatus;

                /*
                 * ENTRY->REVISION is overloaded. When a node is schedule-add or
                 * -replace, then REVISION refers to the BASE node's revision
                 * that is being overwritten. We need to fetch it now.
                 */

                WCDbBaseInfo baseInfo = db.getBaseInfo(entryAbsPath, BaseInfoField.status, BaseInfoField.revision);
                baseStatus = baseInfo.status;
                entry.setRevision(baseInfo.revision);

                if (baseStatus == SVNWCDbStatus.NotPresent) {
                    /* The underlying node is DELETED in this revision. */
                    entry.setDeleted(true);

                    /* This is an add since there isn't a node to replace. */
                    entry.scheduleForAddition();
                } else {
                    entry.scheduleForReplacement();
                }
            } else {
                /*
                 * If we are reading child directories, then we need to
                 * correctly populate the DELETED flag. WC_DB normally wants to
                 * provide all of a directory's metadata from its own area. But
                 * this information is stored only in the parent directory, so
                 * we need to call a custom API to fetch this value.
                 *
                 * ### we should start generating BASE_NODE rows for THIS_DIR
                 * ### in the subdir. future step because it is harder.
                 */
                if (info.kind == SVNWCDbKind.Dir && !"".equals(entry.getName())) {
                    WCDbDirDeletedInfo deletedInfo = db.isDirDeletedTemp(entryAbsPath);
                    entry.setDeleted(deletedInfo.notPresent);
                    entry.setRevision(deletedInfo.baseRevision);
                }
                if (entry.isDeleted()) {
                    /*
                     * There was a DELETED marker in the parent, meaning that we
                     * truly are shadowing a base node. It isn't called a
                     * 'replace' though (the BASE is pretending not to exist).
                     */
                    entry.scheduleForAddition();
                } else {
                    /*
                     * There was NOT a 'not-present' BASE_NODE in the parent
                     * directory. And there is no BASE_NODE in this directory.
                     * Therefore, we are looking at some kind of add/copy rather
                     * than a replace.
                     */

                    /* ### if this looks like a plain old add, then rev=0. */
                    if (!SVNRevision.isValidRevisionNumber(entry.getCopyFromRevision()) && !SVNRevision.isValidRevisionNumber(entry.getCommittedRevision())) {
                        entry.setRevision(0);
                    }

                    if (info.status == SVNWCDbStatus.ObstructedAdd) {
                        entry.setRevision(INVALID_REVNUM);
                    }

                    /*
                     * ### when we're reading a directory that is not present,
                     * ### then it must be "normal" rather than "add".
                     */
                    if ("".equals(entry.getName()) && info.status == SVNWCDbStatus.ObstructedAdd) {
                        entry.unschedule();
                    } else {
                        entry.scheduleForAddition();
                    }
                }
            }

            /*
             * If we don't have "real" data from the entry (obstruction), then
             * we cannot begin a scan for data. The original node may have
             * important data. Set up stuff to kill that idea off, and finish up
             * this entry.
             */
            if (info.status == SVNWCDbStatus.ObstructedAdd) {
                entry.setCommittedRevision(INVALID_REVNUM);
                workStatus = SVNWCDbStatus.Normal;
                scannedOriginalRelPath = null;
            } else {
                final WCDbAdditionInfo additionInfo = db.scanAddition(entryAbsPath, AdditionInfoField.status, AdditionInfoField.opRootAbsPath, AdditionInfoField.reposRelPath,
                        AdditionInfoField.reposRootUrl, AdditionInfoField.reposUuid, AdditionInfoField.originalReposRelPath, AdditionInfoField.originalRevision);
                workStatus = additionInfo.status;
                opRootAbsPath = additionInfo.opRootAbsPath;
                info.reposRelPath = additionInfo.reposRelPath;
                entry.setRepositoryRootURL(additionInfo.reposRootUrl);
                entry.setUUID(additionInfo.reposUuid);
                scannedOriginalRelPath = additionInfo.originalReposRelPath;
                originalRevision = additionInfo.originalRevision;

                /*
                 * In wc.db we want to keep the valid revision of the
                 * not-present BASE_REV, but when we used entries we set the
                 * revision to 0 when adding a new node over a not present base
                 * node.
                 */
                if (workStatus == SVNWCDbStatus.Added && entry.isDeleted()) {
                    entry.setRevision(0);
                }
            }

            if (!SVNRevision.isValidRevisionNumber(entry.getCommittedRevision()) && scannedOriginalRelPath == null) {
                /*
                 * There is NOT a last-changed revision (last-changed date and
                 * author may be unknown, but we can always check the rev). The
                 * absence of a revision implies this node was added WITHOUT any
                 * history. Avoid the COPIED checks in the else block.
                 */
                /*
                 * ### scan_addition may need to be updated to avoid returning
                 * ### status_copied in this case.
                 */
            } else if (workStatus == SVNWCDbStatus.Copied) {
                entry.setCopied(true);

                /*
                 * If this is a child of a copied subtree, then it should be
                 * schedule_normal.
                 */
                if (info.originalReposRelpath == null) {
                    /* ### what if there is a BASE node under there? */
                    entry.unschedule();
                }

                /*
                 * Copied nodes need to mirror their copyfrom_rev, if they don't
                 * have a revision of their own already.
                 */
                if (!SVNRevision.isValidRevisionNumber(entry.getRevision()) || entry.getRevision() == 0 /* added */)
                    entry.setRevision(originalRevision);
            }

            /* Does this node have copyfrom_* information? */
            if (scannedOriginalRelPath != null) {
                boolean isCopiedChild;
                boolean isMixedRev = false;

                assert (workStatus == SVNWCDbStatus.Copied);

                /*
                 * If this node inherits copyfrom information from an ancestor
                 * node, then it must be a copied child.
                 */
                isCopiedChild = (info.originalReposRelpath == null);

                /*
                 * If this node has copyfrom information on it, then it may be
                 * an actual copy-root, or it could be participating in a
                 * mixed-revision copied tree. So if we don't already know this
                 * is a copied child, then we need to look for this
                 * mixed-revision situation.
                 */
                if (!isCopiedChild) {
                    File parentAbsPath;
                    File parentReposRelPath;
                    SVNURL parentRootUrl;

                    /*
                     * When we insert entries into the database, we will
                     * construct additional copyfrom records for mixed-revision
                     * copies. The old entries would simply record the different
                     * revision in the entry->revision field. That is not
                     * available within wc-ng, so additional copies are made
                     * (see the logic inside write_entry()). However, when
                     * reading these back *out* of the database, the additional
                     * copies look like new "Added" nodes rather than a simple
                     * mixed-rev working copy.
                     *
                     * That would be a behavior change if we did not compensate.
                     * If there is copyfrom information for this node, then the
                     * code below looks at the parent to detect if it *also* has
                     * copyfrom information, and if the copyfrom_url would align
                     * properly. If it *does*, then we omit storing copyfrom_url
                     * and copyfrom_rev (ie. inherit the copyfrom info like a
                     * normal child), and update entry->revision with the
                     * copyfrom_rev in order to (re)create the mixed-rev copied
                     * subtree that was originally presented for storage.
                     */

                    /*
                     * Get the copyfrom information from our parent.
                     *
                     * Note that the parent could be added/copied/moved-here.
                     * There is no way for it to be deleted/moved-away and have
                     * *this* node appear as copied.
                     */
                    parentAbsPath = SVNFileUtil.getParentFile(entryAbsPath);

                    try {

                        final WCDbAdditionInfo additionInfo = db.scanAddition(parentAbsPath, AdditionInfoField.opRootAbsPath, AdditionInfoField.reposRelPath, AdditionInfoField.reposRootUrl);

                        opRootAbsPath = additionInfo.opRootAbsPath;
                        parentReposRelPath = additionInfo.originalReposRelPath;
                        parentRootUrl = additionInfo.originalRootUrl;

                        if (parentRootUrl != null && info.originalRootUrl.equals(parentRootUrl)) {
                            String relpath_to_entry = SVNPathUtil.getRelativePath(SVNPathUtil.validateFilePath(opRootAbsPath.toString()), SVNPathUtil.validateFilePath(entryAbsPath.toString()));
                            String entry_repos_relpath = SVNPathUtil.append(SVNPathUtil.validateFilePath(parentReposRelPath.toString()), relpath_to_entry);

                            /*
                             * The copyfrom repos roots matched.
                             *
                             * Now we look to see if the copyfrom path of the
                             * parent would align with our own path. If so, then
                             * it means this copyfrom was spontaneously created
                             * and inserted for mixed-rev purposes and can be
                             * eliminated without changing the semantics of a
                             * mixed-rev copied subtree.
                             *
                             * See notes/api-errata/wc003.txt for some
                             * additional detail, and potential issues.
                             */
                            if (entry_repos_relpath.equals(info.originalReposRelpath.toString())) {
                                isCopiedChild = true;
                                isMixedRev = true;
                            }
                        }

                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                            throw e;
                        }
                    }

                }

                if (isCopiedChild) {
                    /*
                     * We won't be settig the copyfrom_url, yet need to clear
                     * out the copyfrom_rev. Thus, this node becomes a child of
                     * a copied subtree (rather than its own root).
                     */
                    entry.setCopyFromRevision(INVALID_REVNUM);

                    /*
                     * Children in a copied subtree are schedule normal since we
                     * don't plan to actually *do* anything with them. Their
                     * operation is implied by ancestors.
                     */
                    entry.unschedule();

                    /*
                     * And *finally* we turn this entry into the mixed revision
                     * node that it was intended to be. This node's revision is
                     * taken from the copyfrom record that we spontaneously
                     * constructed.
                     */
                    if (isMixedRev)
                        entry.setRevision(originalRevision);
                } else if (info.originalReposRelpath != null) {
                    entry.setCopyFromURL(SVNPathUtil.append(SVNPathUtil.validateFilePath(info.originalRootUrl.toString()), SVNPathUtil.validateFilePath(info.originalReposRelpath.toString())));
                } else {
                    /*
                     * NOTE: if original_repos_relpath == NULL, then the second
                     * call to scan_addition() will not have occurred. Thus,
                     * this use of OP_ROOT_ABSPATH still contains the original
                     * value where we fetched a value for SCANNED_REPOS_RELPATH.
                     */
                    String relPathToEntry = SVNPathUtil.getRelativePath(SVNPathUtil.validateFilePath(opRootAbsPath.toString()), SVNPathUtil.validateFilePath(entryAbsPath.toString()));
                    String entryReposRelPath = SVNPathUtil.append(SVNPathUtil.validateFilePath(scannedOriginalRelPath.toString()), relPathToEntry);
                    entry.setCopyFromURL(SVNPathUtil.append(info.originalRootUrl.toString(), entryReposRelPath));
                }
            }
        } else if (info.status == SVNWCDbStatus.NotPresent) {
            /*
             * ### buh. 'deleted' nodes are actually supposed to be ### schedule
             * "normal" since we aren't going to actually *do* ### anything to
             * this node at commit time.
             */
            entry.unschedule();
            entry.setDeleted(true);
        } else if (info.status == SVNWCDbStatus.Obstructed) {
            /*
             * ### set some values that should (hopefully) let this directory
             * ### be usable.
             */
            entry.setRevision(INVALID_REVNUM);
        } else if (info.status == SVNWCDbStatus.Absent) {
            entry.setAbsent(true);
        } else if (info.status == SVNWCDbStatus.Excluded) {
            entry.unschedule();
            entry.setDepth(SVNDepth.EXCLUDE);
        } else {
            /* ### we should have handled all possible status values. */
            SVNErrorManager.assertionFailure(false, "MALFUNCTION", SVNLogType.WC);
        }

        /*
         * ### higher levels want repos information about deleted nodes, even
         * ### tho they are not "part of" a repository any more.
         */
        if (entry.isScheduledForDeletion()) {
            final DeletedBaseInfo deletedBaseInfo = getBaseInfoForDeleted(entry, entryAbsPath, parentEntry);
            info.kind = deletedBaseInfo.kind;
            info.reposRelPath = deletedBaseInfo.reposRelPath;
            info.checksum = deletedBaseInfo.checksum;
        }

        /* ### default to the infinite depth if we don't know it. */
        if (entry.getDepth() == SVNDepth.UNKNOWN) {
            entry.setDepth(SVNDepth.INFINITY);
        }

        if (info.kind == SVNWCDbKind.Dir) {
            entry.setKind(SVNNodeKind.DIR);
        } else if (info.kind == SVNWCDbKind.File) {
            entry.setKind(SVNNodeKind.FILE);
        } else if (info.kind == SVNWCDbKind.Symlink) {
            entry.setKind(SVNNodeKind.FILE); /* ### no symlink kind */
        } else {
            entry.setKind(SVNNodeKind.UNKNOWN);
        }

        /*
         * We should always have a REPOS_RELPATH, except for: - deleted nodes -
         * certain obstructed nodes - not-present nodes - absent nodes -
         * excluded nodes
         *
         * ### the last three should probably have an "implied" REPOS_RELPATH
         */
        assert (info.reposRelPath != null || entry.isScheduledForDeletion() || info.status == SVNWCDbStatus.Obstructed || info.status == SVNWCDbStatus.ObstructedAdd
                || info.status == SVNWCDbStatus.ObstructedDelete || info.status == SVNWCDbStatus.NotPresent || info.status == SVNWCDbStatus.Absent || info.status == SVNWCDbStatus.Excluded);
        if (info.reposRelPath != null)
            entry.setURL(SVNPathUtil.append(entry.getRepositoryRoot(), info.reposRelPath.toString()));

        if (info.checksum != null) {
            /*
             * SVN_EXPERIMENTAL_PRISTINE: If we got a SHA-1, get the
             * corresponding MD-5.
             */
            if (info.checksum.getKind() != SVNChecksumKind.MD5) {
                info.checksum = db.getPristineMD5(entryAbsPath, info.checksum);
            }

            assert (info.checksum.getKind() == SVNChecksumKind.MD5);
            entry.setChecksum(info.checksum.toString());
        }

        if (info.conflicted) {

            final List<SVNConflictDescription> conflicts = db.readConflicts(entryAbsPath);

            for (SVNConflictDescription cd : conflicts) {

                final SVNMergeFileSet cdf = cd.getMergeFiles();
                if (cd.isTextConflict()) {
                    entry.setConflictOld(cdf.getBasePath());
                    entry.setConflictNew(cdf.getRepositoryPath());
                    entry.setConflictWorking(cdf.getLocalPath());
                } else if (cd.isPropertyConflict()) {
                    entry.setPropRejectFile(cdf.getRepositoryPath());
                }
            }
        }

        if (info.lock != null) {
            entry.setLockToken(info.lock.token);
            entry.setLockOwner(info.lock.owner);
            entry.setLockComment(info.lock.comment);
            entry.setLockCreationDate(info.lock.date != null ? info.lock.date.toString() : null);
        }

        /*
         * Let's check for a file external. ### right now this is ugly, since we
         * have no good way querying ### for a file external OR retrieving
         * properties. ugh.
         */
        if (entry.getKind() == SVNNodeKind.FILE)
            checkFileExternal(entry, entryAbsPath);

        entry.setWorkingSize(info.translatedSize);

        return entry;

    }

    private void checkFileExternal(SVNEntry17 entry, File localAbsPath) throws SVNException {
        final String serialized = db.getFileExternalTemp(localAbsPath);
        if (serialized != null) {
            final UnserializedFileExternalInfo info = unserializeFileExternal(serialized);
            entry.setExternalFilePath(info.path);
            entry.setExternalFilePegRevision(info.pegRevision);
            entry.setExternalFileRevision(info.revision);
        }
    }

    private UnserializedFileExternalInfo unserializeFileExternal(String str) throws SVNException {
        final UnserializedFileExternalInfo info = new UnserializedFileExternalInfo();
        if (str != null) {
            StringBuffer buffer = new StringBuffer(str);
            info.pegRevision = SVNAdminUtil.parseRevision(buffer);
            info.revision = SVNAdminUtil.parseRevision(buffer);
            info.path = buffer.toString();
        }
        return info;
    }

    private class UnserializedFileExternalInfo {

        public String path = null;
        public SVNRevision pegRevision = SVNRevision.UNDEFINED;
        public SVNRevision revision = SVNRevision.UNDEFINED;
    }

    private class DeletedBaseInfo {

        public SVNWCDbKind kind;
        public File reposRelPath;
        public SVNChecksum checksum;
    }

    private DeletedBaseInfo getBaseInfoForDeleted(SVNEntry17 entry, File entryAbsPath, SVNEntry parentEntry) throws SVNException {

        DeletedBaseInfo resInfo = new DeletedBaseInfo();

        SVNException err = null;

        try {

            /* Get the information from the underlying BASE node. */

            final WCDbBaseInfo baseInfo = db.getBaseInfo(entryAbsPath, BaseInfoField.kind, BaseInfoField.revision, BaseInfoField.changedRev, BaseInfoField.changedDate, BaseInfoField.changedAuthor,
                    BaseInfoField.lastModTime, BaseInfoField.depth, BaseInfoField.checksum, BaseInfoField.translatedSize);

            resInfo.kind = baseInfo.kind;
            resInfo.checksum = baseInfo.checksum;
            entry.setRevision(baseInfo.revision);
            entry.setCommittedRevision(baseInfo.changedRev);
            entry.setCommittedDate(baseInfo.changedDate == null ? null : baseInfo.changedDate.toString());
            entry.setAuthor(baseInfo.changedAuthor);
            entry.setTextTime(baseInfo.lastModTime == null ? null : baseInfo.lastModTime.toString());
            entry.setDepth(baseInfo.depth);
            entry.setWorkingSize(baseInfo.translatedSize);

        } catch (SVNException e) {
            err = e;
        }

        /*
         * SVN_EXPERIMENTAL_PRISTINE:checksum is originally MD-5 but will later
         * be SHA-1. That's OK here - we are just returning what is stored.
         */

        if (err != null) {
            if (err.getErrorMessage().getErrorCode() != SVNErrorCode.WC_PATH_NOT_FOUND) {
                throw err;
            }

            /*
             * No base node? This is a deleted child of a copy/move-here, so we
             * need to scan up the WORKING tree to find the root of the
             * deletion. Then examine its parent to discover its future location
             * in the repository.
             */

            final WCDbDeletionInfo deletionInfo = db.scanDeletion(entryAbsPath, DeletionInfoField.workDelAbsPath);

            assert (deletionInfo.workDelAbsPath != null);
            final File parent_abspath = SVNFileUtil.getParentFile(deletionInfo.workDelAbsPath);

            /*
             * During post-commit the parent that was previously added may have
             * been moved from the WORKING tree to the BASE tree.
             */

            final WCDbInfo parentInfo = db.readInfo(parent_abspath, InfoField.status, InfoField.reposRelPath, InfoField.reposRootUrl, InfoField.reposUuid);
            File parentReposRelPath = parentInfo.reposRelPath;
            entry.setRepositoryRootURL(parentInfo.reposRootUrl);
            entry.setUUID(parentInfo.reposUuid);

            if (parentInfo.status == SVNWCDbStatus.Added || parentInfo.status == SVNWCDbStatus.ObstructedAdd) {
                final WCDbAdditionInfo additionInfo = db.scanAddition(parent_abspath, AdditionInfoField.reposRelPath, AdditionInfoField.reposRootUrl, AdditionInfoField.reposUuid);
                parentReposRelPath = additionInfo.reposRelPath;
                entry.setRepositoryRootURL(additionInfo.reposRootUrl);
                entry.setUUID(additionInfo.reposUuid);
            }

            /* Now glue it all together */
            resInfo.reposRelPath = SVNFileUtil.createFilePath(parentReposRelPath,
                    SVNPathUtil.getRelativePath(SVNPathUtil.validateFilePath(parent_abspath.toString()), SVNPathUtil.validateFilePath(entryAbsPath.toString())));
        } else {
            final WCDbRepositoryInfo baseReposInfo = db.scanBaseRepository(entryAbsPath, RepositoryInfoField.values());
            resInfo.reposRelPath = baseReposInfo.relPath;
            entry.setRepositoryRootURL(baseReposInfo.rootUrl);
            entry.setUUID(baseReposInfo.uuid);
        }

        /* Do some extra work for the child nodes. */
        if (parentEntry != null) {
            /*
             * For child nodes without a revision, pick up the parent's
             * revision.
             */
            if (!SVNRevision.isValidRevisionNumber(entry.getRevision()))
                entry.setRevision(parentEntry.getRevision());
        }

        /*
         * For deleted nodes, our COPIED flag has a rather complex meaning.
         *
         * In general, COPIED means "an operation on an ancestor took care of
         * me." This typically refers to a copy of an ancestor (and this node
         * just came along for the ride). However, in certain situations the
         * COPIED flag is set for deleted nodes.
         *
         * First off, COPIED will *never* be set for nodes/subtrees that are
         * simply deleted. The deleted node/subtree *must* be under an ancestor
         * that has been copied. Plain additions do not count; only copies
         * (add-with-history).
         *
         * The basic algorithm to determine whether we live within a copied
         * subtree is as follows:
         *
         * 1) find the root of the deletion operation that affected us (we may
         * be that root, or an ancestor was deleted and took us with it)
         *
         * 2) look at the root's *parent* and determine whether that was a copy
         * or a simple add.
         *
         * It would appear that we would be done at this point. Once we
         * determine that the parent was copied, then we could just set the
         * COPIED flag.
         *
         * Not so fast. Back to the general concept of "an ancestor operation
         * took care of me." Further consider two possibilities:
         *
         * 1) this node is scheduled for deletion from the copied subtree, so at
         * commit time, we copy then delete
         *
         * 2) this node is scheduled for deletion because a subtree was deleted
         * and then a copied subtree was added (causing a replacement). at
         * commit time, we delete a subtree, and then copy a subtree. we do not
         * need to specifically touch this node -- all operations occur on
         * ancestors.
         *
         * Given the "ancestor operation" concept, then in case (1) we must
         * *clear* the COPIED flag since we'll have more work to do. In case
         * (2), we *set* the COPIED flag to indicate that no real work is going
         * to happen on this node.
         *
         * Great fun. And just maybe the code reading the entries has no bugs in
         * interpreting that gobbledygook... but that *is* the expectation of
         * the code. Sigh.
         *
         * We can get a little bit of shortcut here if THIS_DIR is also schduled
         * for deletion.
         */
        if (parentEntry != null && parentEntry.isScheduledForDeletion()) {
            /*
             * ### not entirely sure that we can rely on the parent. for ###
             * example, what if we are a deletion of a BASE node, but ### the
             * parent is a deletion of a copied subtree? sigh.
             */

            /* Child nodes simply inherit the parent's COPIED flag. */
            entry.setCopied(parentEntry.isCopied());
        } else {

            /* Find out details of our deletion. */
            final WCDbDeletionInfo deletionInfo = db.scanDeletion(entryAbsPath, DeletionInfoField.baseReplaced, DeletionInfoField.workDelAbsPath);

            /*
             * If there is no deletion in the WORKING tree, then the node is a
             * child of a simple explicit deletion of the BASE tree. It
             * certainly isn't copied. If we *do* find a deletion in the WORKING
             * tree, then we need to discover information about the parent.
             */
            if (deletionInfo.workDelAbsPath != null) {
                File parentAbsPath;
                SVNWCDbStatus parentStatus;

                /*
                 * The parent is in WORKING except during post-commit when it
                 * may have been moved from the WORKING tree to the BASE tree.
                 */
                parentAbsPath = SVNFileUtil.getParentFile(deletionInfo.workDelAbsPath);
                parentStatus = db.readInfo(parentAbsPath, InfoField.status).status;

                if (parentStatus == SVNWCDbStatus.Added || parentStatus == SVNWCDbStatus.ObstructedAdd) {
                    parentStatus = db.scanAddition(parentAbsPath, AdditionInfoField.status).status;

                }
                if (parentStatus == SVNWCDbStatus.Copied || parentStatus == SVNWCDbStatus.MovedHere || parentStatus == SVNWCDbStatus.Normal) {
                    /*
                     * The parent is copied/moved here, so WORK_DEL_ABSPATH is
                     * the root of a deleted subtree. Our COPIED status is now
                     * dependent upon whether the copied root is replacing a
                     * BASE tree or not.
                     *
                     * But: if we are schedule-delete as a result of being a
                     * copied DELETED node, then *always* mark COPIED. Normal
                     * copies have cmt_* data; copied DELETED nodes are missing
                     * this info.
                     *
                     * Note: MOVED_HERE is a concept foreign to this old
                     * interface, but it is best represented as if a copy had
                     * occurred, so we'll model it that way to old clients.
                     *
                     * Note: svn_wc__db_status_normal corresponds to the
                     * post-commit parent that was copied or moved in WORKING
                     * but has now been converted to BASE.
                     */
                    if (SVNRevision.isValidRevisionNumber(entry.getCommittedRevision())) {
                        /*
                         * The scan_deletion call will tell us if there was an
                         * explicit move-away of an ancestor (which also means a
                         * replacement has occurred since there is a WORKING
                         * tree that isn't simply BASE deletions). The call will
                         * also tell us if there was an implicit deletion caused
                         * by a replacement. All stored in BASE_REPLACED.
                         */
                        entry.setCopied(deletionInfo.baseReplaced);
                    } else {
                        entry.setCopied(true);
                    }
                } else {
                    assert (parentStatus == SVNWCDbStatus.Added);

                    /*
                     * Whoops. WORK_DEL_ABSPATH is scheduled for deletion, yet
                     * the parent is scheduled for a plain addition. This can
                     * occur when a subtree is deleted, and then nodes are added
                     * *later*. Since the parent is a simple add, then nothing
                     * has been copied. Nothing more to do.
                     *
                     * Note: if a subtree is added, *then* deletions are made,
                     * the nodes should simply be removed from version control.
                     */
                }
            }
        }

        return resInfo;
    }

    public SVNURL getUrlFromPath(File localAbsPath) throws SVNException {
        return getEntryLocation(localAbsPath, SVNRevision.UNDEFINED, false).url;
    }

    public static class EntryLocationInfo {

        public SVNURL url;
        public long revNum;
    }

    public EntryLocationInfo getEntryLocation(File localAbsPath, SVNRevision pegRevisionKind, boolean fetchRevnum) throws SVNException {
        /*
         * This function doesn't contact the repository, so error out if asked
         * to do so.
         */
        // TODO what is svn_opt_revision_date in our code ? (sergey)
        if (/* pegRevisionKind == svn_opt_revision_date || */
        pegRevisionKind == SVNRevision.HEAD) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
            SVNErrorManager.error(err, SVNLogType.WC);
            return null;
        }

        final NodeCopyFromInfo copyFrom = getNodeCopyFromInfo(localAbsPath, NodeCopyFromField.url, NodeCopyFromField.rev);

        final EntryLocationInfo result = new EntryLocationInfo();

        if (copyFrom.url != null && pegRevisionKind == SVNRevision.WORKING) {
            result.url = copyFrom.url;
            if (fetchRevnum)
                result.revNum = copyFrom.rev;
        } else {
            final SVNURL node_url = getNodeUrl(localAbsPath);

            if (node_url != null) {
                result.url = node_url;
                if (fetchRevnum) {
                    if ((pegRevisionKind == SVNRevision.COMMITTED) || (pegRevisionKind == SVNRevision.PREVIOUS)) {
                        result.revNum = getNodeChangedInfo(localAbsPath).changedRev;
                        if (pegRevisionKind == SVNRevision.PREVIOUS)
                            result.revNum = result.revNum - 1;
                    } else {
                        /*
                         * Local modifications are not relevant here, so
                         * consider svn_opt_revision_unspecified,
                         * svn_opt_revision_number, svn_opt_revision_base, and
                         * svn_opt_revision_working as the same.
                         */
                        result.revNum = getNodeBaseRev(localAbsPath);
                    }
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.ENTRY_MISSING_URL, "Entry for ''{0}'' has no URL", localAbsPath);
                SVNErrorManager.error(err, SVNLogType.WC);
            }
        }

        return result;
    }

    public enum NodeCopyFromField {
        rootUrl, reposRelPath, url, rev, isCopyTarget;
    }

    public static class NodeCopyFromInfo {

        public SVNURL rootUrl = null;
        public File reposRelPath = null;
        public SVNURL url = null;
        public long rev = INVALID_REVNUM;
        public boolean isCopyTarget = false;
    }

    public NodeCopyFromInfo getNodeCopyFromInfo(File localAbsPath, NodeCopyFromField... fields) throws SVNException {
        final EnumSet<NodeCopyFromField> f = SVNWCDb.getInfoFields(NodeCopyFromField.class, fields);

        final WCDbInfo readInfo = db.readInfo(localAbsPath, InfoField.status, InfoField.originalReposRelpath, InfoField.originalRootUrl, InfoField.originalRevision);
        SVNURL original_root_url = readInfo.originalRootUrl;
        File original_repos_relpath = readInfo.originalReposRelpath;
        long original_revision = readInfo.originalRevision;
        SVNWCDbStatus status = readInfo.status;

        final NodeCopyFromInfo copyFrom = new NodeCopyFromInfo();

        if (original_root_url != null && original_repos_relpath != null) {
            /*
             * If this was the root of the copy then the URL is immediately
             * available...
             */
            SVNURL my_copyfrom_url = null;

            if (f.contains(NodeCopyFromField.url) || f.contains(NodeCopyFromField.isCopyTarget))
                my_copyfrom_url = SVNURL.parseURIEncoded(SVNPathUtil.append(original_root_url.toString(), original_repos_relpath.toString()));

            if (f.contains(NodeCopyFromField.rootUrl))
                copyFrom.rootUrl = original_root_url;
            if (f.contains(NodeCopyFromField.reposRelPath))
                copyFrom.reposRelPath = original_repos_relpath;
            if (f.contains(NodeCopyFromField.url))
                copyFrom.url = my_copyfrom_url;

            if (f.contains(NodeCopyFromField.rev))
                copyFrom.rev = original_revision;

            if (f.contains(NodeCopyFromField.isCopyTarget)) {
                /*
                 * ### At this point we'd just set is_copy_target to TRUE, *but*
                 * we currently want to model wc-1 behaviour. Particularly, this
                 * affects mixed-revision copies (e.g. wc-wc copy): - Wc-1 saw
                 * only the root of a mixed-revision copy as the copy's root. -
                 * Wc-ng returns an explicit original_root_url,
                 * original_repos_relpath pair for each subtree with mismatching
                 * revision. We need to compensate for that: Find out if the
                 * parent of this node is also copied and has a matching
                 * copy_from URL. If so, nevermind the revision, just like wc-1
                 * did, and say this was not a separate copy target.
                 */
                final File parent_abspath = SVNFileUtil.getFileDir(localAbsPath);
                final String base_name = SVNFileUtil.getFileName(localAbsPath);

                /*
                 * This is a copied node, so we should never fall off the top of
                 * a working copy here.
                 */
                final SVNURL parent_copyfrom_url = getNodeCopyFromInfo(parent_abspath, NodeCopyFromField.url).url;

                /*
                 * So, count this as a separate copy target only if the URLs
                 * don't match up, or if the parent isn't copied at all.
                 */
                if (parent_copyfrom_url == null || !SVNURL.parseURIEncoded(SVNPathUtil.append(parent_copyfrom_url.toString(), base_name)).equals(my_copyfrom_url)) {
                    copyFrom.isCopyTarget = true;
                }
            }
        } else if ((status == SVNWCDbStatus.Added || status == SVNWCDbStatus.ObstructedAdd)
                && (f.contains(NodeCopyFromField.rev) || f.contains(NodeCopyFromField.url) || f.contains(NodeCopyFromField.rootUrl) || f.contains(NodeCopyFromField.reposRelPath))) {
            /*
             * ...But if this is merely the descendant of an explicitly
             * copied/moved directory, we need to do a bit more work to
             * determine copyfrom_url and copyfrom_rev.
             */

            final WCDbAdditionInfo scanAddition = db.scanAddition(localAbsPath, AdditionInfoField.status, AdditionInfoField.opRootAbsPath, AdditionInfoField.originalReposRelPath,
                    AdditionInfoField.originalRootUrl, AdditionInfoField.originalRevision);
            final File op_root_abspath = scanAddition.opRootAbsPath;
            status = scanAddition.status;
            original_repos_relpath = scanAddition.originalReposRelPath;
            original_root_url = scanAddition.originalRootUrl;
            original_revision = scanAddition.originalRevision;

            if (status == SVNWCDbStatus.Copied || status == SVNWCDbStatus.MovedHere) {
                SVNURL src_parent_url = SVNURL.parseURIEncoded(SVNPathUtil.append(original_root_url.toString(), original_repos_relpath.toString()));
                String src_relpath = SVNPathUtil.getPathAsChild(op_root_abspath.toString(), localAbsPath.toString());

                if (src_relpath != null) {
                    if (f.contains(NodeCopyFromField.rootUrl))
                        copyFrom.rootUrl = original_root_url;
                    if (f.contains(NodeCopyFromField.reposRelPath))
                        copyFrom.reposRelPath = new File(original_repos_relpath, src_relpath);
                    if (f.contains(NodeCopyFromField.url))
                        copyFrom.url = SVNURL.parseURIEncoded(SVNPathUtil.append(src_parent_url.toString(), src_relpath.toString()));
                    if (f.contains(NodeCopyFromField.rev))
                        copyFrom.rev = original_revision;
                }
            }
        }

        return copyFrom;
    }

    public static String getPathAsChild(File parent, File child) {
        if (parent == null || child == null)
            return null;
        if (parent.equals(child))
            return null;
        final String parentPath = parent.toString();
        final String childPath = child.toString();
        if (!childPath.startsWith(parentPath))
            return null;
        final String restPath = childPath.substring(parentPath.length());
        if (restPath.startsWith(File.separator)) {
            return restPath.substring(1);
        }
        return restPath;
    }

    public static boolean isAncestor(File parent, File child) {
        if (parent == null || child == null)
            return false;
        if (parent.equals(child))
            return false;
        final String parentPath = parent.toString();
        final String childPath = child.toString();
        return childPath.startsWith(parentPath);
    }

    public static boolean isErrorAccess(SVNException e) {
        final SVNErrorCode errorCode = e.getErrorMessage().getErrorCode();
        return errorCode == SVNErrorCode.FS_NOT_FOUND || errorCode == SVNErrorCode.IO_ERROR;
    }

    public boolean isPropsModified(File localAbspath) throws SVNException {
        return db.readInfo(localAbspath, InfoField.propsMod).propsMod;
    }

    public interface ISVNWCNodeHandler {

        void nodeFound(File localAbspath) throws SVNException;
    }

    public void nodeWalkChildren(File localAbspath, ISVNWCNodeHandler nodeHandler, boolean showHidden, SVNDepth walkDepth) throws SVNException {
        assert (walkDepth != null && walkDepth.getId() >= SVNDepth.EMPTY.getId() && walkDepth.getId() <= SVNDepth.INFINITY.getId());
        WCDbInfo readInfo = db.readInfo(localAbspath, InfoField.status, InfoField.kind);
        SVNWCDbKind kind = readInfo.kind;
        SVNWCDbStatus status = readInfo.status;
        nodeHandler.nodeFound(localAbspath);
        if (kind == SVNWCDbKind.File || status == SVNWCDbStatus.NotPresent || status == SVNWCDbStatus.Excluded || status == SVNWCDbStatus.Absent)
            return;
        if (kind == SVNWCDbKind.Dir) {
            walkerHelper(localAbspath, nodeHandler, showHidden, walkDepth);
            return;
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.NODE_UNKNOWN_KIND, "''{0}'' has an unrecognized node kind", localAbspath);
        SVNErrorManager.error(err, SVNLogType.WC);
    }

    private void walkerHelper(File dirAbspath, ISVNWCNodeHandler nodeHandler, boolean showHidden, SVNDepth depth) throws SVNException {
        if (depth == SVNDepth.EMPTY)
            return;
        final List<String> relChildren = db.readChildren(dirAbspath);
        for (final String child : relChildren) {
            checkCancelled();
            File childAbspath = SVNFileUtil.createFilePath(dirAbspath, child);
            WCDbInfo childInfo = db.readInfo(childAbspath, InfoField.status, InfoField.kind);
            SVNWCDbStatus childStatus = childInfo.status;
            SVNWCDbKind childKind = childInfo.kind;
            if (!showHidden)
                switch (childStatus) {
                    case NotPresent:
                    case Absent:
                    case Excluded:
                        continue;
                    default:
                        break;
                }
            if (childKind == SVNWCDbKind.File || depth.getId() >= SVNDepth.IMMEDIATES.getId()) {
                nodeHandler.nodeFound(childAbspath);
            }
            if (childKind == SVNWCDbKind.Dir && depth.getId() >= SVNDepth.IMMEDIATES.getId()) {
                SVNDepth depth_below_here = depth;

                if (depth.getId() == SVNDepth.IMMEDIATES.getId())
                    depth_below_here = SVNDepth.EMPTY;
                walkerHelper(childAbspath, nodeHandler, showHidden, depth_below_here);
            }
        }
    }

    public File acquireWriteLock(File localAbspath, boolean lockAnchor) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void releaseWriteLock(File localAbspath) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void exclude(File localAbspath) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void cropTree(File localAbspath, SVNDepth depth) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public class SVNWCNodeReposInfo {

        public SVNURL reposRootUrl;
        public String reposUuid;
    }

    public SVNWCNodeReposInfo getNodeReposInfo(File baseDirAbspath, boolean scanAdded, boolean scanDeleted) throws SVNException {
        // TODO
        throw new UnsupportedOperationException();
    }

    public SVNSkel wqBuildBaseRemove(File localAbspath, boolean b) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void wqAdd(File dir_abspath, SVNSkel workItem) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public void wqRun(File dir_abspath) {
        // TODO
        throw new UnsupportedOperationException();
    }

}
