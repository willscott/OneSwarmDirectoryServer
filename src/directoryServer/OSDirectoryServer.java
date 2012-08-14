package directoryServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.thread.QueuedThreadPool;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Directory Server This class maintains a Database of ExitNodeInfo and serves
 * it to OneSwarm instances that need
 * 
 * @author nick
 */
public class OSDirectoryServer implements Runnable {
    private static final String PARAM_ACTION = "action";
    private static final String CHECK_IN = "checkin";
    private static final String REGISTER = "register";
    private static final String LIST_NODES = "list";
    // TODO (nick) Add incremental update functionality and make a spec for
    // Nodes deleted
    // since last update
    private static final String LAST_UPDATE = "lastUpdate";

    final Server jettyServer = new Server();
    ExitNodeDB db;

    private OSDirectoryServer(int port) throws ParserConfigurationException, SAXException,
            IOException {

        db = new ExitNodeDB();
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
        jettyServer.addHandler(new DirectoryRequestHandler());
    }

    private class DirectoryRequestHandler extends AbstractHandler {
        ExitNodeDB db;

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

            OutputFormat of = new OutputFormat("XML", "ISO-8859-1", true);
            of.setIndent(1);
            of.setIndenting(true);
            XMLSerializer serializer = new XMLSerializer(resp.getOutputStream(), of);
            ContentHandler hd = serializer.asContentHandler();
            hd.startDocument();

            // Check for the action parameter and do action
            if (parameters.containsKey(PARAM_ACTION)) {
                String action = parameters.get(PARAM_ACTION);
                if (action.equals(CHECK_IN)) {
                    handleRegisterAction(true, request.getInputStream(), hd);
                } else if (action.equals(REGISTER)) {
                    handleRegisterAction(false, request.getInputStream(), hd);
                } else if (action.equals(LIST_NODES)) {
                    long lastUpdate = parameters.containsKey(LAST_UPDATE) ? Long
                            .parseLong(parameters.get(LAST_UPDATE)) : 0l;
                    response = db.getUpdatesSince(lastUpdate);
                } else {
                    hd.startElement("", "", XMLConstants.GENERAL_ERROR, null);
                    char[] message = "Invalid Operation".toCharArray();
                    hd.characters(message, 0, message.length);
                    hd.endElement("", "", XMLConstants.GENERAL_ERROR);
                }
                hd.endDocument();

                request.setHandled(true);
                resp.getOutputStream().flush();
            }
        }

        private void handleRegisterAction(boolean justCheckIn, InputStream xml, ContentHandler hd)
                throws SAXException {
            try {
                List<ExitNodeRecord> newNodes = new Parser(xml).parseAsExitNodeList();
                for (ExitNodeRecord node : newNodes) {
                    try {
                        if (justCheckIn) {
                            db.checkIn(node);
                        } else {
                            db.add(node);
                        }
                    } catch (IllegalArgumentException e) {
                        // TODO (nick) remove after debugging.
                        e.printStackTrace();
                        // These are our checks for correctness such
                        // as "Duplicate Key Used"
                        hd.startElement("", "", XMLConstants.EXIT_NODE, null);
                        hd.startElement("", "", XMLConstants.SERVICE_ID, null);
                        char[] msg = ("" + node.serviceId).toCharArray();
                        hd.characters(msg, 0, msg.length);
                        hd.endElement("", "", XMLConstants.SERVICE_ID);
                        hd.startElement("", "", XMLConstants.NODE_ERROR, null);
                        msg = e.getMessage().toCharArray();
                        hd.characters(msg, 0, msg.length);
                        hd.endElement("", "", XMLConstants.NODE_ERROR);
                        hd.endElement("", "", XMLConstants.EXIT_NODE);
                    }
                }
                db.saveEdits();
            } catch (SAXParseException e) {
                // TODO (nick) Remove after debugging.
                e.printStackTrace();
                // These are XML errors such as
                // "Unexpected End of File"
                hd.startElement("", "", XMLConstants.GENERAL_ERROR, null);
                char[] msg = ("Error on line " + e.getLineNumber() + ", column "
                        + e.getColumnNumber() + ": " + e.getMessage()).toCharArray();
                hd.characters(msg, 0, msg.length);
                hd.endElement("", "", XMLConstants.GENERAL_ERROR);
            } catch (Exception e) {
                e.printStackTrace();
                hd.startElement("", "", XMLConstants.GENERAL_ERROR, null);
                char[] msg = e.getMessage().toCharArray();
                hd.characters(msg, 0, msg.length);
                hd.endElement("", "", XMLConstants.GENERAL_ERROR);
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
            Thread directoryServer = new Thread(new OSDirectoryServer(port));
            directoryServer.setName("Exit Node Directory Web Server");
            directoryServer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
