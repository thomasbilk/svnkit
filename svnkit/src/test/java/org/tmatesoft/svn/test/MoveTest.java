package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MoveTest {

    @Ignore("SVNKIT-295")
    @Test
    public void testMoveFileOutOfVersionControl() throws Exception {
        //SVNKIT-295
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMoveFileOutOfVersionControl", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File file = workingCopy.getFile("file");

            final File unversionedDirectory = workingCopy.getFile("unversionedDirectory");
            final File targetFile = new File(unversionedDirectory, "file");

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNMoveClient moveClient = clientManager.getMoveClient();
                moveClient.doMove(file, targetFile);

                final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
                Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
                Assert.assertEquals(SVNStatusType.STATUS_UNVERSIONED, statuses.get(unversionedDirectory).getNodeStatus());
                Assert.assertNull(statuses.get(targetFile));
            } finally {
                clientManager.dispose();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRenamedDirectoryWithMovedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRenamedDirectoryWithMovedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);

            final File directory = workingCopy.getFile("directory");
            final File file = new File(directory, "file");

            final File movedDirectory = workingCopy.getFile("movedDirectory");
            final File movedFile = new File(directory, "movedFile");

            final SvnCopy moveFile = svnOperationFactory.createCopy();
            moveFile.addCopySource(SvnCopySource.create(SvnTarget.fromFile(file), SVNRevision.WORKING));
            moveFile.setSingleTarget(SvnTarget.fromFile(movedFile));
            moveFile.setMove(true);
            moveFile.run();

            final SvnCopy moveDirectory = svnOperationFactory.createCopy();
            moveDirectory.addCopySource(SvnCopySource.create(SvnTarget.fromFile(directory), SVNRevision.WORKING));
            moveDirectory.setSingleTarget(SvnTarget.fromFile(movedDirectory));
            moveDirectory.setMove(true);
            moveDirectory.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertTrue(statuses.get(movedDirectory).isCopied());
            Assert.assertNotNull(statuses.get(movedDirectory).getCopyFromUrl());

            Assert.assertTrue(statuses.get(new File(movedDirectory, "movedFile")).isCopied());
            Assert.assertNotNull(statuses.get(new File(movedDirectory, "movedFile")).getCopyFromUrl());

            Assert.assertTrue(statuses.get(new File(movedDirectory, "file")).isCopied());
            Assert.assertNull(statuses.get(new File(movedDirectory, "file")).getCopyFromUrl());

            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(movedDirectory).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_ADDED, statuses.get(new File(movedDirectory, "movedFile")).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(new File(movedDirectory, "file")).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(file).getNodeStatus());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(directory).getNodeStatus());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMoveAddedDirectoryWithPropertiesBetweenWorkingCopies() throws Exception {
        //SVNKIT-333
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMoveAddedDirectoryWithPropertiesBetweenWorkingCopies", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final WorkingCopy workingCopy1 = sandbox.checkoutNewWorkingCopy(url);
            final WorkingCopy workingCopy2 = sandbox.checkoutNewWorkingCopy(url);

            final File sourceDirectory = workingCopy1.getFile("sourceDirectory");
            SVNFileUtil.ensureDirectoryExists(sourceDirectory);
            workingCopy1.add(sourceDirectory);
            workingCopy1.setProperty(sourceDirectory, "propertyName", SVNPropertyValue.create("propertyValue"));

            final File targetDirectory = workingCopy2.getFile("targetDirectory");

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNMoveClient moveClient = clientManager.getMoveClient();
                moveClient.doMove(sourceDirectory, targetDirectory);
            } finally {
                clientManager.dispose();
            }

            final SvnGetProperties getProperties = svnOperationFactory.createGetProperties();
            getProperties.setSingleTarget(SvnTarget.fromFile(targetDirectory));
            final SVNProperties properties = getProperties.run();

            Assert.assertNotNull(properties);
            final SVNPropertyValue propertyValue = properties.getSVNPropertyValue("propertyName");
            Assert.assertEquals("propertyValue", SVNPropertyValue.getPropertyAsString(propertyValue));

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMovedToAndMovedFrom() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMovedToAndMovedFrom", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File sourceFile = workingCopy.getFile("sourceFile");
            final File targetFile = workingCopy.getFile("targetFile");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setMove(true);
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceFile), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(targetFile));
            copy.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(sourceFile, statuses.get(targetFile).getMovedFromPath());
            Assert.assertNull(statuses.get(targetFile).getMovedToPath());
            Assert.assertEquals(targetFile, statuses.get(sourceFile).getMovedToPath());
            Assert.assertNull(statuses.get(sourceFile).getMovedFromPath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDoubleLockingOnMove() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDoubleLockingOnMove", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/sourceDirectory/source");
            commitBuilder.addDirectory("directory/targetDirectory");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File source = workingCopy.getFile("directory/sourceDirectory/source");
            final File target = workingCopy.getFile("directory/targetDirectory/target");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.setMove(true);
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(source), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(target));
            copy.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(target, statuses.get(source).getMovedToPath());
            Assert.assertEquals(source, statuses.get(target).getMovedFromPath());

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMoveBackMovedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMoveBackMovedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("source");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File source = workingCopy.getFile("source");
            final File target = workingCopy.getFile("target");

            final SvnCopy move = svnOperationFactory.createCopy();
            move.setMove(true);
            move.addCopySource(SvnCopySource.create(SvnTarget.fromFile(source), SVNRevision.WORKING));
            move.setSingleTarget(SvnTarget.fromFile(target));
            move.run();

            final SvnCopy moveBack = svnOperationFactory.createCopy();
            moveBack.setMove(true);
            moveBack.addCopySource(SvnCopySource.create(SvnTarget.fromFile(target), SVNRevision.WORKING));
            moveBack.setSingleTarget(SvnTarget.fromFile(source));
            moveBack.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(source).getNodeStatus());
            Assert.assertNull(statuses.get(target));

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMoveAndCommitDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        Assume.assumeTrue(TestUtil.areAllSvnserveOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMoveAndCommitDirectory", options);
        try {
            final BasicAuthenticationManager authenticationManager = new BasicAuthenticationManager("user", "password");
            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user", "password");
            final SVNURL url = sandbox.createSvnRepositoryWithSvnAccess(loginToPassword);

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.setAuthenticationManager(authenticationManager);
            commitBuilder.addFile("source/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File source = workingCopy.getFile("source");
            final File target = workingCopy.getFile("target");

            final SvnCopy move = svnOperationFactory.createCopy();
            move.setMove(true);
            move.addCopySource(SvnCopySource.create(SvnTarget.fromFile(source), SVNRevision.WORKING));
            move.setSingleTarget(SvnTarget.fromFile(target));
            move.setFailWhenDstExists(false);
            move.setMakeParents(false);
            move.run();

            final SvnCommit commit = svnOperationFactory.createCommit();
            commit.addTarget(SvnTarget.fromFile(source));
            commit.addTarget(SvnTarget.fromFile(target));
            commit.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(SVNStatusType.STATUS_NORMAL, statuses.get(target).getNodeStatus());
            Assert.assertNull(statuses.get(source));

        }
        finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMoveMetadataOnly() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMoveMetadataOnly", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("sourceFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File sourceFile = workingCopy.getFile("sourceFile");
            final File targetFile = workingCopy.getFile("targetFile");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceFile), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(targetFile));
            copy.setMove(true);
            copy.setMetadataOnly(true);
            copy.run();

            final Map<File, SvnStatus> statuses = TestUtil.getStatuses(svnOperationFactory, workingCopy.getWorkingCopyDirectory());
            Assert.assertEquals(SVNStatusType.STATUS_DELETED, statuses.get(sourceFile).getNodeStatus());
            Assert.assertEquals(targetFile, statuses.get(sourceFile).getMovedToPath());
            Assert.assertEquals(SVNStatusType.STATUS_MISSING, statuses.get(targetFile).getNodeStatus());
            Assert.assertNull(statuses.get(targetFile).getMovedToPath());
        }
        finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMixedRevisionMoveForbidden() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMixedRevisionMoveForbidden", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file1");
            commitBuilder1.addFile("directory/file2");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/file1", "changed".getBytes());
            commitBuilder2.changeFile("directory/file2", "changed".getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);
            final File directory = workingCopy.getFile("directory");
            final File movedDirectory = workingCopy.getFile("movedDirectory");

            final File file1 = workingCopy.getFile("directory/file1");
            final File file2 = workingCopy.getFile("directory/file2");

            final SvnUpdate update = svnOperationFactory.createUpdate();
            update.setSingleTarget(SvnTarget.fromFile(file2));
            update.setRevision(SVNRevision.create(1));
            update.run();

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(directory), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(movedDirectory));
            copy.setMove(true);
            copy.setAllowMixedRevisions(false);
            try {
                copy.run();
                Assert.fail("An exception should be thrown");
            } catch (SVNException e) {
                //expected
                Assert.assertEquals(SVNErrorCode.WC_MIXED_REVISIONS, e.getErrorMessage().getErrorCode());
            }
        }
        finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMoveReplaced() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMoveReplaced", options);

        try {
            final SVNURL url = sandbox.createSvnRepository();
            CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("normal.txt");
            commitBuilder.addFile("replaced.txt");
            commitBuilder.commit();

            WorkingCopy wc1 = sandbox.checkoutNewWorkingCopy(url);
            File normalFile = wc1.getFile("normal.txt");
            File replacedFile = wc1.getFile("replaced.txt");
            File moveToFile = wc1.getFile("moveTo.txt");

            SVNClientManager scm = SVNClientManager.newInstance();

            // Remove from version control...
            scm.getWCClient().doDelete(replacedFile, true, true, false);
            // ... copy another one instead
            scm.getCopyClient().doCopy(
                    new SVNCopySource[] { new SVNCopySource(SVNRevision.WORKING, SVNRevision.WORKING, normalFile) },
                    replacedFile,
                    false,
                    false,
                    true);
            SVNURL originalURL = scm.getStatusClient().doStatus(normalFile, false).getURL();

            SVNStatus status = scm.getStatusClient().doStatus(replacedFile, false);
            Assert.assertEquals(SVNStatusType.STATUS_REPLACED, status.getCombinedNodeAndContentsStatus());
            Assert.assertTrue(status.isCopied());
            Assert.assertEquals(originalURL.toString(), status.getCopyFromURL().toString());

            // Move the replacing item to a different location
            scm.getCopyClient().doCopy(
                    new SVNCopySource[] { new SVNCopySource(SVNRevision.WORKING, SVNRevision.WORKING, replacedFile) },
                    moveToFile,
                    true,
                    false,
                    true);

            status = scm.getStatusClient().doStatus(moveToFile, false);
            Assert.assertEquals(originalURL.toString(), status.getCopyFromURL().toString());
        } finally {
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "MoveTest";
    }
}
