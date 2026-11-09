package fr.lordfinn.craftmaster.regie;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Microphone audio as sent to the voice control service: 16 kHz, mono, 16-bit signed little-endian PCM in a WAV. */
public final class Wav {

    private Wav() {}

    public static final int SAMPLE_RATE = 16000;
    public static final int BYTES_PER_SAMPLE = 2;

    /** Wraps raw PCM (16 kHz mono 16-bit LE) in a 44-byte RIFF/WAVE header. An odd trailing byte is dropped. */
    public static byte[] encode(byte[] pcm) {
        int len = pcm.length & ~1;
        ByteBuffer b = ByteBuffer.allocate(44 + len).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + len).put(new byte[]{'W', 'A', 'V', 'E'});
        b.put(new byte[]{'f', 'm', 't', ' '}).putInt(16)
                .putShort((short) 1)                               // PCM
                .putShort((short) 1)                               // mono
                .putInt(SAMPLE_RATE)
                .putInt(SAMPLE_RATE * BYTES_PER_SAMPLE)            // byte rate
                .putShort((short) BYTES_PER_SAMPLE)                // block align
                .putShort((short) 16);                             // bits per sample
        b.put(new byte[]{'d', 'a', 't', 'a'}).putInt(len).put(pcm, 0, len);
        return b.array();
    }

    /** Duration of raw PCM in seconds. */
    public static double seconds(int pcmBytes) {
        return pcmBytes / (double) (SAMPLE_RATE * BYTES_PER_SAMPLE);
    }

    /**
     * The PCM data of a WAV file (dev injection of a recorded order). Only 16 kHz mono 16-bit PCM is accepted;
     * other chunks (LIST...) are skipped.
     *
     * @throws IllegalArgumentException if it is not such a WAV
     */
    public static byte[] pcmOf(byte[] wav) {
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (wav.length < 12 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157) throw new IllegalArgumentException("not a RIFF/WAVE file");
        int pos = 12;
        boolean formatOk = false;
        while (pos + 8 <= wav.length) {
            int id = b.getInt(pos), size = b.getInt(pos + 4);
            if (size < 0 || pos + 8 + size > wav.length) size = wav.length - pos - 8;
            if (id == 0x20746d66) { // "fmt "
                formatOk = b.getShort(pos + 8) == 1 && b.getShort(pos + 10) == 1 && b.getInt(pos + 12) == SAMPLE_RATE
                        && b.getShort(pos + 22) == 16;
            } else if (id == 0x61746164) { // "data"
                if (!formatOk) throw new IllegalArgumentException("WAV must be 16 kHz mono 16-bit PCM");
                byte[] pcm = new byte[size];
                System.arraycopy(wav, pos + 8, pcm, 0, size);
                return pcm;
            }
            pos += 8 + size + (size & 1);
        }
        throw new IllegalArgumentException("no data chunk");
    }

    /** Loudness of a block of PCM, 0..1 (RMS), for the HUD level meter. */
    public static float level(byte[] pcm, int len) {
        long sum = 0;
        int n = len / 2;
        for (int i = 0; i + 1 < len; i += 2) {
            int s = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            sum += (long) s * s;
        }
        return n == 0 ? 0 : (float) Math.min(1.0, Math.sqrt(sum / (double) n) / 8000.0);
    }
}
