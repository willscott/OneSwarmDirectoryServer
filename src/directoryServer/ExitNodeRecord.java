package directoryServer;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;

import org.xml.sax.SAXException;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import edu.washington.cs.oneswarm.f2f.xml.XMLHelper;

public class ExitNodeRecord implements Comparable<ExitNodeRecord> {
    // Verification Constants
    // TODO (nick) find correct consts
    private static final int PUB_KEY_LENGTH = 162; // length of Key.getEncoded()
    private static final int SIG_LENGTH = 128; // Bytes after Base64.decode
    private static final int MIN_NICKNAME_LENGTH = 3;

    long createdTime; // As provided by System.currentTimeMillis();
    long lastCheckinTime; // Essentially a Keep Alivelong serviceId;
    long serviceId;
    String publicKey;
    String nickname;
    int bandwidth;
    String version;
    public String exitPolicy;
    public byte[] signature;

    public ExitNodeRecord() {
        super();
        createdTime = System.currentTimeMillis();
        lastCheckinTime = createdTime;
    }

    public void checkIn() {
        lastCheckinTime = System.currentTimeMillis();
    }

    public boolean checkForErrors(boolean fullCheckInclSignature, XMLHelper xmlOut)
            throws SAXException {
        boolean caughtErrors = false;
        if (lastCheckinTime < createdTime) {
            xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST, "Broken Timestamp. Created at "
                    + createdTime + " Last check-in " + lastCheckinTime + ".");
            caughtErrors = true;
        }
        if (serviceId == 0) {
            xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST, "Invalid Service ID.");
            caughtErrors = true;
        }
        PublicKey pubKey = decodeKeyString(publicKey);
        if (pubKey == null || pubKey.getAlgorithm() == "RSA" || pubKey.getFormat() == "PKCS#8"
                || pubKey.getEncoded().length != PUB_KEY_LENGTH) {
            xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST, "Invalid RSA Public Key.");
            caughtErrors = true;
        }
        if (fullCheckInclSignature) {
            if (nickname == null || nickname.length() < MIN_NICKNAME_LENGTH) {
                xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST,
                        "Invalid Nickname. Must be 3 or more characters.");
                caughtErrors = true;
            }
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
            if (signature == null || signature.length != SIG_LENGTH) {
                xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST, "Invalid Signature length. Must be "
                        + SIG_LENGTH + "bytes.");
                caughtErrors = true;
            }
            if (!caughtErrors) {
                Signature sig;
                try {
                    sig = Signature.getInstance("SHA1withRSA");
                    try {
                        sig.initVerify(pubKey);
                        sig.update(hashBase());
                        if (!sig.verify(signature)) {
                            xmlOut.writeStatus(XMLHelper.ERROR_INVALID_SIGNATURE,
                                    "Signature Verification Failed.");
                            caughtErrors = true;
                        }
                    } catch (Exception e) {
                        xmlOut.writeStatus(XMLHelper.ERROR_INVALID_SIGNATURE, "Invalid Signature: "
                                + e.getMessage());
                        caughtErrors = true;
                    }
                } catch (NoSuchAlgorithmException e) {
                    xmlOut.writeStatus(XMLHelper.ERROR_GENERAL_SERVER,
                            "Signature Verification failed on the Server");
                    e.printStackTrace();
                }

            }
        }
        return caughtErrors;
    }

    private byte[] hashBase() {
        try {
            return (publicKey + this.nickname + bandwidth + exitPolicy + version)
                    .getBytes(XMLHelper.ENCODING);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Decodes the String format of a key. See getPublicKeyString for format.
     * 
     * @param key
     * @return
     */
    private PublicKey decodeKeyString(String key) {
        if (key == null || key.length() < 1) {
            return null;
        }
        final String[] parts = key.split(":");
        return new PublicKey() {
            private static final long serialVersionUID = 4008509182035615274L;

            @Override
            public String getAlgorithm() {
                return parts[0];
            }

            @Override
            public String getFormat() {
                return parts[1];
            }

            @Override
            public byte[] getEncoded() {
                return Base64.decode(parts[2]);
            }
        };
    }

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

    @Override
    public int compareTo(ExitNodeRecord other) {
        if (this.createdTime > other.createdTime) {
            return -1;
        }
        if (this.createdTime == other.createdTime) {
            return 0;
        }
        return 1;
    }
}
