package com.hoohooolom.app.tts;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.Executors;

/**
 * Synthesizes هوهو's non-verbal "hoo... hoo" owl call on the fly (two descending sine pulses
 * with a soft attack/decay envelope) so the app doesn't need a bundled audio asset.
 */
public final class HootSoundPlayer {
    private static final int SAMPLE_RATE = 22050;

    private HootSoundPlayer() {}

    public static void playHoot() {
        Executors.newSingleThreadExecutor().execute(() -> {
            short[] samples = buildHoot();
            AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(samples.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
            track.write(samples, 0, samples.length);
            track.play();
            track.setNotificationMarkerPosition(samples.length);
            track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override public void onMarkerReached(AudioTrack t) {
                    t.release();
                }
                @Override public void onPeriodicNotification(AudioTrack t) {}
            });
        });
    }

    private static short[] buildHoot() {
        short[] pulse1 = pulse(0.22, 500, 340, 0.9);
        short[] gap = new short[(int) (SAMPLE_RATE * 0.09)];
        short[] pulse2 = pulse(0.28, 430, 290, 1.0);
        short[] out = new short[pulse1.length + gap.length + pulse2.length];
        System.arraycopy(pulse1, 0, out, 0, pulse1.length);
        System.arraycopy(gap, 0, out, pulse1.length, gap.length);
        System.arraycopy(pulse2, 0, out, pulse1.length + gap.length, pulse2.length);
        return out;
    }

    /** One breathy hoot pulse: frequency glides from startHz to endHz with a raised-cosine envelope. */
    private static short[] pulse(double seconds, double startHz, double endHz, double peakAmp) {
        int n = (int) (SAMPLE_RATE * seconds);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double freq = startHz + (endHz - startHz) * t;
            phase += 2 * Math.PI * freq / SAMPLE_RATE;
            double envelope = Math.sin(Math.PI * t); // fades in and out smoothly
            double sample = Math.sin(phase) * envelope * peakAmp;
            out[i] = (short) (sample * Short.MAX_VALUE * 0.6);
        }
        return out;
    }
}
