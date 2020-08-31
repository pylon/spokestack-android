package io.spokestack.spokestack.profile;

import io.spokestack.spokestack.PipelineProfile;
import io.spokestack.spokestack.SpeechPipeline;

/**
 * A speech pipeline profile that relies on manual pipeline activation, using
 * Azure Speech Service for ASR.
 *
 * <p>
 * Azure Speech Service requires extra configuration, which must be added to the
 * pipeline build process separately from this profile:
 * </p>
 *
 * <ul>
 *   <li>
 *      <b>azure-api-key</b> (string): Azure API key
 *   </li>
 *   <li>
 *      <b>azure-region</b> (string): service region for Azure key
 *   </li>
 * </ul>
 *
 * @see io.spokestack.spokestack.microsoft.AzureSpeechRecognizer
 */
public class PushToTalkAzureASR implements PipelineProfile {
    @Override
    public SpeechPipeline.Builder apply(SpeechPipeline.Builder builder) {
        return builder
              .setInputClass(
                    "io.spokestack.spokestack.android.MicrophoneInput")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AutomaticGainControl")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.AcousticNoiseSuppressor")
              .addStageClass(
                    "io.spokestack.spokestack.webrtc.VoiceActivityDetector")
              .addStageClass("io.spokestack.spokestack.ActivationTimeout")
              .addStageClass(
                    "io.spokestack.spokestack.microsoft.AzureSpeechRecognizer");
    }
}
