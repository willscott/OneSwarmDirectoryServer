package directoryServer;

import java.io.UnsupportedEncodingException;

import org.xml.sax.SAXException;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import edu.washington.cs.oneswarm.f2f.xml.XMLHelper;

public class ServiceRecord extends DirectoryRecord {

    @Override
    public void fullXML(XMLHelper xmlOut) throws SAXException {
        xmlOut.startElement(XMLHelper.SERVICE);
        xmlOut.writeTag(XMLHelper.SERVICE_ID, Long.toString(this.serviceId));
        xmlOut.writeTag(XMLHelper.PUBLIC_KEY, this.publicKey);
        xmlOut.writeTag(XMLHelper.NICKNAME, this.nickname);
        xmlOut.writeTag(XMLHelper.SIGNATURE, Base64.encode(signature));
        xmlOut.endElement(XMLHelper.SERVICE);
    }

    @Override
    protected byte[] hashBase() {
        try {
            return (serviceId + publicKey + this.nickname).getBytes(XMLHelper.ENCODING);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String type() {
        return XMLHelper.SERVICE;
    }

}
