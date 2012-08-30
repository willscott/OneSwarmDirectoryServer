package directoryServer;

import java.io.UnsupportedEncodingException;

import org.apache.xerces.impl.dv.util.Base64;
import org.xml.sax.SAXException;

import edu.washington.cs.oneswarm.f2f.xml.XMLHelper;

public class ProxyDirectoryRecord extends DirectoryRecord {
	private static final long serialVersionUID = 3296371858195528174L;
	int bandwidth;
    String version;
    public String exitPolicy;

    @Override
    public boolean checkForErrors(boolean fullCheckInclSignature, XMLHelper xmlOut)
            throws SAXException {
        boolean caughtErrors = super.checkForErrors(fullCheckInclSignature, xmlOut);
        if (bandwidth == 0) {
            xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST, "Invalid Advertized Bandwidth.");
            caughtErrors = true;
        }
        if (exitPolicy == null || exitPolicy.length() == 0) {
            xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST, "Invalid Exit Policy.");
            caughtErrors = true;
        }
        if (version == null || version.length() == 0) {
            xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST, "Invalid Version String.");
            caughtErrors = true;
        }
        return caughtErrors;
    }

    @Override
    public String type() {
        return XMLHelper.EXIT_NODE;
    }

    @Override
    protected byte[] hashBase() {
        try {
            return (serviceId + publicKey + this.nickname + bandwidth + exitPolicy + version)
                    .getBytes(XMLHelper.ENCODING);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void fullXML(XMLHelper xmlOut) throws SAXException {
        xmlOut.startElement(XMLHelper.EXIT_NODE);
        xmlOut.writeTag(XMLHelper.SERVICE_ID, Long.toString(this.serviceId));
        xmlOut.writeTag(XMLHelper.PUBLIC_KEY, this.publicKey);
        xmlOut.writeTag(XMLHelper.NICKNAME, this.nickname);
        xmlOut.writeTag(XMLHelper.BANDWIDTH, "" + this.bandwidth);
        xmlOut.writeTag(XMLHelper.EXIT_POLICY, this.exitPolicy);
        xmlOut.writeTag(XMLHelper.VERSION, this.version);
        xmlOut.writeTag(XMLHelper.SIGNATURE, Base64.encode(signature));
        xmlOut.endElement(XMLHelper.EXIT_NODE);
    }
}
