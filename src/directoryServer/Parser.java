package directoryServer;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.sun.org.apache.xml.internal.security.exceptions.Base64DecodingException;
import com.sun.org.apache.xml.internal.security.utils.Base64;

public class Parser {
    private ExitNodeRecord tempNode;
    private String tempVal;

    private final InputStream in;

    public Parser(InputStream in) {
        this.in = in;
    }

    /**
     * Adds ExitNodes found in this parser's XML input to the DB.
     * 
     * @param max
     *            The maximum number of ExitNodes to Register or, 0 to signal
     *            unlimited.
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    public List<ExitNodeRecord> parseAsExitNodeList() throws ParserConfigurationException,
            SAXException, IOException {
        final List<ExitNodeRecord> list = new LinkedList<ExitNodeRecord>();

        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();

        parser.parse(in, new ExitNodeListHandler(list));

        return list;
    }

    private class ExitNodeListHandler extends DefaultHandler {
        private final List<ExitNodeRecord> exitNodes;
        private final DateFormat formatter = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy");

        public ExitNodeListHandler(List<ExitNodeRecord> list) {
            this.exitNodes = list;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if (qName.equals(XMLConstants.EXIT_NODE)) {
                tempNode = new ExitNodeRecord();
            }
        }

        @Override
        public void characters(char ch[], int start, int length) throws SAXException {
            tempVal = new String(ch, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (qName.equalsIgnoreCase(XMLConstants.EXIT_NODE)) {
                exitNodes.add(tempNode);
            } else if (qName.equalsIgnoreCase(XMLConstants.SERVICE_ID)) {
                tempNode.serviceId = Long.parseLong(tempVal);
            } else if (qName.equalsIgnoreCase(XMLConstants.PUBLIC_KEY)) {
                tempNode.publicKey = tempVal;
            } else if (qName.equalsIgnoreCase(XMLConstants.NICKNAME)) {
                tempNode.nickname = tempVal;
            } else if (qName.equalsIgnoreCase(XMLConstants.BANDWIDTH)) {
                tempNode.bandwidth = Integer.parseInt(tempVal);
            } else if (qName.equalsIgnoreCase(XMLConstants.EXIT_POLICY)) {
                tempNode.exitPolicy = tempVal;
            } else if (qName.equalsIgnoreCase(XMLConstants.VERSION)) {
                tempNode.version = tempVal;
            } else if (qName.equalsIgnoreCase(XMLConstants.SIGNATURE)) {
                try {
                    tempNode.signature = Base64.decode(tempVal);
                } catch (Base64DecodingException e) {
                    throw new IllegalArgumentException(e);
                }
            } else if (qName.equalsIgnoreCase(XMLConstants.EXIT_NODE_LIST)) {
            } else {
                throw new IllegalArgumentException("Unrecognized Tag: " + qName);
            }
        }

        @Override
        public void endDocument() throws SAXException {
            synchronized (exitNodes) {
            }
        }
    }
}
