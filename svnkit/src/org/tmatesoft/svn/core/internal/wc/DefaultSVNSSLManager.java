/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXCertPathValidatorResult;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.ISVNSSLManager;
import org.tmatesoft.svn.core.auth.SVNSSLAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;
import org.tmatesoft.svn.util.SVNDebugLog;



/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class DefaultSVNSSLManager implements ISVNSSLManager {

    private SVNURL myURL;
    private File myClientCertFile;
    private String myClientCertPassword;
    private DefaultSVNAuthenticationManager myAuthManager;
    
    private KeyManager[] myKeyManagers;
    private X509Certificate[] myTrustedCerts;
    private boolean myIsKeyManagerCreated;
    private String myRealm;
    private File myAuthDirectory;
    private boolean myIsUseKeyStore;
    private File[] myServerCertFiles;
    private boolean myIsPromptForClientCert;
    private SVNSSLAuthentication myClientAuthentication;
    private Throwable myClientCertError;
    private TrustAnchor[] myTrustedAnchors;

    public DefaultSVNSSLManager(File authDir, SVNURL url, 
            File[] serverCertFiles, boolean useKeyStore, File clientFile, String clientPassword,
            boolean promptForClientCert,
            DefaultSVNAuthenticationManager authManager) {
        myURL = url;
        myAuthDirectory = authDir;
        myClientCertFile = clientFile;
        myClientCertPassword = clientPassword;
        myIsPromptForClientCert = promptForClientCert;
        myRealm = "https://" + url.getHost() + ":" + url.getPort();
        myAuthManager = authManager;
        myIsUseKeyStore = useKeyStore;
        myServerCertFiles = serverCertFiles;
        if (myClientCertFile != null) {
            // force cert load.
            getKeyManagers();
        }
    }
    
    public Throwable getClientCertLoadingError() {
        return myClientCertError;
    }

    private void init() {
        if (myTrustedCerts != null) {
            return;
        }
        Collection trustedCerts = new ArrayList();
        Collection trustedAnchors = new ArrayList();
        // load trusted certs from files.
        for (int i = 0; i < myServerCertFiles.length; i++) {
            X509Certificate cert = loadCertificate(myServerCertFiles[i]);
            if (cert != null) {
                trustedCerts.add(cert);
                trustedAnchors.add(new TrustAnchor(cert, null));
            }
        }
        // load from 'default' keystore
        if (myIsUseKeyStore) {
            try {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                if (keyStore != null) {
                    String path = System.getProperty("java.home") + "/lib/security/cacerts";
                    path = path.replace('/', File.separatorChar);
                    File file = new File(path);
                    InputStream is = null;
                    try {
                        if (file.isFile() && file.canRead()) {
                            is = SVNFileUtil.openFileForReading(file);
                        }
                        keyStore.load(is, null);
                    } catch (NoSuchAlgorithmException e) {
                    } catch (CertificateException e) {
                    } catch (IOException e) {
                    } catch (SVNException e) { 
                    } finally {
                        SVNFileUtil.closeFile(is);
                    }
                    PKIXParameters params = new PKIXParameters(keyStore);
                    for (Iterator anchors = params.getTrustAnchors().iterator(); anchors.hasNext(); ) {
                        TrustAnchor ta = (TrustAnchor) anchors.next();
                        trustedAnchors.add(ta);
                        X509Certificate cert = ta.getTrustedCert();
                        if (cert != null) {
                            trustedCerts.add(cert);
                        }
                    }
                    
                }
            } catch (KeyStoreException e) {
            } catch (InvalidAlgorithmParameterException e) {
            }
        }
        myTrustedCerts = (X509Certificate[]) trustedCerts.toArray(new X509Certificate[trustedCerts.size()]);
        myTrustedAnchors = (TrustAnchor[]) trustedAnchors.toArray(new TrustAnchor[trustedAnchors.size()]);
    }

    public SSLContext getSSLContext() throws IOException {
        try {
            SSLContext context = SSLContext.getInstance("SSLv3");
            context.init(getKeyManagers(), new TrustManager[] {new X509TrustManager() { 
                public X509Certificate[] getAcceptedIssuers() {
                    init();
                    return myTrustedCerts;
                }
                public void checkClientTrusted(X509Certificate[] certs, String arg1) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] certs, String algorithm) throws CertificateException {
                    if (certs != null && certs.length > 0 && certs[0] != null) {
                        init();
                        // check with our trust anchors.
                        if (myTrustedAnchors != null && myTrustedAnchors.length > 0) {
                            try {
                                CertPathValidator validator = CertPathValidator.getInstance("PKIX");
                                CertPath path = CertificateFactory.getInstance("X509").generateCertPath(Arrays.asList(certs));
                                PKIXParameters params = new PKIXParameters(new HashSet(Arrays.asList(myTrustedAnchors)));
                                params.setRevocationEnabled(false);
                                PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult) validator.validate(path, params);
                                if (result != null && result.getTrustAnchor() != null) {
                                    return;
                                }                        
                            } catch (NoSuchAlgorithmException e1) {
                            } catch (CertPathValidatorException e) {
                            } catch (InvalidAlgorithmParameterException e) {
                            }
                        }

                        String data = SVNBase64.byteArrayToBase64(certs[0].getEncoded());
                        String stored = (String) myAuthManager.getRuntimeAuthStorage().getData("svn.ssl.server", myRealm);
                        if (data.equals(stored)) {
                            return;
                        }
                        stored = getStoredServerCertificate(myRealm);
                        if (data.equals(stored)) {
                            return;
                        }
                        ISVNAuthenticationProvider authProvider = myAuthManager.getAuthenticationProvider();
                        int failures = SVNSSLUtil.getServerCertificateFailures(certs[0], myURL.getHost());
                        // compose bit mask.
                        // 8 is default
                        // check dates for 1 and 2
                        // check host name for 4
                        if (authProvider != null) {
                            boolean store = myAuthManager.isAuthStorageEnabled();
                            int result = authProvider.acceptServerAuthentication(myURL, myRealm, certs[0], store);
                            if (result == ISVNAuthenticationProvider.ACCEPTED && store) {
                                try {
                                    storeServerCertificate(myRealm, data, failures);
                                } catch (SVNException e) {
                                    CertificateException ce = new CertificateException("svn: Server SSL ceritificate for '" + myRealm + "' cannot be saved");
                                    ce.initCause(new SVNCancelException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, ce.getMessage())));
                                    throw ce;
                                }
                            }
                            if (result != ISVNAuthenticationProvider.REJECTED) {
                                myAuthManager.getRuntimeAuthStorage().putData("svn.ssl.server", myRealm, data);
                                return;
                            } 
                            CertificateException ce = new CertificateException("svn: Server SSL ceritificate for '" + myRealm + "' rejected");
                            ce.initCause(new SVNCancelException(SVNErrorMessage.create(SVNErrorCode.CANCELLED, ce.getMessage())));
                            throw ce;
                        } 
                        // like as tmp. accepted.
                        return;
                    }
                }                    
            }}, null);
            return context;
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e.getMessage());
        } catch (KeyManagementException e) {
            throw new IOException(e.getMessage());
        }            
    }

    public void acknowledgeSSLContext(boolean accepted, SVNErrorMessage errorMessage) {
        if (!accepted) {
            myIsKeyManagerCreated = false;
            myClientCertError = null;
            myKeyManagers = null;
            myTrustedCerts = null;
            myTrustedAnchors = null;
        }
    }
    
    private String getStoredServerCertificate(String realm) {
        File file = new File(myAuthDirectory, SVNFileUtil.computeChecksum(realm));
        if (!file.isFile()) {
            return null;
        }
        SVNProperties props = new SVNProperties(file, "");
        try {
            String storedRealm = props.getPropertyValue("svn:realmstring");
            if (!realm.equals(storedRealm)) {
                return null;
            }
            return props.getPropertyValue("ascii_cert");
        } catch (SVNException e) {
        }
        return null;
    }

    private void storeServerCertificate(String realm, String data, int failures) throws SVNException {
        myAuthDirectory.mkdirs();
        
        File file = new File(myAuthDirectory, SVNFileUtil.computeChecksum(realm));
        SVNProperties props = new SVNProperties(file, "");
        props.delete();
        try {
            props.setPropertyValue("ascii_cert", data);
            props.setPropertyValue("svn:realmstring", realm);
            props.setPropertyValue("failures", Integer.toString(failures));
            
            SVNFileUtil.setReadonly(props.getFile(), false);
        } catch (SVNException e) {
            props.delete();
        }
    }
    
    private KeyManager[] getKeyManagers() {
        if (myIsKeyManagerCreated) {
            return myKeyManagers;
        }
        myIsKeyManagerCreated = true;
        if (myClientCertFile == null) {
            return null;
        }
        myKeyManagers = loadClientCertificate();
        return myKeyManagers;
    }
    
    private KeyManager[] loadClientCertificate() {
        char[] passphrase = null;
        if (myClientCertPassword != null) {
            passphrase = myClientCertPassword.toCharArray();
        }
        KeyStore keyStore = null;            
        InputStream is;
        try {
            is = SVNFileUtil.openFileForReading(myClientCertFile);
        } catch (SVNException e1) {
            myClientCertError = e1;
            return null;
        }
        try {
            keyStore = KeyStore.getInstance("PKCS12");
            if (keyStore != null) {
                keyStore.load(is, passphrase);                    
            }
        } catch (Throwable th) {
            SVNDebugLog.getDefaultLog().info(th);
            myClientCertError = th;
            return null;
        } finally {
            SVNFileUtil.closeFile(is);
        }
        KeyManagerFactory kmf = null;
        KeyManager[] result = null;
        if (keyStore != null) {
            try {
                kmf = KeyManagerFactory.getInstance("SunX509");
                if (kmf != null) {
                    kmf.init(keyStore, passphrase);
                    result = kmf.getKeyManagers();
                }
            } catch (Throwable e) {
                myClientCertError = e;
                SVNDebugLog.getDefaultLog().info(e);
            } 
        }
        return result;
    }

    private static X509Certificate loadCertificate(File pemFile) {
        InputStream is = null;
        try {
            is = SVNFileUtil.openFileForReading(pemFile);
        } catch (SVNException e) {
            return null;
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X509");
            return (X509Certificate) factory.generateCertificate(is);
        } catch (CertificateException e) {
            SVNDebugLog.getDefaultLog().info(e);
            return null;
        } finally {
            SVNFileUtil.closeFile(is);
        }
    }

    public boolean isClientCertPromptRequired() {
        return myIsPromptForClientCert;
    }

    public void setClientAuthentication(SVNSSLAuthentication sslAuthentication) {
        if (sslAuthentication != null) {
            myClientCertFile = sslAuthentication.getCertificateFile();
            myClientCertPassword = sslAuthentication.getPassword();
        } else {
            myClientCertFile = null;
            myClientCertPassword = null;
        }
        myClientAuthentication = sslAuthentication;
        // load here.
        myKeyManagers = loadClientCertificate();
        myIsKeyManagerCreated = true;
    } 
    
    public SVNSSLAuthentication getClientAuthentication() {
        return myClientAuthentication;
    }
}
