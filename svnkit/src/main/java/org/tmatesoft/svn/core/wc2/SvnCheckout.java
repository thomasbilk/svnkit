package org.tmatesoft.svn.core.wc2;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNLogType;

public class SvnCheckout extends AbstractSvnUpdate<Long> {
    
    private SvnTarget source;

    protected SvnCheckout(SvnOperationFactory factory) {
        super(factory);
    }

    public SvnTarget getSource() {
        return source;
    }

    public void setSource(SvnTarget source) {
        this.source = source;
    }

    @Override
    protected void ensureArgumentsAreValid() throws SVNException {
        super.ensureArgumentsAreValid();
        if (getRevision() == null) {
            setRevision(SVNRevision.UNDEFINED);
        }
        
        if (getSource() == null || !getSource().isURL() || getSource().getURL() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL);
            SVNErrorManager.error(err, SVNLogType.WC);
        }

        if (!getRevision().isValid() && getFirstTarget() != null) {
            setRevision(getFirstTarget().getPegRevision());            
        }
        if (!getRevision().isValid()) {
            setRevision(SVNRevision.HEAD);
        }
        
        if (getFirstTarget() == null || getFirstTarget().getFile() == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_FILENAME, "Checkout destination path can not be NULL");
            SVNErrorManager.error(err, SVNLogType.WC);
        }
        if (getRevision().getNumber() < 0 && getRevision().getDate() == null && getRevision() != SVNRevision.HEAD) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_BAD_REVISION);
            SVNErrorManager.error(err, SVNLogType.WC);
        }
    }
    
    

}
