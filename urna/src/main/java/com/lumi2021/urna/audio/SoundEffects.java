package com.lumi2021.urna.audio;

public interface SoundEffects {

    void play(UrnaSound sound);
    SoundEffects NONE = sound -> { };
}
