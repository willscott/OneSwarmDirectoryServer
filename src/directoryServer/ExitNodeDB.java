package directoryServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ExitNodeDB {
    private static final String DATABASE_FILE = "knownExitNodes.xml";
    // The max age that a ExitNode registration may have before it is deleted.
    private static final int MAX_AGE = 60 * 60 * 1000;
    private static final int GRACE_PERIOD = 60 * 1000;
    private final Map<Long, ExitNodeRecord> registeredKeys;
    private PriorityQueue<ExitNodeRecord> readableExitNodeList;
    private final PriorityQueue<ExitNodeRecord> mutableExitNodeList;

    public ExitNodeDB() throws ParserConfigurationException, SAXException, IOException {
        mutableExitNodeList = new PriorityQueue<ExitNodeRecord>();
        registeredKeys = new HashMap<Long, ExitNodeRecord>();
        try {
            File dbFile = new File(DATABASE_FILE);
            if (!dbFile.exists()) {
                dbFile.createNewFile();
                OutputFormat of = new OutputFormat("XML", "ISO-8859-1", true);
                of.setIndent(1);
                of.setIndenting(true);
                FileOutputStream fos = new FileOutputStream(dbFile);
                XMLSerializer serializer = new XMLSerializer(fos, of);
                org.xml.sax.ContentHandler hd = serializer.asContentHandler();
                hd.startDocument();
                hd.startElement("", "", XMLConstants.EXIT_NODE_LIST, null);
                hd.endElement("", "", XMLConstants.EXIT_NODE_LIST);
                hd.endDocument();
                fos.close();
            }
            readFromFile(dbFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Thread dbClean = new Thread(new DBCleaner());
        dbClean.setDaemon(true);
        dbClean.start();
    }

    public void add(ExitNodeRecord node) {
        String errors = node.checkForErrors(true);
        if (!errors.equals("")) {
            throw new IllegalArgumentException(errors);
        }

        synchronized (registeredKeys) {
            if (registeredKeys.containsKey(node.serviceId)) {
                ExitNodeRecord oldNode = registeredKeys.get(node.serviceId);
                if (!oldNode.publicKey.equals(node.publicKey)) {
                    throw new IllegalArgumentException("Duplicate Key Used.");
                }
                remove(oldNode);
            }
            registeredKeys.put(node.serviceId, node);
        }

        synchronized (mutableExitNodeList) {
            mutableExitNodeList.add(node);
        }
    }

    void remove(ExitNodeRecord node) {
        synchronized (mutableExitNodeList) {
            mutableExitNodeList.remove(node);
        }
        synchronized (registeredKeys) {
            registeredKeys.remove(node.serviceId);
        }
    }

    public void checkIn(ExitNodeRecord node) {
        String errors = node.checkForErrors(false);
        if (!errors.equals("")) {
            throw new IllegalArgumentException(errors);
        }

        if (!registeredKeys.containsKey(node.serviceId)) {
            throw new IllegalArgumentException("Service ID is not registered.");
        }
        ExitNodeRecord oldNode = registeredKeys.get(node.serviceId);
        if (node.signature.equals(oldNode.signature) && oldNode.publicKey.equals(node.publicKey)) {
            registeredKeys.get(node.serviceId).checkIn();
        } else {
            throw new IllegalArgumentException(
                    "Public Key or Signature does not match existing registration.");
        }
    }

    void saveEdits() {
        PriorityQueue<ExitNodeRecord> newReadable = new PriorityQueue<ExitNodeRecord>();
        synchronized (mutableExitNodeList) {
            for (ExitNodeRecord node : mutableExitNodeList) {
                newReadable.add(node);
            }
        }
        readableExitNodeList = newReadable;
        saveToFile(new File(DATABASE_FILE));
    }

    public void getUpdatesSince(long lastUpdateTime, ContentHandler hd) {
        for (ExitNodeRecord node : readableExitNodeList) {
            if (node.createdTime > lastUpdateTime) {
            	try {
            		node.fullXML(hd);
            	} catch(Exception e) {
            		continue;
            	}
            } else {
                break;
            }
        }
    }

    private void saveToFile(File file) {
        try {
        	FileOutputStream fos = new FileOutputStream(file);
            OutputFormat of = new OutputFormat("XML", "ISO-8859-1", true);
            of.setIndent(1);
            of.setIndenting(true);
            XMLSerializer serializer = new XMLSerializer(fos, of);
            ContentHandler hd = serializer.asContentHandler();
            hd.startDocument();
            hd.startElement("", "", XMLConstants.EXIT_NODE_LIST, null);
            getUpdatesSince(0, hd);
            hd.endElement("", "", XMLConstants.EXIT_NODE_LIST);
            hd.endDocument();
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void readFromFile(File file) throws ParserConfigurationException, SAXException,
            IOException {
        FileInputStream in = new FileInputStream(file);
        List<ExitNodeRecord> savedNodes = new Parser(in).parseAsExitNodeList();
        for (ExitNodeRecord node : savedNodes) {
            add(node);
        }
        saveEdits();
        in.close();
    }

    private class DBCleaner implements Runnable {

        @Override
        public void run() {
            Timer cleaner = new Timer();

            cleaner.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (mutableExitNodeList) {
                        long startTime = System.currentTimeMillis();
                        Iterator<ExitNodeRecord> nodes = mutableExitNodeList.iterator();
                        while (nodes.hasNext()) {
                            ExitNodeRecord node = nodes.next();
                            // Check if the Node is too old with a grace period
                            if (startTime > node.lastCheckinTime + MAX_AGE + GRACE_PERIOD) {
                                nodes.remove();
                            }
                        }
                    }
                    saveEdits();
                }
            }, 0, MAX_AGE / 4);
        }
    }
}
