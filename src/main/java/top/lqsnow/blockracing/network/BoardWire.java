package top.lqsnow.blockracing.network;

import org.json.simple.JSONValue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Versioned, bounded UTF-8 JSON in a single Paper/Fabric custom payload. */
public final class BoardWire {
    public static final String REQUEST = "blockracing:board_request";
    public static final String SNAPSHOT = "blockracing:board_v1";
    public static final int MAX_PACKET = 30_000;
    public static final int MAX_JSON = 512 * 1024;
    private BoardWire() { }

    public static byte[] encode(Map<String, Object> data) {
        byte[] json = JSONValue.toJSONString(data).getBytes(StandardCharsets.UTF_8);
        if (json.length > MAX_JSON) throw new IllegalArgumentException("Board JSON exceeds limit");
        try {
            var bytes = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(json); }
            if (bytes.size() > MAX_PACKET) throw new IllegalArgumentException("Board packet exceeds limit");
            return bytes.toByteArray();
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }

    public static boolean validRequest(byte[] bytes) {
        return bytes.length == 1 && (bytes[0] == 0 || bytes[0] == 1);
    }
}
