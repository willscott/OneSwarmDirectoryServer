package directoryServer;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;

import org.xml.sax.SAXException;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import edu.washington.cs.oneswarm.f2f.xml.XMLHelper;

abstract class DirectoryRecord implements Comparable<ExitNodeRecord> {
    // Verification Constants
    private static final int PUB_KEY_LENGTH = 162; // length of Key.getEncoded()
    private static final int SIG_LENGTH = 128; // Bytes after Base64.decode
    private static final int MIN_NICKNAME_LENGTH = 3;

    long createdTime; // As provided by System.currentTimeMillis();
    long lastCheckinTime; // Essentially a Keep Alive
    long serviceId;
    String publicKey;
    String nickname;
    public byte[] signature;

    public DirectoryRecord() {
        createdTime = System.currentTimeMillis();
        lastCheckinTime = createdTime;
    }

    abstract public void fullXML(XMLHelper xmlOut) throws SAXException;

    abstract protected byte[] hashBase();

    abstract public String type();

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
            if (signature == null || signature.length != SIG_LENGTH) {
                xmlOut.writeStatus(XMLHelper.ERROR_BAD_REQUEST,
                        "Invalid Signature length. Must be " + SIG_LENGTH + "bytes.");
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
