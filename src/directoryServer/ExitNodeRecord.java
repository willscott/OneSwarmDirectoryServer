package directoryServer;

import java.io.UnsupportedEncodingException;
import java.security.PublicKey;
import java.security.Signature;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

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

    public String checkForErrors(boolean fullCheckInclSignature) {
        String errors = "";
        if (lastCheckinTime < createdTime) {
            errors += "\tBroken Timestamp. Created at " + createdTime + " Last check-in "
                    + lastCheckinTime + "\n";
        }
        if (serviceId == 0) {
            errors += "\tInvalid Service ID\n";
        }
        PublicKey pubKey = decodeKeyString(publicKey);
        if (pubKey == null || pubKey.getAlgorithm() == "RSA" || pubKey.getFormat() == "PKCS#8"
                || pubKey.getEncoded().length != PUB_KEY_LENGTH) {
            errors += "\tInvalid RSA Public Key\n";
        }
        if (fullCheckInclSignature) {
            if (nickname == null || nickname.length() < MIN_NICKNAME_LENGTH) {
                errors += "\tInvalid Nickname. Must be 3 or more characters\n";
            }
            if (bandwidth == 0) {
                errors += "\tInvalid Advertized Bandwidth\n";
            }
            if (exitPolicy == null || exitPolicy.length() == 0) {
                errors += "\tInvalid Exit Policy\n";
            }
            if (version == null || version.length() == 0) {
                errors += "\tInvalid Version String\n";
            }
            if (signature == null || signature.length != SIG_LENGTH) {
                errors += "\tInvalid Signature\n" + signature.length;
            }
            if (errors.equals("")) {
                try {
                    Signature sig = Signature.getInstance("SHA1withRSA");
                    sig.initVerify(pubKey);
                    sig.update(hashBase());
                    if (!sig.verify(signature)) {
                        errors += "\tSignature Verification Failed\n";
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return errors;
    }

    private byte[] hashBase() {
        try {
            return (publicKey + this.nickname + bandwidth + this.exitPolicy.toString() + this.version)
                    .getBytes(XMLConstants.ENCODING);
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

    public String fullXML() {
        // TODO (willscott) XML with same info as ExitNodeInfo
        return "TODO";
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
