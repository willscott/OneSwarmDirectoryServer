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

import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.jetty.nio.SelectChannelConnector;
import org.mortbay.thread.QueuedThreadPool;
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

            StringBuilder response = new StringBuilder();

            // Check for the action parameter and do action
            if (parameters.containsKey(PARAM_ACTION)) {
                String action = parameters.get(PARAM_ACTION);
                if (action.equals(CHECK_IN)) {
                    handleRegisterAction(true, request.getInputStream(), response);
                } else if (action.equals(REGISTER)) {
                    handleRegisterAction(false, request.getInputStream(), response);
                } else if (action.equals(LIST_NODES)) {
                    long lastUpdate = parameters.containsKey(LAST_UPDATE) ? Long
                            .parseLong(parameters.get(LAST_UPDATE)) : 0l;
                    response = db.getUpdatesSince(lastUpdate);
                } else {
                    response = XML.tag(XML.GENERAL_ERROR, "Invalid Operation");
                }

                request.setHandled(true);
                response = XML.tag(XML.EXIT_NODE_LIST, response).insert(0, XML.HEADER);
                resp.getOutputStream().write(response.toString().getBytes(XML.ENCODING));
            }
        }

        private void handleRegisterAction(boolean justCheckIn, InputStream xml,
                StringBuilder response) {
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
                        response.append(XML.tag(XML.EXIT_NODE,
                                XML.tag(XML.SERVICE_ID, "" + node.getId()),
                                XML.tag(XML.NODE_ERROR, e.getMessage())));
                    }
                }
                db.saveEdits();
            } catch (SAXParseException e) {
                // TODO (nick) Remove after debugging.
                e.printStackTrace();
                // These are XML errors such as
                // "Unexpected End of File"
                response = XML.tag(XML.GENERAL_ERROR, "Error on line " + e.getLineNumber()
                        + ", column " + e.getColumnNumber() + ": " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                response = XML.tag(XML.GENERAL_ERROR, e.getMessage());
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
