package directoryServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Timer;
import java.util.TimerTask;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import edu.washington.cs.oneswarm.f2f.xml.XMLHelper;

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
                FileOutputStream fos = new FileOutputStream(dbFile);
                XMLHelper xmlOut = new XMLHelper(fos);
                xmlOut.startElement(XMLHelper.EXIT_NODE_LIST);
                xmlOut.endElement(XMLHelper.EXIT_NODE_LIST);
                xmlOut.close();
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

    public void add(ExitNodeRecord node, XMLHelper xmlOut) throws SAXException {
        if (node.checkForErrors(true, xmlOut)) {
            return;
        }

        synchronized (registeredKeys) {
            if (registeredKeys.containsKey(node.serviceId)) {
                ExitNodeRecord oldNode = registeredKeys.get(node.serviceId);
                if (!oldNode.publicKey.equals(node.publicKey)) {
                    xmlOut.writeStatus(XMLHelper.ERROR_DUPLICATE_SERVICE_ID,
                            "Key already exists in registry.");
                    return;
                }
                remove(oldNode);
            }
            registeredKeys.put(node.serviceId, node);
        }

        synchronized (mutableExitNodeList) {
            mutableExitNodeList.add(node);
        }
        xmlOut.writeStatus(XMLHelper.STATUS_SUCCESS, "Registration Suceeded.");
    }

    void remove(ExitNodeRecord node) {
        synchronized (mutableExitNodeList) {
            mutableExitNodeList.remove(node);
        }
        synchronized (registeredKeys) {
            registeredKeys.remove(node.serviceId);
        }
    }

    public void checkIn(ExitNodeRecord node, XMLHelper xmlOut) throws SAXException {
        if (node.checkForErrors(false, xmlOut)) {
            return;
        }

        if (!registeredKeys.containsKey(node.serviceId)) {
            xmlOut.writeStatus(XMLHelper.ERROR_UNREGISTERED_SERVICE_ID,
                    "Unregistered serviceId. Register this ExitNode before using checkin.");
            return;
        }
        ExitNodeRecord oldNode = registeredKeys.get(node.serviceId);
        if (Arrays.equals(node.signature, oldNode.signature)
                && oldNode.publicKey.equals(node.publicKey)) {
            registeredKeys.get(node.serviceId).checkIn();
        } else {
            xmlOut.writeStatus(XMLHelper.ERROR_INVALID_SIGNATURE,
                    "Public Key or Signature does not match existing registration.");
            return;
        }
        xmlOut.writeStatus(XMLHelper.STATUS_SUCCESS, "Checkin Suceeded.");
    }

    void saveEdits() throws SAXException, IOException {
        PriorityQueue<ExitNodeRecord> newReadable = new PriorityQueue<ExitNodeRecord>();
        synchronized (mutableExitNodeList) {
            for (ExitNodeRecord node : mutableExitNodeList) {
                newReadable.add(node);
            }
        }
        readableExitNodeList = newReadable;
        saveToFile(new File(DATABASE_FILE));
    }

    public void getUpdatesSince(long lastUpdateTime, XMLHelper xmlOut) {
        for (ExitNodeRecord node : readableExitNodeList) {
            if (node.createdTime > lastUpdateTime) {
                try {
                    node.fullXML(xmlOut);
                } catch (Exception e) {
                    continue;
                }
            } else {
                break;
            }
        }
    }

    public void clear() throws SAXException, IOException {
        mutableExitNodeList.clear();
        registeredKeys.clear();
        saveEdits();
    }

    private void saveToFile(File file) throws SAXException, IOException {
        FileOutputStream fos = new FileOutputStream(file);
        XMLHelper xmlOut = new XMLHelper(fos);
        xmlOut.startElement(XMLHelper.EXIT_NODE_LIST);
        getUpdatesSince(0, xmlOut);
        xmlOut.endElement(XMLHelper.EXIT_NODE_LIST);
        xmlOut.close();
        fos.close();
    }

    private void readFromFile(File file) throws ParserConfigurationException, SAXException,
            IOException {
        XMLHelper xmlOut = new XMLHelper(System.out);
        FileInputStream in = new FileInputStream(file);
        List<ExitNodeRecord> savedNodes = new LinkedList<ExitNodeRecord>();
        XMLHelper.parse(in, new ExitNodeListHandler(savedNodes, xmlOut));
        for (ExitNodeRecord node : savedNodes) {
            add(node, xmlOut);
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
                    try {
                        saveEdits();
                    } catch (SAXException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, 0, MAX_AGE / 4);
        }
    }
}
