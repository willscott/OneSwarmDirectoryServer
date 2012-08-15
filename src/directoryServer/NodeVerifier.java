package directoryServer;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class NodeVerifier implements Callable<Boolean> {
	/**
	 * Verify an exit node record.
	 * A final return value of true indicates the record can be
	 * assumed to be legitimate.
	 * 
	 * @param record The Published record to verify.
	 * @return A future task indicating the progress of verification.
	 */
	public static FutureTask<Boolean> verify(ExitNodeRecord record) {
		return new FutureTask<Boolean>(new NodeVerifier(record));
	}
	
	private final ExitNodeRecord record;
	private NodeVerifier(ExitNodeRecord record) {
		this.record = record;
	}

	@Override
	public Boolean call() throws Exception {
		if (!this.record.exitPolicy.contains("allow")) {
			return false;
		}
		return true;
	}
}