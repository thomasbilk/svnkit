package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.util.Iterator;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNCopyDriver;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNLog;
import org.tmatesoft.svn.core.internal.wc.admin.SVNVersionedProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.internal.wc16.*;

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
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNMoveClient17 extends SVNCopyDriver {
	private SVNWCClient17 myWCClient;
	private SVNCopyClient17 myCopyClient;

	/** 
	 * Constructs and initializes an <b>SVNMoveClient</b> object
	 * with the specified run-time configuration and authentication 
	 * drivers.
	 * <p>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNMoveClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the 
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
	 * <p>
	 * If <code>authManager</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNMoveClient</b> will be using a default authentication
	 * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
	 * which uses server-side settings and auth storage from the 
	 * default SVN's run-time configuration area (or system properties
	 * if that area is not found).
	 * @param authManager an authentication and network layers driver
	 * @param options     a run-time configuration options driver     
	 */
	public SVNMoveClient17(ISVNAuthenticationManager authManager,
			ISVNOptions options) {
		super(authManager, options);
		myWCClient = new SVNWCClient17(authManager, options);
		myCopyClient = new SVNCopyClient17(authManager, options);
	}

	/** 
	 * Constructs and initializes an <b>SVNMoveClient</b> object
	 * with the specified run-time configuration and repository pool object.
	 * <p/>
	 * If <code>options</code> is <span class="javakeyword">null</span>,
	 * then this <b>SVNMoveClient</b> will be using a default run-time
	 * configuration driver  which takes client-side settings from the
	 * default SVN's run-time configuration area but is not able to
	 * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).
	 * <p/>
	 * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
	 * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used to create {@link SVNRepository repository access objects}.
	 * @param repositoryPool   a repository pool object
	 * @param options          a run-time configuration options driver
	 */
	public SVNMoveClient17(ISVNRepositoryPool repositoryPool,
			ISVNOptions options) {
		super(repositoryPool, options);
		myWCClient = new SVNWCClient17(repositoryPool, options);
		myCopyClient = new SVNCopyClient17(repositoryPool, options);
	}

	public void setEventHandler(ISVNEventHandler dispatcher) {
		super.setEventHandler(dispatcher);
		myWCClient.setEventHandler(dispatcher);
		myCopyClient.setEventHandler(dispatcher);
	}

	public void setDebugLog(ISVNDebugLog log) {
		super.setDebugLog(log);
		myWCClient.setDebugLog(log);
		myCopyClient.setDebugLog(log);
	}

	public void setOptions(ISVNOptions options) {
		super.setOptions(options);
		if (myWCClient != null) {
			myWCClient.setOptions(options);
		}
		if (myCopyClient != null) {
			myCopyClient.setOptions(options);
		}
	}

	/** 
	 * Moves a source item to a destination one. 
	 * <p>
	 * <code>dst</code> should not exist. Furher it's considered to be versioned if
	 * its parent directory is under version control, otherwise <code>dst</code>
	 * is considered to be unversioned.
	 * <p>
	 * If both <code>src</code> and <code>dst</code> are unversioned, then simply 
	 * moves <code>src</code> to <code>dst</code> in the filesystem.
	 * <p>
	 * If <code>src</code> is versioned but <code>dst</code> is not, then 
	 * exports <code>src</code> to <code>dst</code> in the filesystem and
	 * removes <code>src</code> from version control.
	 * <p>
	 * If <code>dst</code> is versioned but <code>src</code> is not, then 
	 * moves <code>src</code> to <code>dst</code> (even if <code>dst</code>
	 * is scheduled for deletion).
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
	 * @param src            a source path
	 * @param dst            a destination path
	 * @throws SVNException   if one of the following is true:
	 * <ul>
	 * <li><code>dst</code> already exists
	 * <li><code>src</code> does not exist
	 * </ul>
	 */
	public void doMove(File src, File dst) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}

	/** 
	 * Reverts a previous move operation back. Provided in pair with {@link #doMove(File,File) doMove()} 
	 * and used to roll back move operations. In this case <code>src</code> is
	 * considered to be the target of the previsous move operation, and <code>dst</code>
	 * is regarded to be the source of that same operation which have been moved
	 * to <code>src</code> and now is to be restored. 
	 * <p>
	 * <code>dst</code> could exist in that case if it has been a WC directory
	 * that was scheduled for deletion during the previous move operation. Furher 
	 * <code>dst</code> is considered to be versioned if its parent directory is 
	 * under version control, otherwise <code>dst</code> is considered to be unversioned.
	 * <p>
	 * If both <code>src</code> and <code>dst</code> are unversioned, then simply 
	 * moves <code>src</code> back to <code>dst</code> in the filesystem.
	 * <p>
	 * If <code>src</code> is versioned but <code>dst</code> is not, then 
	 * unmoves <code>src</code> to <code>dst</code> in the filesystem and
	 * removes <code>src</code> from version control.
	 * <p>
	 * If <code>dst</code> is versioned but <code>src</code> is not, then 
	 * first tries to make a revert on <code>dst</code> - if it has not been committed
	 * yet, it will be simply reverted. However in the case <code>dst</code> has been already removed 
	 * from the repository, <code>src</code> will be copied back to <code>dst</code>
	 * and scheduled for addition. Then <code>src</code> is removed from the filesystem.
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
	 * <ul style="list-style-type: lower-alpha">
	 * <li>if <code>src</code> is scheduled for addition with history, then
	 * copies <code>src</code> to <code>dst</code> specifying the source
	 * ancestor's URL and revision (i.e. the ancestor of the source is the
	 * ancestor of the destination);
	 * <li>if <code>src</code> is already under version control, then
	 * copies <code>src</code> to <code>dst</code> specifying the source
	 * URL and revision as the ancestor (i.e. <code>src</code> itself is the
	 * ancestor of <code>dst</code>);
	 * <li>if <code>src</code> is just scheduled for addition (without history),
	 * then simply copies <code>src</code> to <code>dst</code> (only in the filesystem,
	 * without history) and schedules <code>dst</code> for addition;  
	 * </ul>
	 * then <code>src</code> is removed from version control.
	 * </ul>
	 * @param src            a source path
	 * @param dst            a destination path
	 * @throws SVNException   if <code>src</code> does not exist
	 */
	public void undoMove(File src, File dst) throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
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
	 * <p>
	 * In that case when you have your files copied/moved in the filesystem, you
	 * can not perform standard (version control) copying/moving - since the target already exists and
	 * the source may be already deleted. Use this method to overcome that restriction.  
	 * @param src           a source file path (was copied/moved to <code>dst</code>)
	 * @param dst           a destination file path
	 * @param move          if <span class="javakeyword">true</span> then
	 * completes moving <code>src</code> to <code>dst</code>,
	 * otherwise completes copying <code>src</code> to <code>dst</code>
	 * @throws SVNException  if one of the following is true:
	 * <ul>
	 * <li><code>move = </code><span class="javakeyword">true</span> and <code>src</code>
	 * still exists
	 * <li><code>dst</code> does not exist
	 * <li><code>dst</code> is a directory 
	 * <li><code>src</code> is a directory
	 * <li><code>src</code> is not under version control
	 * <li><code>dst</code> is already under version control
	 * <li>if <code>src</code> is copied but not scheduled for
	 * addition, and SVNKit is not able to locate the copied
	 * directory root for <code>src</code>
	 * </ul>
	 */
	public void doVirtualCopy(File src, File dst, boolean move)
			throws SVNException {
		SVNErrorMessage err = SVNErrorMessage
				.create(SVNErrorCode.VERSION_MISMATCH);
		SVNErrorManager.error(err, SVNLogType.CLIENT);
	}
}
