package directoryServer;

import java.security.Signature;

import edu.washington.cs.oneswarm.f2f.servicesharing.ExitNodeInfo;

public class ExitNodeRecord extends ExitNodeInfo {
    // Verification Constants
    // TODO (nick) find correct consts
    private static final int PUB_KEY_LENGTH = 162; // length of Key.getEncoded()
    private static final int SIG_LENGTH = 128; // Bytes after Base64.decode
    private static final int MIN_NICKNAME_LENGTH = 3;

    long createdTime; // As provided by System.currentTimeMillis();
    long lastCheckinTime; // Essentially a Keep Alive
    public String policy;
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
        if (this.getId() == 0) {
            errors += "\tInvalid Service ID\n";
        }
        if (this.getPublicKey() == null || this.getPublicKey().getAlgorithm() == "RSA"
                || this.getPublicKey().getFormat() == "PKCS#8"
                || this.getPublicKey().getEncoded().length != PUB_KEY_LENGTH) {
            errors += "\tInvalid RSA Public Key\n";
        }
        if (fullCheckInclSignature) {
            if (this.getNickname() == null || this.getNickname().length() < MIN_NICKNAME_LENGTH) {
                errors += "\tInvalid Nickname. Must be 3 or more characters\n";
            }
            if (this.getAdvertizedBandwith() == 0) {
                errors += "\tInvalid Advertized Bandwidth\n";
            }
            String exitPolicy = this.getExitPolicy();
            if (exitPolicy == null || exitPolicy.length() == 0) {
                errors += "\tInvalid Exit Policy\n";
            }
            if (this.getOnlineSinceDate() == null) {
                errors += "\tInvalid Online Since date\n";
            }
            if (this.getVersion() == null || this.getVersion().length() == 0) {
                errors += "\tInvalid Version String\n";
            }
            if (signature == null || signature.length != SIG_LENGTH) {
                errors += "\tInvalid Signature\n" + signature.length;
            }
            if (errors.equals("")) {
                try {
                    Signature sig = Signature.getInstance("SHA1withRSA");
                    sig.initVerify(this.getPublicKey());
                    sig.update(this.hashBase());
                    if (!sig.verify(signature)) {
                        errors += "\tSignature Verification Failed\n";
                        // TODO (nick) remove println
                        errors += this.fullXML() + "\n";
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return errors;
    }

    @Override
    public int compareTo(ExitNodeInfo other) {
        if (!(other instanceof ExitNodeRecord)) {
            throw new IllegalArgumentException("Cannot be compared to an ExitNodeInfo");
        }
        if (this.createdTime > ((ExitNodeRecord) other).createdTime) {
            return -1;
        }
        if (this.createdTime == ((ExitNodeRecord) other).createdTime) {
            return 0;
        }
        return 1;
    }
}
