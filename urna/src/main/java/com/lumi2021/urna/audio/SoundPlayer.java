package com.lumi2021.urna.audio;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

public final class SoundPlayer implements SoundEffects {

    private final Map<UrnaSound, Clip> clips = new EnumMap<>(UrnaSound.class);

    public static SoundEffects create() {
        if ("1".equals(System.getenv("URNA_MUDO"))) return NONE;
        return new SoundPlayer();
    }

    private SoundPlayer() {
        for (UrnaSound sound : UrnaSound.values()) {
            try (InputStream raw = SoundPlayer.class.getResourceAsStream(sound.resource())) {
                if (raw == null) {
                    System.err.println("[audio] arquivo ausente: " + sound.resource());
                    continue;
                }
                try (AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                    Clip clip = AudioSystem.getClip();
                    clip.open(in);
                    clips.put(sound, clip);
                }
            }
            catch (Exception | LinkageError e) {
                System.err.println("[audio] sem som (" + sound + "): " + e.getMessage());
                return;
            }
        }
    }

    @Override
    public void play(UrnaSound sound) {
        Clip clip = clips.get(sound);
        if (clip == null) return;
        synchronized (clip) {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }
    }
}
