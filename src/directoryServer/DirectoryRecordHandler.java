package directoryServer;

import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;

import edu.washington.cs.oneswarm.f2f.xml.XMLHelper;

public class DirectoryRecordHandler extends DefaultHandler {
    private DirectoryRecord tempNode;
    private String tempVal;
    private boolean errors = false;
    private final List<DirectoryRecord> exitNodes;
    private final XMLHelper xmlOut;

    public DirectoryRecordHandler(List<DirectoryRecord> list, XMLHelper xmlOut) {
        this.exitNodes = list;
        this.xmlOut = xmlOut;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (qName.equalsIgnoreCase(XMLHelper.EXIT_NODE)) {
            tempNode = new ExitNodeRecord();
        } else if (qName.equalsIgnoreCase(XMLHelper.SERVICE)) {
            tempNode = new ServiceRecord();
        }
        errors = false;
    }

    @Override
    public void characters(char ch[], int start, int length) throws SAXException {
        tempVal = new String(ch, start, length);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (tempNode != null && qName.equalsIgnoreCase(tempNode.type())) {
            if (!errors) {
                exitNodes.add(tempNode);
            }
        } else if (qName.equalsIgnoreCase(XMLHelper.SERVICE_ID)) {
            tempNode.serviceId = Long.parseLong(tempVal);
        } else if (qName.equalsIgnoreCase(XMLHelper.PUBLIC_KEY)) {
            tempNode.publicKey = tempVal;
        } else if (qName.equalsIgnoreCase(XMLHelper.NICKNAME)) {
            tempNode.nickname = tempVal;
        } else if (qName.equalsIgnoreCase(XMLHelper.BANDWIDTH)) {
            if (tempNode instanceof ExitNodeRecord) {
                ((ExitNodeRecord) tempNode).bandwidth = Integer.parseInt(tempVal);
            }
        } else if (qName.equalsIgnoreCase(XMLHelper.EXIT_POLICY)) {
            if (tempNode instanceof ExitNodeRecord) {
                ((ExitNodeRecord) tempNode).exitPolicy = tempVal;
            }
        } else if (qName.equalsIgnoreCase(XMLHelper.VERSION)) {
            if (tempNode instanceof ExitNodeRecord) {
                ((ExitNodeRecord) tempNode).version = tempVal;
            }
        } else if (qName.equalsIgnoreCase(XMLHelper.SIGNATURE)) {
            try {
                tempNode.signature = Base64.decode(tempVal);
            } catch (Base64DecodingException e) {
                xmlOut.startElement(XMLHelper.EXIT_NODE);
                xmlOut.writeTag(XMLHelper.SERVICE_ID, tempNode.serviceId + "");
                xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST,
                        "Signature is not in valid Base64 encoding.");
                xmlOut.endElement(XMLHelper.EXIT_NODE);
                errors = true;
            }
        } else if (qName.equalsIgnoreCase(XMLHelper.ROOT)) {
        } else {
            xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST, "Unrecognized Tag: " + qName);
            errors = true;
        }
    }

    @Override
    public void endDocument() throws SAXException {
    }
}
