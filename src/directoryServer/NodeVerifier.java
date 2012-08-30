package directoryServer;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class NodeVerifier implements Callable<Boolean> {
    /**
     * Verify an exit node record. A final return value of true indicates the
     * record can be assumed to be legitimate.
     * 
     * @param record
     *            The Published record to verify.
     * @return A future task indicating the progress of verification.
     */
    public static FutureTask<Boolean> verify(DirectoryRecord record) {
        return new FutureTask<Boolean>(new NodeVerifier(record));
    }

    private final DirectoryRecord record;

    private NodeVerifier(DirectoryRecord record) {
        this.record = record;
    }

    @Override
    public Boolean call() throws Exception {
        // Make sure the node will accept some connections.
        if (record instanceof ProxyDirectoryRecord) {
            ProxyDirectoryRecord exitNode = (ProxyDirectoryRecord) record;
            if (!exitNode.exitPolicy.contains("allow")) {
                return false;
            }
            // TODO (willscott) See if the exit node can be reached.
            return true;
        } else if (record instanceof PortDirectoryRecord) {
            // TODO (willscott) See if the OS Website can be reached
            return true;
        } else {
            // If the DirectoryRecord is not a known type, dont allow
            return false;
        }
    }
}