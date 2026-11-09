package fr.lordfinn.craftmaster.client.regie;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.regie.Wav;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records the microphone (16 kHz mono 16-bit, the format the service expects) on its own thread while the key is
 * held. A {@link #fromWav recorded WAV} can stand in for the microphone (dev testing, nobody speaking): it is
 * "played" in real time so the HUD behaves the same.
 */
abstract class Recorder {

    static final AudioFormat FORMAT = new AudioFormat(Wav.SAMPLE_RATE, 16, 1, true, false);
    /** Safety stop: nobody gives a 30-second order. */
    static final int MAX_SECONDS = 30;

    protected final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
    protected volatile float level;
    protected volatile boolean running = true;

    /** Loudness of the last block, 0..1 (HUD meter). */
    float level() { return level; }

    double seconds() {
        synchronized (pcm) { return Wav.seconds(pcm.size()); }
    }

    /** Stops and returns the audio recorded so far (raw PCM). */
    abstract byte[] stop();

    protected byte[] take() {
        synchronized (pcm) { return pcm.toByteArray(); }
    }

    protected void append(byte[] buf, int n) {
        synchronized (pcm) {
            if (pcm.size() + n <= MAX_SECONDS * Wav.SAMPLE_RATE * Wav.BYTES_PER_SAMPLE) pcm.write(buf, 0, n);
        }
        level = Wav.level(buf, n);
    }

    /** Opens the default microphone. @throws LineUnavailableException no microphone / already in exclusive use */
    static Recorder microphone() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) throw new LineUnavailableException("16 kHz mono capture not supported");
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT, Wav.SAMPLE_RATE / 5 * 2);   // 100 ms buffer
        line.start();
        return new Recorder() {
            private final Thread thread = Thread.ofPlatform().daemon().name("CraftMaster-mic").start(() -> {
                byte[] buf = new byte[Wav.SAMPLE_RATE / 20 * 2];   // 50 ms blocks
                while (running) {
                    int n = line.read(buf, 0, buf.length);
                    if (n > 0) append(buf, n);
                }
            });

            @Override
            byte[] stop() {
                running = false;
                line.stop();      // unblocks read()
                try { thread.join(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                // What is still in the line's buffer is the end of the sentence
                byte[] rest = new byte[line.available()];
                int n = line.read(rest, 0, rest.length);
                if (n > 0) append(rest, n);
                line.close();
                return take();
            }
        };
    }

    /** A WAV file instead of the microphone, fed in real time. */
    static Recorder fromWav(Path file) throws Exception {
        byte[] all = Wav.pcmOf(Files.readAllBytes(file));
        CraftMaster.LOG.info("Voice control: using {} instead of the microphone ({} s)", file, Wav.seconds(all.length));
        return new Recorder() {
            private final long start = System.nanoTime();
            private final Thread thread = Thread.ofPlatform().daemon().name("CraftMaster-wav").start(() -> {
                int block = Wav.SAMPLE_RATE / 20 * 2, pos = 0;
                while (running && pos < all.length) {
                    int n = Math.min(block, all.length - pos);
                    byte[] b = new byte[n];
                    System.arraycopy(all, pos, b, 0, n);
                    append(b, n);
                    pos += n;
                    long due = start + (long) (Wav.seconds(pos) * 1e9);
                    long wait = due - System.nanoTime();
                    if (wait > 0) try { Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000)); } catch (InterruptedException e) { return; }
                }
                level = 0;
            });

            @Override
            byte[] stop() {
                running = false;
                try { thread.join(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                // Released early or late: the whole recording is the order
                return all;
            }

            @Override
            double seconds() { return Math.min(Wav.seconds(all.length), (System.nanoTime() - start) / 1e9); }
        };
    }

    static double durationOf(Path wav) throws Exception {
        return Wav.seconds(Wav.pcmOf(Files.readAllBytes(wav)).length);
    }
}
