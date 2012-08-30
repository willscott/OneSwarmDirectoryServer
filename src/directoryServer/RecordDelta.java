package directoryServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class RecordDelta {
	public RecordDelta(DirectoryRecord record, boolean update) {
		this.record = record;
		if (update) {
			this.type = TYPE.UPDATE;
			this.serviceId = record.serviceId;
			this.lastCheckinTime = record.lastCheckinTime;
		} else {
			this.type = TYPE.NEWREG;
			try {
				ByteArrayOutputStream serializer = new ByteArrayOutputStream();
				new ObjectOutputStream(serializer).writeObject(record);
				this.data = serializer.toByteArray();
			} catch (Exception e) {
				this.valid = false;
				return;
			}
		}
	}
	
	public RecordDelta(byte[] serialized) {
		if (serialized[0] == 0x01) { // checkin
			this.type = TYPE.UPDATE;
			// long serviceid, long lastUpdate
			byte[] longBuffer;
			
			longBuffer = Arrays.copyOfRange(serialized, 1, 9);
			this.serviceId = ByteBuffer.wrap(longBuffer).getLong();
			longBuffer = Arrays.copyOfRange(serialized, 9, 17);
			this.lastCheckinTime = ByteBuffer.wrap(longBuffer).getLong();
		} else if (serialized[0] == 0x02) { // new registration.
			this.type = TYPE.NEWREG;
			try {
				ByteArrayInputStream deserializer = new ByteArrayInputStream(serialized, 1, serialized.length - 1);
				this.record = (DirectoryRecord)new ObjectInputStream(deserializer).readObject();
			} catch (Exception e) {
				this.valid = false;
				return;
			}
			this.lastCheckinTime = System.currentTimeMillis();
		} else {
			this.valid = false;
		}
	}
	
	enum TYPE {
		UPDATE,
		NEWREG
	};
	public TYPE type;
	public long serviceId;
	public long lastCheckinTime;
    public byte[] data;
    public DirectoryRecord record;
    boolean valid = true;

	public byte[] toByteArray() {
		if (this.type == TYPE.UPDATE) {
			byte[] output = new byte[17];
			output[0] = 0x01;
			ByteBuffer.wrap(output).putLong(1, this.serviceId);
			ByteBuffer.wrap(output).putLong(9, this.lastCheckinTime);
			return output;
		} else if (this.type == TYPE.NEWREG) {
			ByteBuffer output = ByteBuffer.allocate(this.data.length + 1);
			output.put((byte) 0x02); // Type
			output.put(data);
			output.flip();
			return output.array();
		} else {
			return new byte[] {};
		}
	}
}
