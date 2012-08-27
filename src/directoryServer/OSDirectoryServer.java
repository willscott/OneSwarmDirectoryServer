package directoryServer;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyStore.ProtectionParameter;
import java.security.cert.Certificate;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.encoders.Base64;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.thread.QueuedThreadPool;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import edu.washington.cs.oneswarm.f2f.xml.XMLHelper;

/**
 * Directory Server This class maintains a Database of ExitNodeInfo and serves
 * 
 * it to OneSwarm instances that need
 * 
 * @author nick
 */
public class OSDirectoryServer implements Runnable {
	private static final String KEY_STORE = "key.store";
    private static final String PARAM_ACTION = "action";
    private static final String CHECK_IN = "checkin";
    private static final String REGISTER = "register";
    private static final String LIST_NODES = "list";
    // TODO (nick) Add incremental update functionality and make a spec for
    // Nodes deleted
    // since last update
    private static final String LAST_UPDATE = "lastUpdate";

    final Server jettyServer = new Server();
    DirectoryDB db;

    private OSDirectoryServer(int port, Signature authority) throws ParserConfigurationException, SAXException,
            IOException {

        db = new DirectoryDB();
        new Thread(new ServiceConsole(db)).start();

        /* Define thread pool for the web server. */
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMinThreads(2);
        threadPool.setMaxThreads(250);
        threadPool.setName("Jetty thread pool");
        threadPool.setDaemon(true);
        jettyServer.setThreadPool(threadPool);

        /* Define connection statistics for the web server. */
        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setMaxIdleTime(10000);
        connector.setLowResourceMaxIdleTime(5000);
        connector.setAcceptQueueSize(100);
        connector.setHost(null);
        connector.setPort(port);
        jettyServer.addConnector(connector);

        /* Define handlers for the web server. */
        jettyServer.addHandler(new DirectoryRequestHandler(authority));
    }

    private class DirectoryRequestHandler extends AbstractHandler {
    	private final Signature authority;

    	protected DirectoryRequestHandler(Signature authority) {
    		super();
    		this.authority = authority;
    	}

        @Override
        public void handle(String target, HttpServletRequest req, HttpServletResponse resp,
                int dispatch) throws IOException, ServletException {

            Request request = (req instanceof Request) ? (Request) req : HttpConnection
                    .getCurrentConnection().getRequest();

            // Map of GET method parameters
            Map<String, String> parameters = new HashMap<String, String>();
            for (Object keyObj : request.getParameterMap().keySet()) {
                String key = (keyObj instanceof String) ? (String) keyObj : "";
                String value = request.getParameter(key);
                if (key != null && value != null) {
                    parameters.put(key, value);
                }
            }
            try {

                // Check for the action parameter and do action
                if (parameters.containsKey(PARAM_ACTION)) {
                	ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
                    XMLHelper xmlOut = new XMLHelper(responseStream);
                    String action = parameters.get(PARAM_ACTION);
                    if (action.equals(CHECK_IN)) {
                        handleRegisterAction(true, request.getInputStream(), xmlOut);
                    } else if (action.equals(REGISTER)) {
                        handleRegisterAction(false, request.getInputStream(), xmlOut);
                    } else if (action.equals(LIST_NODES)) {
                        long lastUpdate = parameters.containsKey(LAST_UPDATE) ? Long
                                .parseLong(parameters.get(LAST_UPDATE)) : 0l;
                        db.getUpdatesSince(lastUpdate, xmlOut);
                    } else {
                        xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST, "Invalid Operation");
                    }
                    xmlOut.writeDigest();
                    xmlOut.close();
                    byte[] response = responseStream.toByteArray();
                    authority.update(response);
                    byte[] sig = authority.sign();
                    int pos = Utils.lastIndexOf(response, XMLHelper.DIGEST_PLACEHOLDER.getBytes());
                    int remainder = pos + XMLHelper.DIGEST_PLACEHOLDER.getBytes().length;
                    request.setHandled(true);
                    
                    resp.getOutputStream().write(response, 0, pos);
                    resp.getOutputStream().write(Base64.encode(sig), 0, sig.length);
                    resp.getOutputStream().write(response, remainder, response.length - remainder);
                    
                    resp.getOutputStream().flush();
                }
            } catch (SAXException e) {
                request.setHandled(false);
            } catch (SignatureException e) {
            	request.setHandled(false);
				e.printStackTrace();
			}
        }

        private void handleRegisterAction(boolean justCheckIn, InputStream xmlIn, XMLHelper xmlOut)
                throws SAXException {
            try {
                List<DirectoryRecord> newNodes = new LinkedList<DirectoryRecord>();
                XMLHelper.parse(xmlIn, new DirectoryRecordHandler(newNodes, xmlOut));
                for (DirectoryRecord node : newNodes) {
                    xmlOut.startElement(node.type());
                    xmlOut.writeTag(XMLHelper.SERVICE_ID, node.serviceId + "");
                    if (justCheckIn) {
                        db.checkIn(node, xmlOut);
                    } else {
                        db.add(node, xmlOut);
                    }
                    xmlOut.endElement(node.type());
                }
                db.saveEdits();
            } catch (SAXParseException e) {
                // These are XML errors such as
                // "Unexpected End of File"
                xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST,
                        "Error on line " + e.getLineNumber() + ", column " + e.getColumnNumber()
                                + ": " + e.getMessage());
            } catch (Exception e) {
                xmlOut.writeStatus(XMLHelper.ERROR_GENERAL_SERVER, e.getMessage());
            }
        }
    }

    @Override
    public void run() {
        try {
            jettyServer.start();
            jettyServer.join();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            jettyServer.destroy();
        }
    }

    public static void main(String[] args) {
        try {
            int port = 7888;
            File storeFile = new File(KEY_STORE);
            boolean firstTime = !storeFile.exists();
            
            KeyStore store = java.security.KeyStore.getInstance("JKS");
            if (firstTime) {
            	Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
            	store.load(null, null);
            	KeyPairGenerator keygen = java.security.KeyPairGenerator.getInstance("RSA");
            	keygen.initialize(1024, new SecureRandom());
            	KeyPair newkey = keygen.generateKeyPair();

            	// Generate the certificate linking the key pair.
            	Date startDate = new Date();
            	Date endDate = new Date(System.currentTimeMillis() + 5 * 365 * 24 * 60 * 60 * 1000); // 5 years.

            	ContentSigner siggen = new JcaContentSignerBuilder("SHA1withRSA").setProvider("BC").build(newkey.getPrivate());
            	SubjectPublicKeyInfo pubinfo = SubjectPublicKeyInfo.getInstance(newkey.getPublic().getEncoded());
            	
            	X509CertificateHolder holder = new X509v1CertificateBuilder(
            			new X500Name("CN=OneSwarmDirectory"),
            			BigInteger.ONE,
            			startDate,
            			endDate,
            			new X500Name("CN=OneSwarmDirectory"),
            			pubinfo).build(siggen);
            	Certificate cert = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(holder.getEncoded()));
            	
            	store.setKeyEntry("signingkey", newkey.getPrivate(), new char[] {}, new java.security.cert.Certificate[] {cert});
            	store.setCertificateEntry("signingcert", cert);
            	store.store(new FileOutputStream(storeFile), new char[] {});
            } else {
            	store.load(new FileInputStream(storeFile), new char[] {});
            }

            PrivateKey privateKey = ((KeyStore.PrivateKeyEntry) store.getEntry("signingkey", new PasswordProtection(new char[] {}))).getPrivateKey();
            Signature me = java.security.Signature.getInstance("SHA1withRSA");
            me.initSign(privateKey);
            
            Thread directoryServer = new Thread(new OSDirectoryServer(port, me));
            directoryServer.setName("Exit Node Directory Web Server");
            directoryServer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
