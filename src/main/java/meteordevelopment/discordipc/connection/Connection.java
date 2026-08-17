package meteordevelopment.discordipc.connection;

import com.google.gson.JsonObject;
import meteordevelopment.discordipc.Opcode;
import meteordevelopment.discordipc.Packet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class Connection {
    public static Connection open(Consumer<Packet> callback) {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) return null;

        for (int i = 0; i < 10; i++) {
            try {
                return new WinConnection("\\\\.\\pipe\\discord-ipc-" + i, callback);
            } catch (IOException ignored) {
            }
        }

        return null;
    }

    public void write(Opcode opcode, JsonObject json) {
        json.addProperty("nonce", UUID.randomUUID().toString());

        byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(data.length + 8);
        buffer.putInt(Integer.reverseBytes(opcode.ordinal()));
        buffer.putInt(Integer.reverseBytes(data.length));
        buffer.put(data);
        buffer.rewind();

        write(buffer);
    }

    protected abstract void write(ByteBuffer buffer);

    public abstract void close();
}
