package directoryServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.xerces.impl.dv.util.Base64;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;

/**
 * This class coordinates state between different instances of "the same server."
 * To have multiple servers, which can be used together, all machines must have the
 * same "key.store" identity file, and a knownParters.txt file listing identities of
 * other instances.
 *
 * @author willscott
 *
 */
public class ServiceCoordinator {
	private final DirectoryDB db;
	private final Signature authority;
	private final File partnerFile;
	private final List<URL> partners;
	private ExecutorService executor;
	private static final String PATH = "coord";
	
	public ServiceCoordinator(DirectoryDB db, File partners, Signature authority) {
		this.db = db;
		this.authority = authority;
		this.partnerFile = partners;
		this.partners = new ArrayList<URL>();
		
		this.setup();
	}

	public void setup() {
		// Load in the partners list.
		try {
			BufferedReader br = new BufferedReader(new FileReader(partnerFile));
			String line;
			while((line = br.readLine()) != null) {
				if (line.length() > 0) {
					partners.add(new URL(line + "/" + PATH));
				}
			}
		} catch(IOException e) {
			System.err.println("Partner Initialization Failed!");
			e.printStackTrace();
			return;
		}
		
		OSDirectoryServer.instance.jettyServer.addHandler(new CoordinatorHandler(this));
		
		this.executor = this.getExecutor();
	}
	
	public class CoordinatorHandler extends org.mortbay.jetty.handler.AbstractHandler {
		private ServiceCoordinator state;
		private CoordinatorHandler(ServiceCoordinator sc) {
			this.state = sc;
		}

		@Override
		public void handle(String path, HttpServletRequest req,
				HttpServletResponse resp, int dispatch) throws IOException,
				ServletException {
            Request request = (req instanceof Request) ? (Request) req : HttpConnection
                    .getCurrentConnection().getRequest();

            if(path == PATH) {
            	try {
            		// Validate the request.
            		String payload = request.getParameter("p");
            		byte[] pb = Base64.decode(payload);
            		String digest = request.getParameter("d");
            		byte[] db = Base64.decode(digest);
            		synchronized(this.state.authority) {
            			this.state.authority.update(pb);
            			if (!this.state.authority.verify(db)) {
            				return;
            			}
            		}

            		// Extract the command.
            		RecordDelta d = new RecordDelta(pb);
            		if (!d.valid) {
            			return;
            		}
            		this.state.db.merge(d);

            		request.setHandled(true);
            	} catch (Exception e) {
            		return;
            	}
			}
		}
	}

	public ExecutorService getExecutor() {
		LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
		return new ThreadPoolExecutor(1, 2, 1000, TimeUnit.MILLISECONDS, queue);
	}

	public void add(final DirectoryRecord node, final boolean update) {
		Runnable task = new Runnable() {
			@Override
			public void run() {
				byte[] payload = new RecordDelta(node, update).toByteArray();
				byte[] signature;
				synchronized(ServiceCoordinator.this.authority) {
					try {
						ServiceCoordinator.this.authority.update(payload);
						signature = ServiceCoordinator.this.authority.sign();
					} catch (Exception e) {
						e.printStackTrace();
						return;
					}
				}
				String pb = Base64.encode(payload);
				String db = Base64.encode(signature);

				for (URL u : ServiceCoordinator.this.partners) {
					try {
						HttpURLConnection conn = (HttpURLConnection)u.openConnection();
						conn.addRequestProperty("p", pb);
						conn.addRequestProperty("d", db);
						conn.getContent();
					} catch (IOException e) {
						e.printStackTrace();
						continue;
					}
				}
			}
		};
		this.executor.execute(task);
	}
}
