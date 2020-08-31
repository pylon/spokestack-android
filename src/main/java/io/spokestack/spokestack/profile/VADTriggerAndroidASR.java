package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

/**
 * A speech pipeline profile that uses voice activity detection to activate
 * Android's {@code SpeechRecognizer} API for ASR.
 *
 * <p>
 * Using Android's built-in ASR requires that an Android {@code Context} object
 * be attached to the speech pipeline using it. This must be done separately
 * from profile application, using
 * {@link SpeechPipeline.Builder#setAndroidContext(android.content.Context)}.
 * </p>
 *
 * @see io.spokestack.spokestack.android.AndroidSpeechRecognizer
 */
public class VADTriggerAndroidASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        return builder
              .setInputClass(
                    "io.spokestack.spokestack.android.PreASRMicrophoneInput")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AutomaticGainControl")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.VoiceActivityDetector")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.VoiceActivityTrigger")
              .addStageClass(
                    "io.spokestack.spokestack.android.AndroidSpeechRecognizer");
    }
}
