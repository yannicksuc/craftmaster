package fr.lordfinn.craftmaster.regie;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RegieTest {

    // ------------------------------------------------------------------ WAV

    @Test
    void wavHeaderIsStandard16kMono() {
        byte[] pcm = new byte[32000];   // 1 s
        pcm[0] = 1;
        byte[] wav = Wav.encode(pcm);
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(44 + 32000, wav.length);
        assertEquals("RIFF", new String(wav, 0, 4));
        assertEquals(36 + 32000, b.getInt(4));
        assertEquals("WAVEfmt ", new String(wav, 8, 8));
        assertEquals(16, b.getInt(16));
        assertEquals(1, b.getShort(20));        // PCM
        assertEquals(1, b.getShort(22));        // mono
        assertEquals(16000, b.getInt(24));
        assertEquals(32000, b.getInt(28));      // byte rate
        assertEquals(2, b.getShort(32));
        assertEquals(16, b.getShort(34));
        assertEquals("data", new String(wav, 36, 4));
        assertEquals(32000, b.getInt(40));
        assertEquals(1, wav[44]);
        assertEquals(1.0, Wav.seconds(pcm.length), 1e-9);
    }

    @Test
    void wavRoundTripAndOddLength() {
        byte[] pcm = {1, 2, 3, 4, 5};
        assertArrayEquals(new byte[]{1, 2, 3, 4}, Wav.pcmOf(Wav.encode(pcm)));
    }

    @Test
    void wavReaderSkipsChunksAndRefusesOtherFormats() {
        byte[] wav = Wav.encode(new byte[]{9, 9});
        // Insert a LIST chunk between fmt and data, as Windows TTS does
        byte[] list = {'L', 'I', 'S', 'T', 3, 0, 0, 0, 'a', 'b', 'c', 0};
        byte[] withList = new byte[wav.length + list.length];
        System.arraycopy(wav, 0, withList, 0, 36);
        System.arraycopy(list, 0, withList, 36, list.length);
        System.arraycopy(wav, 36, withList, 36 + list.length, wav.length - 36);
        assertArrayEquals(new byte[]{9, 9}, Wav.pcmOf(withList));

        byte[] stereo = wav.clone();
        stereo[22] = 2;
        assertThrows(IllegalArgumentException.class, () -> Wav.pcmOf(stereo));
        assertThrows(IllegalArgumentException.class, () -> Wav.pcmOf("hello world!".getBytes()));
    }

    @Test
    void level() {
        assertEquals(0f, Wav.level(new byte[100], 100));
        byte[] loud = new byte[100];
        for (int i = 0; i < 100; i += 2) { loud[i] = (byte) 0xFF; loud[i + 1] = 0x7F; }
        assertEquals(1f, Wav.level(loud, 100));
    }

    // ------------------------------------------------------------------ JSON

    @Test
    void actionsKeepIntegersAsIntegers() {
        List<Map<String, Object>> a = RegieJson.readActions(
                "[{\"type\":\"score.add\",\"player\":\"Ensuri\",\"points\":3,\"season\":4},"
                        + "{\"type\":\"manche.create\",\"name\":\"Pont\",\"aliases\":[\"pont\"],\"onStage\":true,\"timer\":90},"
                        + "{\"nope\":1}, 5, {\"type\":7}]");
        assertEquals(2, a.size());
        assertEquals(3, a.get(0).get("points"));
        assertInstanceOf(Integer.class, a.get(0).get("season"));
        assertEquals(List.of("pont"), a.get(1).get("aliases"));
        assertEquals(true, a.get(1).get("onStage"));
        assertThrows(IllegalArgumentException.class, () -> RegieJson.readActions("{\"type\":\"x\"}"));
        assertThrows(IllegalArgumentException.class, () -> RegieJson.readActions("not json"));
    }

    @Test
    void contextIsWrittenWithNullsAndAccents() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("currentManche", null);
        m.put("name", "Épreuve <1>");
        assertEquals("{\"currentManche\":null,\"name\":\"Épreuve <1>\"}", RegieJson.write(m));
    }

    @Test
    void sayIsRemovedFromTheActionsSentToTheServer() {
        assertEquals("[{\"type\":\"curtain.open\"}]",
                RegieJson.withoutSay("[{\"type\":\"say\",\"text\":\"ok\"},{\"type\":\"curtain.open\"}]"));
        assertEquals("[]", RegieJson.withoutSay("[{\"type\":\"say\",\"text\":\"?\"}]"));
        assertEquals("[]", RegieJson.withoutSay("garbage"));
    }

    // ------------------------------------------------------------------ service

    @Test
    void serviceRequestBody() {
        String body = RegieService.body(new byte[]{1, 2, 3}, "{\"a\":1}");
        assertEquals("{\"audio\":\"" + Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}) + "\",\"context\":{\"a\":1}}", body);
        assertTrue(RegieService.body(new byte[0], null).endsWith("\"context\":{}}"));
    }

    @Test
    void serviceReplyParsing() {
        RegieService.Reply r = RegieService.parse("{\"transcript\":\"trois points pour Juste\",\"actions\":[{\"type\":\"score.add\","
                + "\"player\":\"JustEdible\",\"points\":3,\"manche\":\"current\"}],\"reply\":\"\",\"warnings\":[\"w\"],\"error\":null,"
                + "\"timings\":{\"total\":1.2}}");
        assertNull(r.error());
        assertEquals("trois points pour Juste", r.transcript());
        assertEquals(1, r.actionCount());
        assertEquals(3, RegieJson.readActions(r.actionsJson()).getFirst().get("points"));
        assertEquals(List.of("w"), r.warnings());

        RegieService.Reply noKey = RegieService.parse("{\"transcript\":\"annule\",\"actions\":[],\"reply\":\"Pas de clé\",\"error\":\"no_api_key\"}");
        assertEquals("no_api_key", noKey.error());
        assertEquals(0, noKey.actionCount());

        assertEquals("bad_response", RegieService.parse("<html>").error());
        assertEquals("bad_response", RegieService.parse("").error());
    }

    @Test
    void unreachableServiceGivesAnError() {
        // Port 1 on localhost: nothing listens there
        assertEquals("unreachable", new RegieService("http://127.0.0.1:1").command(new byte[0], "{}").error());
    }

    // ------------------------------------------------------------------ payloads

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, value);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes());
        return back;
    }

    @Test
    void payloadCodecs() {
        assertEquals(new RegiePayloads.Status(true), roundTrip(RegiePayloads.Status.CODEC, new RegiePayloads.Status(true)));
        assertEquals(new RegiePayloads.ContextRequest(42), roundTrip(RegiePayloads.ContextRequest.CODEC, new RegiePayloads.ContextRequest(42)));
        // A context far bigger than writeUtf's 32767 characters
        char[] big = new char[100_000];
        Arrays.fill(big, 'é');
        String json = "{\"x\":\"" + new String(big) + "\"}";
        RegiePayloads.Context c = new RegiePayloads.Context(7, true, json);
        assertEquals(c, roundTrip(RegiePayloads.Context.CODEC, c));
        RegiePayloads.Actions a = new RegiePayloads.Actions(8, "trois points pour Juste", "[{\"type\":\"undo\",\"count\":1}]");
        assertEquals(a, roundTrip(RegiePayloads.Actions.CODEC, a));
        List<String> lines = new ArrayList<>(List.of("JustEdible +3 (pont) → 3 pts", "✗ manche « piscine » inconnue"));
        RegiePayloads.Result r = new RegiePayloads.Result(8, false, lines, "");
        assertEquals(r, roundTrip(RegiePayloads.Result.CODEC, r));
    }
}
