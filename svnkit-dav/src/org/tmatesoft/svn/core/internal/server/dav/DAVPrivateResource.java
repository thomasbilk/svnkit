/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.File;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.server.dav.handlers.ServletDAVHandler;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.2.0
 * @author  TMate Software Ltd.
 */
public class DAVPrivateResource extends DAVResource {

    public DAVPrivateResource(SVNRepository repository, DAVResourceURI resourceURI, boolean isSVNClient, String deltaBase, long version, 
            String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) throws SVNException {
        super(repository, resourceURI, isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, userName, activitiesDB);
    }

    public DAVPrivateResource(SVNRepository repository, DAVResourceURI resourceURI, long revision, boolean isSVNClient, String deltaBase, 
            long version, String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) {
        super(repository, resourceURI, revision, isSVNClient, deltaBase, version, clientOptions, baseChecksum, resultChecksum, userName, 
                activitiesDB);
    }
    
    private DAVPrivateResource() {
    }

    protected void prepare() throws DAVException {
    }

    public DAVResource dup() {
        DAVPrivateResource copy = new DAVPrivateResource();
        copyTo(copy);
        return copy;
    }

    public DAVResource getParentResource() throws DAVException {
        throwIllegalGetParentResourceError();
        return null;
    }

    public static DAVPrivateResource createPrivateResource(DAVResource resource, DAVResourceKind resourceKind) {
        DAVPrivateResource privateResource = new DAVPrivateResource();
        resource.copyTo(privateResource);
        
        DAVResourceURI resourceURI = privateResource.getResourceURI();
        resourceURI.setKind(resourceKind);
        resourceURI.setType(DAVResourceType.PRIVATE);

        String path = "/" + DAVResourceURI.SPECIAL_URI + "/" + resourceKind.toString();
        resourceURI.setURI(path);
        resourceURI.setPath(null);
        
        privateResource.setCollection(true);
        privateResource.setExists(true);
        privateResource.setRevision(SVNRepository.INVALID_REVISION);
        return privateResource; 
    }
}
