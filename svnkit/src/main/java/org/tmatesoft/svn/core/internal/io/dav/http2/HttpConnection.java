package org.tmatesoft.svn.core.internal.io.dav.http2;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.io.dav.DAVElement;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVErrorHandler;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPHeader;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPSSLKeyManager;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPStatus;
import org.tmatesoft.svn.core.internal.io.dav.http.IHTTPConnection;
import org.tmatesoft.svn.core.internal.io.dav.http.SpoolFile;
import org.tmatesoft.svn.core.internal.io.dav.http.XMLReader;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNSSLUtil;
import org.tmatesoft.svn.core.internal.util.SVNSocketFactory;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableOutputStream;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.util.Version;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class HttpConnection implements IHTTPConnection {
    
    private static final String SPOOL_FILE = "svnkit.spool.file";
    public static final String CANCELLER_PARAMETER = "svnkit.canceller";
    
    private DefaultHttpClient myHttpClient;
    private BasicHttpContext myHttpContext;
    private HttpHost myHttpHost;
    
    private SAXParser mySAXParser;    
    private SVNURL myLocation;
    private HttpCredentialsProvider myCredentialsProvider;
    
    private File mySpoolDirectory;
    private boolean myIsSpoolAllRequests;
    private boolean myIsSpoolRequest;
    
    private ISVNCanceller myCanceller;
    private String myHttpCharset;

    public HttpConnection(SVNRepository repository, String charset, File spoolDirectory, boolean isSpoolAllRequestes) {
        SVNURL location = repository.getLocation();
        
        myHttpHost = new HttpHost(location.getHost(), location.getPort(), location.getProtocol());
        myHttpCharset = charset;
        myHttpContext = new BasicHttpContext();
        myCredentialsProvider = new HttpCredentialsProvider(getHttpContext(), repository, repository.getAuthenticationManager());
        
        myLocation = repository.getLocation();
        mySpoolDirectory = spoolDirectory;
        myIsSpoolAllRequests = isSpoolAllRequestes;
        myCanceller = repository.getCanceller();

        clearAuthenticationCache();
    }
    
    private DefaultHttpClient createHttpClient() throws SVNException {
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", 80, new HttpPlainSocketFactory()));
        if ("https".equalsIgnoreCase(myLocation.getProtocol())) {
            TrustManager tm = myCredentialsProvider.getSSLTrustManager();
            HTTPSSLKeyManager km = myCredentialsProvider.getSSLKeyManager();
            try {
                SSLContext context = SVNSocketFactory.createSSLContext(new KeyManager[] {km}, tm);
                registry.register(new Scheme("https", 443, new HttpSSLSocketFactory(context)));
            } catch (IOException e) {
                SVNDebugLog.getDefaultLog().logError(SVNLogType.NETWORK, e);
            }
        }
        return new DefaultHttpClient(new SingleClientConnManager(registry));
    }
    
    public void setSpoolResponse(boolean spoolResponse) {
        myIsSpoolRequest = spoolResponse;
    }
    
    private boolean isSpoolResponse() {
        return myIsSpoolAllRequests || myIsSpoolRequest;
    }

    public HTTPStatus request(String method, String path, HTTPHeader header,
            StringBuffer body, int ok1, int ok2, OutputStream dst,
            DefaultHandler handler) throws SVNException {
        return request(method, path, header, body, ok1, ok2, dst, handler, null);
    }

    public HTTPStatus request(String method, String path, HTTPHeader header,
            StringBuffer body, int ok1, int ok2, OutputStream dst,
            DefaultHandler handler, SVNErrorMessage context)
            throws SVNException {
        return request(method, path, header, (Object) body, ok1, ok2, dst, handler, context);
    }

    public HTTPStatus request(String method, String path, HTTPHeader header,
            InputStream body, int ok1, int ok2, OutputStream dst,
            DefaultHandler handler) throws SVNException {
        return request(method, path, header, body, ok1, ok2, dst, handler, null);
    }
    
    public HTTPStatus request(String method, String path, HTTPHeader header,
            InputStream body, int ok1, int ok2, OutputStream dst,
            DefaultHandler handler, SVNErrorMessage context)
            throws SVNException {
        return request(method, path, header, (Object) body, ok1, ok2, dst, handler, context);
    }
    
    private HTTPStatus request(String method, String path, HTTPHeader header,
            Object body, int ok1, int ok2, OutputStream dst,
            DefaultHandler handler, SVNErrorMessage context)
            throws SVNException {
        
        HttpDAVRequest request = createHttpRequest(method, path, header);
        
        HTTPStatus httpStatus = null;        
        HttpResponse response = null;
        SVNErrorMessage error = null;
        
        resetAuthenticationState();
        myCredentialsProvider.configureRequest(request);
        configureRequest(request);
        
        while (true) {
            error = null;
            try {
                request.setEntity(createRequestEntity(body, header));

                response = getHttpClient().execute(getHttpHost(), request, getHttpContext());
                if (response == null) {
                    throw new ClientProtocolException("Unexpected NULL HttpResponse object");
                } else if (response.getStatusLine() == null) {
                    throw new ClientProtocolException("Unexpected NULL StatusLine object");
                }
            } catch (ClientProtocolException e) {
                error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e);
            } catch (SSLHandshakeException e) {
                if (e.getCause() instanceof SVNSSLUtil.CertificateNotTrustedException) {
                    SVNErrorManager.cancel(e.getMessage(), SVNLogType.NETWORK);
                } else {
                    SVNErrorMessage sslErr = SVNErrorMessage.create(SVNErrorCode.RA_NOT_AUTHORIZED, "SSL handshake failed: ''{0}''", new Object[] { e.getMessage() }, SVNErrorMessage.TYPE_ERROR, e);
                    if (acknowledgeSSLContext(sslErr)) {
                        consumeResponse(response);
                        error = null;
                        continue;
                    }
                    error = sslErr;
                }
            } catch (SVNCancellableOutputStream.IOCancelException e) {
                SVNErrorManager.cancel(e.getMessage(), SVNLogType.NETWORK);
            } catch (IOException e) {
                error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e);
            } finally {
                if (error != null) {
                    close();
                }
            }
         
            if (error == null) {
                error = myCredentialsProvider.getAuthenticationError();
            }
            if (error != null) {
                consumeResponse(response);
                close();
                SVNErrorManager.error(error, SVNLogType.NETWORK);
            }
            
            try {
                acknowledgeSSLContext(null);
                
                httpStatus = createHttpStatus(response);
                
                int code = response.getStatusLine().getStatusCode();
                error = createErrorFromStatus(response.getStatusLine(), path, context); 
                if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                    acknowledgeCredentials(error);
                    SVNErrorManager.error(error, SVNLogType.NETWORK);
                } else if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    acknowledgeCredentials(error);
                    if (myCredentialsProvider.hasMoreCredentials()) {
                        error = null;
                        continue;
                    }
                    SVNErrorManager.error(error, SVNLogType.NETWORK);
                } else if (code == HttpURLConnection.HTTP_PROXY_AUTH) {
                    acknowledgeProxyContext(error);
                    SVNErrorManager.error(error, SVNLogType.NETWORK);
                } else if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP) {
                    SVNErrorManager.error(error, SVNLogType.NETWORK);
                }
                acknowledgeProxyContext(null);
                acknowledgeCredentials(null);

                HttpEntity entity = new BasicHttpEntity();
                if (response.getEntity() != null) {
                    entity = response.getEntity();
                    if (isSpoolResponse()) {
                        entity = spoolEntity(entity);
                    }
                }
                if (!isExpectedCode(method, code, ok1, ok2)) {
                    error = readError(method, path, response.getStatusLine(), entity.getContent(), context);
                } else if (code >= 300) {
                    SVNErrorMessage serverError = readError(method, path, response.getStatusLine(), entity.getContent(), context);
                    httpStatus.setError(serverError);
                    error = null;
                } else if (dst != null) {
                    response.getEntity().writeTo(dst);
                    error = null;
                } else if (handler != null) {
                    error = parseXMLEntity(method, path, handler, entity.getContent());
                } else {
                    error = null;
                }
            } catch (SVNCancellableOutputStream.IOCancelException e) {
                error = SVNErrorMessage.create(SVNErrorCode.CANCELLED, e);
            } catch (IOException e) {
                error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e);
            } finally {
                consumeResponse(response);
            }         
            break;
        }
        if (error != null) {
            SVNErrorManager.error(error, SVNLogType.NETWORK);
        }
        return httpStatus;
    }
    
    private void configureRequest(HttpDAVRequest request) {
        if (myHttpCharset != null) {
            HttpProtocolParams.setHttpElementCharset(request.getParams(), myHttpCharset);
        }
        
        HttpProtocolParams.setUserAgent(request.getParams(), Version.getUserAgent());
        HttpConnectionParams.setSoReuseaddr(request.getParams(), true);
        int bufferSize = SVNSocketFactory.getSocketReceiveBufferSize();
        if (bufferSize <= 0) {
            bufferSize = 8192;
        }
        HttpConnectionParams.setSocketBufferSize(request.getParams(), bufferSize);        
        request.getParams().setParameter(CANCELLER_PARAMETER, myCanceller);
    }

    private HttpEntity spoolEntity(HttpEntity entity) throws IOException {
        SpoolFile spoolFile = new SpoolFile(mySpoolDirectory);
        OutputStream os = null;
        try {
            os = new SVNCancellableOutputStream(spoolFile.openForWriting(), myCanceller);
            entity.writeTo(os);
        } finally {
            SVNFileUtil.closeFile(os);
        }
        getHttpContext().setAttribute(SPOOL_FILE, spoolFile);
        return new InputStreamEntity(spoolFile.openForReading(), -1);
    }


    private void resetAuthenticationState() {
        myCredentialsProvider.reset();
    }

    private void consumeResponse(HttpResponse response) {
        if (response == null) {
            close();
        } else {
            try {
                EntityUtils.consume(response.getEntity());
            } catch (IOException e) {
            }
        }
        SpoolFile spoolFile = (SpoolFile) getHttpContext().getAttribute(SPOOL_FILE);
        if (spoolFile != null) {
            getHttpContext().removeAttribute(SPOOL_FILE);
            try {
                spoolFile.delete();
            } catch (SVNException e) {
                SVNDebugLog.getDefaultLog().logError(SVNLogType.NETWORK, e);
            }
        }
    }

    private SVNErrorMessage createErrorFromStatus(StatusLine statusLine, String path, SVNErrorMessage context) {
        SVNErrorCode errorCode = SVNErrorCode.RA_DAV_REQUEST_FAILED;
        String contextMessage = context != null ? context.getMessageTemplate() : null;
        Object[] contextObjects = context != null ? context.getRelatedObjects() : null; 
        String message = statusLine != null ? statusLine.getStatusCode() + " " + statusLine.getReasonPhrase() : "";
        if (statusLine != null && statusLine.getStatusCode() == HttpURLConnection.HTTP_FORBIDDEN || statusLine.getStatusCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
            errorCode = SVNErrorCode.RA_NOT_AUTHORIZED;
            message = statusLine.getStatusCode() + " " + statusLine.getReasonPhrase();
        } else if (statusLine != null && statusLine.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            errorCode = SVNErrorCode.FS_NOT_FOUND;
        } else if (statusLine != null && (statusLine.getStatusCode() == HttpURLConnection.HTTP_MOVED_PERM || 
                statusLine.getStatusCode() == HttpURLConnection.HTTP_MOVED_TEMP)) {
            message = statusLine.getStatusCode() == HttpURLConnection.HTTP_MOVED_PERM ? "Repository moved permanently to ''{0}''; please relocate" : 
                "Repository moved temporarily to ''{0}''; please relocate";
            return SVNErrorMessage.create(SVNErrorCode.RA_DAV_RELOCATED, message, path);
        }
        // extend context object to include host:port (empty location).
        Object[] messageObjects = contextObjects == null ? new Object[1] : new Object[contextObjects.length + 1];
        int index = messageObjects.length - 1;
        messageObjects[messageObjects.length - 1] = myLocation;
        if (messageObjects.length > 1) {
            System.arraycopy(contextObjects, 0, messageObjects, 0, contextObjects.length);
        }
        if (contextMessage != null) {
            return SVNErrorMessage.create(errorCode, contextMessage + ": " + message + " ({" + index + "})", messageObjects);
        } 
        return SVNErrorMessage.create(errorCode, message + " ({" + index + "})", messageObjects);
    }


    private HTTPStatus createHttpStatus(HttpResponse response) {
        HTTPStatus httpStatus;
        try {
            httpStatus = HTTPStatus.createHTTPStatus(response.getStatusLine().toString());
        } catch (ParseException e) {
            return null;
        }
        HTTPHeader responseHeaders = new HTTPHeader();
        Header[] allHeaders = response.getAllHeaders();
        for (int i = 0; i < allHeaders.length; i++) {
            responseHeaders.addHeaderValue(allHeaders[i].getName(), allHeaders[i].getValue());
        }
        httpStatus.setHeader(responseHeaders);
        return httpStatus;
    }


    private void acknowledgeProxyContext(SVNErrorMessage error) throws SVNException {
        myCredentialsProvider.acknowledgeProxyContext(error);
    }

    private boolean acknowledgeSSLContext(SVNErrorMessage error) throws SVNException {
        return myCredentialsProvider.acknowledgeSSLContext(error);
    }

    private void acknowledgeCredentials(SVNErrorMessage error) throws SVNException {
        myCredentialsProvider.acknowledgeCredentials(error);
    }

    private boolean isExpectedCode(String method, int code, int ok1, int ok2) {
        boolean notExpected = false;        
        if (ok1 >= 0) {
            if (ok1 == 0) {
                ok1 = "PROPFIND".equals(method) ? 207 : 200;
            }
            if (ok2 <= 0) {
                ok2 = ok1;
            }
            notExpected = !(code == ok1 || code == ok2); 
        }
        return !notExpected;
    }

    private HttpContext getHttpContext() {
        return myHttpContext;
    }


    private HttpDAVRequest createHttpRequest(String method, String path, HTTPHeader header) throws SVNException {
        HttpDAVRequest request = new HttpDAVRequest(method);
        if (path == null || "".equals(path)) {
            path = "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        try {
            request.setURI(new URI(myLocation.getProtocol(), myLocation.getUserInfo(), myLocation.getHost(), myLocation.getPort(), SVNEncodingUtil.uriDecode(path), null, null));
        } catch (URISyntaxException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e), SVNLogType.NETWORK);
        }
        addDefaultAndCustomHeaders(header, request);
        return request;
    }


    private void addDefaultAndCustomHeaders(HTTPHeader header, HttpRequest request) {
        if (header != null) {
            @SuppressWarnings("unchecked")
            Map<String, Collection<String>> additionalHeaders = (Map<String, Collection<String>>) header.getRawHeaders();
            for (String headerName : additionalHeaders.keySet()) {
                for (String value : additionalHeaders.get(headerName)) {
                    if (!HTTP.CONTENT_LEN.equals(headerName)) {
                        request.addHeader(headerName, value);
                    }
                }
            }
        }
        request.addHeader(HTTPHeader.DAV_HEADER, DAVElement.DEPTH_OPTION);
        request.addHeader(HTTPHeader.DAV_HEADER, DAVElement.MERGE_INFO_OPTION);
        request.addHeader(HTTPHeader.DAV_HEADER, DAVElement.LOG_REVPROPS_OPTION);
        
        if (!request.containsHeader(HTTP.CONTENT_TYPE)) {
            request.addHeader(HTTP.CONTENT_TYPE, "text/xml; charset=\"utf-8\"");
        }
    }

    private AbstractHttpEntity createRequestEntity(Object body, HTTPHeader customHeaders) throws UnsupportedEncodingException {
        AbstractHttpEntity entity = null; 
        
        if (body instanceof ByteArrayInputStream) {
            int length = ((ByteArrayInputStream) body).available();
            entity = new InputStreamEntity((InputStream) body, length);
        } else if (body instanceof byte[]) {
            entity = new ByteArrayEntity((byte[]) body);
        } else if (body instanceof InputStream){
            int length = -1;
            if (customHeaders.getFirstHeaderValue(HTTP.CONTENT_LEN) != null) {
                String lengthStr = customHeaders.getFirstHeaderValue(HTTP.CONTENT_LEN);
                try {
                    length = Integer.parseInt(lengthStr);
                } catch (NumberFormatException nfe) {                    
                }
                if (length < 0) {
                    length = -1;
                }
            }
            entity = new InputStreamEntity((InputStream) body, length);
        } else if (body instanceof StringBuffer) {
            entity = new StringEntity(body.toString(), "text/xml", HTTP.UTF_8);
        }
        
        return entity;
    }

    private SVNErrorMessage readError(String method, String path, StatusLine statusLine, InputStream content, SVNErrorMessage context) throws SVNCancelException {
        if (statusLine.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
            context = SVNErrorMessage.create(context != null ? context.getErrorCode() : SVNErrorCode.FS_NOT_FOUND, "''{0}'' path not found", path);
        } 
        SVNErrorMessage error = createErrorFromStatus(statusLine, path, context);
        SVNErrorMessage davError = parseXMLErrorEntity(method, path, content);
        if (davError != null) {
            if (error != null) {
                davError.setChildErrorMessage(error);
            }
            return davError; 
        }
        return error;
    }

    private SVNErrorMessage parseXMLErrorEntity(String method, String path, InputStream content) throws SVNCancelException {
        DAVErrorHandler errorHandler = new DAVErrorHandler();
        parseXMLEntity(method, path, errorHandler, content);
        return errorHandler.getErrorMessage();
    }

    private SVNErrorMessage parseXMLEntity(String method, String path, DefaultHandler handler, InputStream content) throws SVNCancelException {
        SVNErrorMessage error = null;
        
        try {
            XMLReader reader = new XMLReader(content);
            while (!reader.isClosed()) {
                org.xml.sax.XMLReader xmlReader = getSAXParser().getXMLReader();
                xmlReader.setContentHandler(handler);
                xmlReader.setDTDHandler(handler);
                xmlReader.setErrorHandler(handler);
                xmlReader.setEntityResolver(HttpXMLUtil.NO_ENTITY_RESOLVER);
                xmlReader.parse(new InputSource(reader));
            }
        } catch (SVNCancellableOutputStream.IOCancelException e) {
            mySAXParser = null;
            SVNErrorManager.cancel(e.getMessage(), SVNLogType.NETWORK);
        } catch (IOException e) {
            mySAXParser = null;
            error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, e);
        } catch (SAXException e) {
            mySAXParser = null;
            if (e.getCause() instanceof SVNException) {
                error = ((SVNException) e.getCause()).getErrorMessage();
            } else if (e.getException() instanceof SVNException) {
                error = ((SVNException) e.getException()).getErrorMessage();
            } else {
                error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "Processing {0} request response failed: {1} ({2}) ",  new Object[] {method, e.getMessage(), path});
            }
        } catch (ParserConfigurationException e) {
            mySAXParser = null;
            error = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "XML parser configuration error while processing {0} request response: {1} ({2}) ",  new Object[] {method, e.getMessage(), path});
        } finally {
            if (mySAXParser != null) {
                org.xml.sax.XMLReader xmlReader = null;
                try {
                    xmlReader = mySAXParser.getXMLReader();
                } catch (SAXException e) {
                }
                if (xmlReader != null) {
                    xmlReader.setContentHandler(HttpXMLUtil.DEFAULT_SAX_HANDLER);
                    xmlReader.setDTDHandler(HttpXMLUtil.DEFAULT_SAX_HANDLER);
                    xmlReader.setErrorHandler(HttpXMLUtil.DEFAULT_SAX_HANDLER);
                    xmlReader.setEntityResolver(HttpXMLUtil.NO_ENTITY_RESOLVER);
                }
            }
        }        
        return error;
    }
    
    private SAXParser getSAXParser() throws ParserConfigurationException, SAXException, FactoryConfigurationError {
        if (mySAXParser == null) {
            mySAXParser = HttpXMLUtil.getSAXParserFactory().newSAXParser();
        }
        return mySAXParser;
    }

    public SVNAuthentication getLastValidCredentials() {
        return myCredentialsProvider.getLastValidCredentials();
    }

    public void clearAuthenticationCache() {
        myCredentialsProvider.clear();
        myHttpContext.setAttribute(ClientContext.AUTH_CACHE, new BasicAuthCache());
    }

    public void close() {
        clearAuthenticationCache();
        if (myHttpClient != null && myHttpClient.getConnectionManager() != null) {
            myHttpClient.getConnectionManager().shutdown();
            myHttpClient = null;
        }
    }

    private HttpHost getHttpHost() {
        return myHttpHost;
    }

    private DefaultHttpClient getHttpClient() throws SVNException {
        if (myHttpClient == null) {
            myHttpClient = createHttpClient();
        }
        return myHttpClient;
    }
}
