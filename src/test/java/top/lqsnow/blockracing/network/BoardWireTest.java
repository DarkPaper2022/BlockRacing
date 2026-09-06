package top.lqsnow.blockracing.network;

import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import static org.junit.jupiter.api.Assertions.*;

class BoardWireTest {
    @Test void requestCannotSupplyTeamOrProgress() {
        assertTrue(BoardWire.validRequest(new byte[]{0}));
        assertTrue(BoardWire.validRequest(new byte[]{1}));
        assertFalse(BoardWire.validRequest(new byte[]{}));
        assertFalse(BoardWire.validRequest(new byte[]{2}));
        assertFalse(BoardWire.validRequest(new byte[]{1, 1}));
    }
    @Test void utf8RoundTripAndPacketBound() throws Exception {
        Map<String, Object> data = Map.of("version", 1, "title", "用望远镜观察 20 种不同生物", "tasks", List.of());
        byte[] packet = BoardWire.encode(data);
        assertTrue(packet.length <= BoardWire.MAX_PACKET);
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(packet))) {
            assertEquals(data.get("title"), ((Map<?, ?>) new JSONParser().parse(new String(gzip.readAllBytes(), StandardCharsets.UTF_8))).get("title"));
        }
    }
    @Test void refusesOversizedJsonAndIncompressiblePayload() {
        assertThrows(IllegalArgumentException.class, () -> BoardWire.encode(Map.of("text", "x".repeat(BoardWire.MAX_JSON))));
        byte[] random = new byte[40_000];
        new Random(3).nextBytes(random);
        assertThrows(IllegalArgumentException.class, () -> BoardWire.encode(Map.of("text", Base64.getEncoder().encodeToString(random))));
    }
    @Test void mutualRemovalNeverMeansOurTeamWon() {
        assertEquals("resolved", TaskBoardBridge.status(false, false));
        assertEquals("resolved", TaskBoardBridge.status(false, true));
        assertEquals("queued", TaskBoardBridge.status(true, false));
        assertEquals("active", TaskBoardBridge.status(true, true));
    }

    @Test void writesRealServerCodecFixtureForClientContractTest() throws Exception {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", "DRAFTOUT:SPY_ON_20_UNIQUE_MOBS");
        row.put("index", 1);
        row.put("title", "用望远镜观察 20 种不同生物");
        row.put("requirement", "spy-unique:20");
        row.put("icon", "minecraft:spyglass");
        row.put("model", "");
        row.put("score", 5);
        row.put("status", "active");
        row.put("current", 14);
        row.put("required", 20);
        row.put("progressKnown", true);
        var data = Map.<String, Object>of("version", 1, "team", "red", "state", "INGAME",
                "chinese", true, "score", 4, "winScore", 100, "totalScore", 200, "tasks", List.of(row));
        byte[] encoded = BoardWire.encode(data);
        java.nio.file.Files.write(java.nio.file.Path.of("target/task-board-contract.bin"), encoded);
        assertTrue(encoded.length > 0 && encoded.length < BoardWire.MAX_PACKET);
    }
}
