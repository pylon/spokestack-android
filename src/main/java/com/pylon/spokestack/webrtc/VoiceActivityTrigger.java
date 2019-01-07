package com.pylon.spokestack.webrtc;

import java.nio.ByteBuffer;

import com.pylon.spokestack.SpeechConfig;
import com.pylon.spokestack.SpeechProcessor;
import com.pylon.spokestack.SpeechContext;

/**
 * Voice Activity Detector (VAD) trigger pipeline component
 *
 * <p>
 * VoiceActivityTrigger is a speech pipeline component that implements Voice
 * Activity Detection (VAD) using the VoiceActivityDetector class. The
 * trigger activates the speech context whenever speech is detected.
 * </p>
 *
 */
public class VoiceActivityTrigger implements SpeechProcessor {
    private boolean isSpeech = false;

    /**
     * constructs a new trigger instance.
     * @param config the pipeline configuration instance
     */
    public VoiceActivityTrigger(SpeechConfig config) {
    }

    /**
     * pipeline cleanup.
     */
    public void close() {
    }

    /**
     * processes a frame of audio.
     * @param context the current speech context
     * @param frame   the audio frame to detect
     */
    public void process(SpeechContext context, ByteBuffer frame) {
        // activate the context on speech edge
        if (context.isSpeech() != this.isSpeech) {
            if (context.isSpeech()) {
                context.setActive(true);
                context.dispatch(SpeechContext.Event.ACTIVATE);
            } else {
                context.setActive(false);
                context.dispatch(SpeechContext.Event.DEACTIVATE);
            }
            this.isSpeech = context.isSpeech();
        }
    }
}
