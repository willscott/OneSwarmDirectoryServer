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
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import edu.washington.cs.oneswarm.f2f.xml.XMLHelper;

public class DirectoryDB {
    private static final String DATABASE_FILE = "knownExitNodes.xml";
    // The max age that a ExitNode registration may have before it is deleted.
    private static final int MAX_AGE = 60 * 60 * 1000;
    private static final int GRACE_PERIOD = 60 * 1000;
    private final Map<Long, DirectoryRecord> registeredKeys;
    private PriorityQueue<DirectoryRecord> readableExitNodeList;
    private final PriorityQueue<DirectoryRecord> mutableExitNodeList;

    public DirectoryDB() throws ParserConfigurationException, SAXException, IOException {
        mutableExitNodeList = new PriorityQueue<DirectoryRecord>();
        registeredKeys = new HashMap<Long, DirectoryRecord>();
        try {
            File dbFile = new File(DATABASE_FILE);
            if (!dbFile.exists()) {
                dbFile.createNewFile();
                FileOutputStream fos = new FileOutputStream(dbFile);
                XMLHelper xmlOut = new XMLHelper(fos);
                xmlOut.startElement(XMLHelper.ROOT);
                xmlOut.endElement(XMLHelper.ROOT);
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

    public void add(DirectoryRecord node, XMLHelper xmlOut) throws SAXException {
        if (node.checkForErrors(true, xmlOut)) {
            return;
        }

        synchronized (registeredKeys) {
            if (registeredKeys.containsKey(node.serviceId)) {
                DirectoryRecord oldNode = registeredKeys.get(node.serviceId);
                if (!oldNode.publicKey.equals(node.publicKey)) {
                    xmlOut.writeStatus(XMLHelper.ERROR_DUPLICATE_SERVICE_ID,
                            "Key already exists in registry.");
                    return;
                }
                remove(oldNode);
            }
        }

        FutureTask<Boolean> verification = NodeVerifier.verify(node);
        try {
            if (verification.get(10, TimeUnit.SECONDS)) {
                synchronized (registeredKeys) {
                    registeredKeys.put(node.serviceId, node);
                }

                synchronized (mutableExitNodeList) {
                    mutableExitNodeList.add(node);
                }
                xmlOut.writeStatus(XMLHelper.STATUS_SUCCESS, "Registration Suceeded.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void remove(DirectoryRecord node) {
        synchronized (mutableExitNodeList) {
            mutableExitNodeList.remove(node);
        }
        synchronized (registeredKeys) {
            registeredKeys.remove(node.serviceId);
        }
    }

    public void checkIn(DirectoryRecord node, XMLHelper xmlOut) throws SAXException {
        if (node.checkForErrors(false, xmlOut)) {
            return;
        }

        if (!registeredKeys.containsKey(node.serviceId)) {
            xmlOut.writeStatus(XMLHelper.ERROR_UNREGISTERED_SERVICE_ID,
                    "Unregistered serviceId. Register this ExitNode before using checkin.");
            return;
        }
        DirectoryRecord oldNode = registeredKeys.get(node.serviceId);
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
        PriorityQueue<DirectoryRecord> newReadable = new PriorityQueue<DirectoryRecord>();
        synchronized (mutableExitNodeList) {
            for (DirectoryRecord node : mutableExitNodeList) {
                newReadable.add(node);
            }
        }
        readableExitNodeList = newReadable;
        saveToFile(new File(DATABASE_FILE));
    }

    public void getUpdatesSince(long lastUpdateTime, XMLHelper xmlOut) {
        for (DirectoryRecord node : readableExitNodeList) {
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
        xmlOut.startElement(XMLHelper.ROOT);
        getUpdatesSince(0, xmlOut);
        xmlOut.endElement(XMLHelper.ROOT);
        xmlOut.close();
        fos.close();
    }

    private void readFromFile(File file) throws ParserConfigurationException, SAXException,
            IOException {
        XMLHelper xmlOut = new XMLHelper(System.out);
        FileInputStream in = new FileInputStream(file);
        List<DirectoryRecord> savedNodes = new LinkedList<DirectoryRecord>();
        XMLHelper.parse(in, new DirectoryRecordHandler(savedNodes, xmlOut));
        for (DirectoryRecord node : savedNodes) {
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
                        Iterator<DirectoryRecord> nodes = mutableExitNodeList.iterator();
                        while (nodes.hasNext()) {
                            DirectoryRecord node = nodes.next();
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
