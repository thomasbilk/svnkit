package org.tmatesoft.svn.core.diff.delta;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.diff.ISVNDeltaConsumer;
import org.tmatesoft.svn.core.diff.ISVNRAData;

/**
 * @author Marc Strapetz
 */
public interface ISVNDeltaGenerator {
	void generateDiffWindow(String commitPath, ISVNDeltaConsumer consumer, ISVNRAData workFile, ISVNRAData baseFile) throws SVNException;
}