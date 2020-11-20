package io.spokestack.spokestack;

import android.content.Context;
import androidx.lifecycle.Lifecycle;
import io.spokestack.spokestack.dialogue.DialogueManager;
import io.spokestack.spokestack.nlu.NLUManager;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.tensorflow.parsers.DigitsParser;
import io.spokestack.spokestack.nlu.tensorflow.parsers.IdentityParser;
import io.spokestack.spokestack.nlu.tensorflow.parsers.IntegerParser;
import io.spokestack.spokestack.nlu.tensorflow.parsers.SelsetParser;
import io.spokestack.spokestack.tts.SynthesisRequest;
import io.spokestack.spokestack.tts.TTSManager;
import io.spokestack.spokestack.util.AsyncResult;
import io.spokestack.spokestack.util.EventTracer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static io.spokestack.spokestack.SpeechPipeline.DEFAULT_BUFFER_WIDTH;
import static io.spokestack.spokestack.SpeechPipeline.DEFAULT_FRAME_WIDTH;
import static io.spokestack.spokestack.SpeechPipeline.DEFAULT_SAMPLE_RATE;

/**
 * This class combines all Spokestack modules into a single component to provide
 * a unified interface to the library's ASR, NLU, and TTS features. Like the
 * individual modules, it is configurable using a fluent builder pattern, but it
 * provides a default configuration; only a few parameters are required from the
 * calling application, and those only for specific features noted in the
 * documentation for the builder's methods.
 *
 * <p>
 * Client applications may wish to establish event listeners for purposes such
 * as forwarding trace events to a logging framework, but events necessary to
 * complete user interactions (for example, sending ASR transcripts through NLU)
 * are handled entirely by this class. This includes internal management of TTS
 * playback, which requires the client application to declare additional media
 * player dependencies; see
 * <a href="https://www.spokestack.io/docs/Android/tts#prerequisites">the
 * documentation</a> for more details. This feature can be disabled via the
 * builder if desired.
 * </p>
 *
 * <p>
 * The default configuration of this class assumes that the client application
 * wants to use all of Spokestack's features, regardless of their implied
 * dependencies or required configuration, so an error will be thrown if any
 * prerequisite is missing at build time. Individual features can be disabled
 * via the builder.
 * </p>
 *
 * <p>
 * Convenience methods are provided to interact with the most important features
 * of individual modules, but they do not completely duplicate the modules'
 * public APIs. Each module in use can be retrieved via its own getter, enabling
 * use of its full API.
 * </p>
 *
 * <p>
 * Any convenience methods called on a module that has been explicitly disabled
 * will result in a {@code NullPointerException}.
 * </p>
 *
 * <p>
 * This class is not threadsafe; public methods used to interact with Spokestack
 * modules should be called from the same thread. The modules themselves use
 * background threads where appropriate to perform intensive tasks.
 * </p>
 *
 * @see SpeechPipeline
 * @see io.spokestack.spokestack.nlu.tensorflow.TensorflowNLU
 * @see TTSManager
 */
public final class Spokestack extends SpokestackAdapter
      implements AutoCloseable {

    private final List<SpokestackAdapter> listeners;
    private final boolean autoClassify;
    private final TranscriptEditor transcriptEditor;
    private SpeechPipeline speechPipeline;
    private NLUManager nlu;
    private TTSManager tts;
    private DialogueManager dialogueManager;

    /**
     * Construct a new Spokestack wrapper from an existing builder.
     *
     * @param builder The builder used to construct the Spokestack wrapper.
     * @throws Exception if there is an error during initialization.
     */
    private Spokestack(Builder builder) throws Exception {
        this.listeners = new ArrayList<>();
        this.listeners.addAll(builder.listeners);
        this.autoClassify = builder.autoClassify;
        this.transcriptEditor = builder.transcriptEditor;
        if (builder.useAsr) {
            this.speechPipeline = builder.getPipelineBuilder()
                  .addOnSpeechEventListener(this)
                  .build();
        }
        if (builder.useNLU) {
            this.nlu = builder.getNluBuilder()
                  .addTraceListener(this)
                  .build();
        }
        if (builder.useTTS) {
            if (!builder.useTTSPlayback) {
                builder.ttsBuilder.setOutputClass(null);
            }
            this.tts = builder.getTtsBuilder()
                  .addTTSListener(this)
                  .build();
        }
        if (builder.useDialogue) {
            this.dialogueManager = builder.dialogueBuilder.build();
        }
    }

    /**
     * Package-private constructor used for testing with an injected NLU.
     *
     * @param builder    The builder to use for everything but NLU.
     * @param nluManager The NLU manager to inject.
     */
    Spokestack(Builder builder, NLUManager nluManager) throws Exception {
        this.listeners = new ArrayList<>();
        this.listeners.addAll(builder.listeners);
        this.autoClassify = builder.autoClassify;
        this.transcriptEditor = builder.transcriptEditor;
        if (builder.useAsr) {
            this.speechPipeline = builder.getPipelineBuilder()
                  .addOnSpeechEventListener(this)
                  .build();
        }
        if (builder.useNLU) {
            this.nlu = nluManager;
        }
        if (builder.useTTS) {
            if (!builder.useTTSPlayback) {
                builder.ttsBuilder.setOutputClass(null);
            }
            this.tts = builder.getTtsBuilder()
                  .addTTSListener(this)
                  .build();
        }
    }

    // speech pipeline

    /**
     * @return The speech pipeline currently in use.
     */
    public SpeechPipeline getSpeechPipeline() {
        return speechPipeline;
    }

    /**
     * Starts the speech pipeline in order to process user input via the
     * microphone (or chosen input class).
     *
     * @throws Exception if there is an error configuring or starting the speech
     *                   pipeline.
     */
    public void start() throws Exception {
        if (this.speechPipeline != null) {
            this.speechPipeline.start();
        }
    }

    /**
     * Stops the speech pipeline and releases all its internal resources.
     */
    public void stop() {
        if (this.speechPipeline != null) {
            this.speechPipeline.stop();
        }
    }

    /**
     * Manually activate the speech pipeline, forcing the current ASR class to
     * begin recognizing speech. Useful when a wakeword is not employed, or in
     * conjunction with a microphone button.
     */
    public void activate() {
        if (this.speechPipeline != null) {
            this.speechPipeline.activate();
        }
    }

    /**
     * Manually deactivate the speech pipeline, forcing the current ASR class to
     * stop recognizing speech. Useful in conjunction with a hold-to-talk
     * button.
     *
     * <p>
     * <b>Note</b>: This method currently has no effect on the Android speech
     * recognizer this class uses by default.
     * </p>
     */
    public void deactivate() {
        if (this.speechPipeline != null) {
            this.speechPipeline.deactivate();
        }
    }

    // NLU

    /**
     * @return The NLU manager currently in use.
     */
    public NLUManager getNlu() {
        return nlu;
    }

    /**
     * Classify a user utterance, returning a wrapper that can either block
     * until the classification is complete or call a registered callback when
     * the result is ready.
     *
     * <p>
     * This convenience method automatically registers all {@code
     * SpokestackAdapter}s added to this class at build time to receive the
     * classification result asynchronously.
     * </p>
     *
     * @param utterance The utterance to classify.
     * @return An object representing the result of the asynchronous
     * classification.
     */
    public AsyncResult<NLUResult> classify(String utterance) {
        if (this.nlu != null) {
            return classifyInternal(utterance);
        }
        return null;
    }

    // TTS

    /**
     * @return The TTS manager currently in use.
     */
    public TTSManager getTts() {
        return tts;
    }

    /**
     * Dynamically constructs TTS component classes, allocating any resources
     * they control. It is only necessary to explicitly call this if the TTS
     * module's resources have been freed via {@link #releaseTts()} or {@link
     * #close()}.
     *
     * @throws Exception If there is an error constructing TTS components.
     */
    public void prepareTts() throws Exception {
        if (this.tts != null) {
            this.tts.prepare();
        }
    }

    /**
     * Stops activity in the TTS module and releases any resources held by its
     * components. No internally queued audio will be played after this method
     * is called, and the queue will be cleared.
     *
     * <p>
     * Once released, an explicit call to {@link #prepareTts()} is required to
     * reallocate TTS resources.
     * </p>
     */
    public void releaseTts() {
        if (this.tts != null) {
            this.tts.release();
        }
    }

    /**
     * Synthesizes a piece of text or SSML, dispatching the result to any
     * registered listeners.
     *
     * @param request The synthesis request data.
     */
    public void synthesize(SynthesisRequest request) {
        if (this.tts != null) {
            this.tts.synthesize(request);
        }
    }

    /**
     * Stops playback of any playing or queued synthesis results.
     */
    public void stopPlayback() {
        if (this.tts != null) {
            this.tts.stopPlayback();
        }
    }

    // listeners

    /**
     * Add a new listener to receive events from Spokestack modules.
     *
     * @param listener A listener that will receive events from all Spokestack
     *                 modules.
     */
    public void addListener(SpokestackAdapter listener) {
        this.listeners.add(listener);
        if (this.speechPipeline != null) {
            this.speechPipeline.addListener(listener);
        }
        if (this.nlu != null) {
            this.nlu.addListener(listener);
        }
        if (this.tts != null) {
            this.tts.addListener(listener);
        }
        if (this.dialogueManager != null) {
            this.dialogueManager.addListener(listener);
        }
    }

    /**
     * Remove a Spokestack event listener, allowing it to be garbage collected.
     *
     * @param listener The listener to be removed.
     */
    public void removeListener(SpokestackAdapter listener) {
        this.listeners.remove(listener);
        if (this.speechPipeline != null) {
            this.speechPipeline.removeListener(listener);
        }
        if (this.nlu != null) {
            this.nlu.removeListener(listener);
        }
        if (this.tts != null) {
            this.tts.removeListener(listener);
        }
        if (this.dialogueManager != null) {
            this.dialogueManager.removeListener(listener);
        }
    }

    @Override
    public void onEvent(@NotNull SpeechContext.Event event,
                        @NotNull SpeechContext context) {
        // automatically classify final ASR transcripts
        if (event == SpeechContext.Event.RECOGNIZE) {
            if (this.nlu != null && this.autoClassify) {
                String transcript = context.getTranscript();
                if (this.transcriptEditor != null) {
                    transcript =
                          this.transcriptEditor.editTranscript(transcript);
                }
                classifyInternal(transcript);
            }
        }
    }

    @Override
    public void nluResult(@NotNull NLUResult result) {
        super.nluResult(result);
    }

    private AsyncResult<NLUResult> classifyInternal(String text) {
        AsyncResult<NLUResult> result =
              this.nlu.classify(text);
        result.registerCallback(this);
        for (SpokestackAdapter listener : this.listeners) {
            result.registerCallback(listener);
        }
        if (this.dialogueManager != null) {
            result.registerCallback(this.dialogueManager);
        }
        return result;
    }

    @Override
    public void close() {
        if (this.speechPipeline != null) {
            this.speechPipeline.close();
        }
        if (this.tts != null) {
            this.tts.close();
        }
    }

    /**
     * Fluent builder interface for configuring Spokestack.
     *
     * @see Spokestack
     * @see Builder#Builder()
     */
    public static class Builder {
        private final SpeechPipeline.Builder pipelineBuilder;
        private final NLUManager.Builder nluBuilder;
        private final TTSManager.Builder ttsBuilder;
        private final DialogueManager.Builder dialogueBuilder;
        private final List<SpokestackAdapter> listeners = new ArrayList<>();

        private boolean useAsr = true;
        private boolean useNLU = true;
        private boolean autoClassify = true;
        private boolean useTTS = true;
        private boolean useTTSPlayback = true;
        private boolean useDialogue = true;

        private SpeechConfig speechConfig;
        private TranscriptEditor transcriptEditor;
        private Context appContext;
        private Lifecycle appLifecycle;

        /**
         * Create a Spokestack builder with a default configuration. The speech
         * pipeline will use the {@link io.spokestack.spokestack.profile.TFWakewordAndroidASR
         * TFWakewordAndroidASR} profile, and all features will be enabled.
         *
         * <p>
         * Internally, this builder delegates to the builder APIs of individual
         * modules. These individual builders can be retrieved and customized as
         * desired. Calls to {@link #setProperty(String, Object)} are propagated
         * to all modules.
         * </p>
         *
         * <p>
         * Some modules require additional configuration that cannot be set
         * automatically. Properties are set via {@link #setProperty(String,
         * Object)}; other configuration is listed by method:
         * </p>
         *
         * <ul>
         *     <li>
         *         <b>Wakeword detection</b> (properties)
         *     <ul>
         *   <li>
         *      <b>wake-filter-path</b> (string): file system path to the
         *      "filter" Tensorflow Lite model.
         *   </li>
         *   <li>
         *      <b>wake-encode-path</b> (string): file system path to the
         *      "encode" Tensorflow Lite model.
         *   </li>
         *   <li>
         *      <b>wake-detect-path</b> (string): file system path to the
         *      "detect" Tensorflow Lite model.
         *   </li>
         *     </ul>
         *     </li>
         *     <li>
         *         <b>NLU</b> (properties)
         *     <ul>
         *   <li>
         *      <b>nlu-model-path</b> (string): file system path to the NLU
         *      TensorFlow Lite model.
         *   </li>
         *   <li>
         *      <b>nlu-metadata-path</b> (string): file system path to the
         *      model's metadata, used to decode intent and slot names and
         *      types.
         *   </li>
         *   <li>
         *      <b>wordpiece-vocab-path</b> (string): file system path to the
         *      wordpiece vocabulary file used by the wordpiece token encoder.
         *   </li>
         *     </ul>
         *     </li>
         *     <li>
         *         <b>TTS</b> (properties)
         *     <ul>
         *   <li>
         *      <b>spokestack-id</b> (string): client ID used to authorize TTS
         *      requests; see <a href="https://spokestack.io/account">
         *          https://spokestack.io/account</a>.
         *   </li>
         *   <li>
         *      <b>spokestack-secret</b> (string): client secret used to
         *      authorize TTS requests; see
         *      <a href="https://spokestack.io/account">
         *          https://spokestack.io/account</a>.
         *   </li>
         *   </ul>
         *     </li>
         *     <li>
         *         <b>TTS</b> (other)
         *     <ul>
         *   <li>
         *       {@link #withAndroidContext(android.content.Context)}:
         *       Android Application context used to manage the audio session
         *       for automatic playback.
         *   </li>
         *   <li>
         *       {@link #withLifecycle(androidx.lifecycle.Lifecycle)}:
         *       Android lifecycle context used to manage automatic pausing and
         *       resuming of audio on application lifecycle events.
         *   </li>
         *   </ul>
         *     </li>
         *     <li>
         *         <b>Dialogue Management</b> (properties)
         *         <p>
         *             Dialogue management is an optional feature that will be
         *             disabled by default. To use it, one of the following
         *             properties is required. If both are included,
         *             {@code dialogue-policy-class} will take precedence.
         *         </p>
         *     <ul>
         *   <li>
         *      <b>dialogue-policy-file</b> (string): Path to a JSON file used to
         *      configure the rule-based dialogue policy.
         *   </li>
         *   <li>
         *      <b>dialogue-policy-class</b> (string): Class name of a custom dialogue
         *      policy.
         *   </li>
         *   </ul>
         *     </li>
         * </ul>
         */
        public Builder() {
            this.speechConfig = new SpeechConfig();
            setDefaults(this.speechConfig);
            String profileClass =
                  "io.spokestack.spokestack.profile.TFWakewordAndroidASR";
            this.pipelineBuilder =
                  new SpeechPipeline.Builder()
                        .setConfig(this.speechConfig)
                        .useProfile(profileClass);
            this.nluBuilder =
                  new NLUManager.Builder().setConfig(this.speechConfig);
            String ttsServiceClass =
                  "io.spokestack.spokestack.tts.SpokestackTTSService";
            String ttsOutputClass =
                  "io.spokestack.spokestack.tts.SpokestackTTSOutput";
            this.ttsBuilder =
                  new TTSManager.Builder()
                        .setTTSServiceClass(ttsServiceClass)
                        .setOutputClass(ttsOutputClass)
                        .setConfig(this.speechConfig);
            this.dialogueBuilder =
                  new DialogueManager.Builder(this.speechConfig);
        }

        private void setDefaults(SpeechConfig config) {
            // speech pipeline
            config.put("sample-rate", DEFAULT_SAMPLE_RATE);
            config.put("frame-width", DEFAULT_FRAME_WIDTH);
            config.put("buffer-width", DEFAULT_BUFFER_WIDTH);

            // nlu
            config.put("slot-digits", DigitsParser.class.getName());
            config.put("slot-integer", IntegerParser.class.getName());
            config.put("slot-selset", SelsetParser.class.getName());
            config.put("slot-entity", IdentityParser.class.getName());

            // other
            config.put("trace-level", EventTracer.Level.ERROR.value());
        }

        /**
         * Construct a wrapper builder with specific module builders. Used for
         * testing.
         *
         * @param pipeline the speech pipeline builder
         * @param tts      the TTS builder
         */
        Builder(SpeechPipeline.Builder pipeline, TTSManager.Builder tts) {
            this.speechConfig = new SpeechConfig();
            this.pipelineBuilder = pipeline;
            this.nluBuilder = new NLUManager.Builder();
            this.ttsBuilder = tts;
            this.dialogueBuilder =
                  new DialogueManager.Builder(this.speechConfig);
        }

        /**
         * @return The builder used to configure the speech pipeline.
         */
        public SpeechPipeline.Builder getPipelineBuilder() {
            return pipelineBuilder;
        }

        /**
         * @return The builder used to configure the NLU module.
         */
        public NLUManager.Builder getNluBuilder() {
            return nluBuilder;
        }

        /**
         * @return The builder used to configure the TTS module.
         */
        public TTSManager.Builder getTtsBuilder() {
            return ttsBuilder;
        }

        /**
         * @return The builder used to configure the dialogue management module.
         */
        public DialogueManager.Builder getDialogueBuilder() {
            return dialogueBuilder;
        }

        /**
         * Sets configuration for all module builders.
         *
         * <p>
         * Note that the following low-level properties are set to default
         * values at builder construction time; these properties must have
         * values in order for Spokestack to start properly:
         * </p>
         *
         * <ul>
         *     <li>sample-rate</li>
         *     <li>frame-width</li>
         *     <li>buffer-width</li>
         * </ul>
         *
         * <p>
         * Other module builders may set their own default values; builders for
         * the modules in use should be consulted before overwriting their
         * configuration.
         * </p>
         *
         * @param config configuration to attach
         * @return the updated builder
         */
        public Builder setConfig(SpeechConfig config) {
            this.speechConfig = config;
            this.pipelineBuilder.setConfig(config);
            this.nluBuilder.setConfig(config);
            this.ttsBuilder.setConfig(config);
            return this;
        }

        /**
         * Sets a configuration value.
         *
         * @param key   Configuration property name
         * @param value Property value
         * @return the updated builder
         */
        public Builder setProperty(String key, Object value) {
            this.speechConfig.put(key, value);
            return this;
        }

        /**
         * Sets a transcript editor used to alter ASR transcripts before they
         * are classified by the NLU module.
         *
         * <p>
         * This can be used to alter ASR results that frequently contain a
         * spelling for a homophone that's incorrect for the domain; for
         * example, an app used to summon a genie whose ASR transcripts tend to
         * contain "Jen" instead of "djinn".
         * </p>
         *
         * <p>
         * If a transcript editor is in use, registered listeners will receive
         * {@code RECOGNIZE} events from the speech pipeline with the unedited
         * transcripts, but the editor will automatically run on those
         * transcripts before the NLU module operates on them. Thus, the {@code
         * utterance} inside the {@code NLUResult} returned by classification
         * will reflect the edited version of the transcript.
         * </p>
         *
         * <p>
         * Transcript editors are <i>not</i> run automatically on inputs to the
         * {@link #classify(String)} convenience method.
         * </p>
         *
         * @param editor A transcript editor used to alter ASR results before
         *               NLU classification.
         * @return the updated builder
         */
        public Builder withTranscriptEditor(TranscriptEditor editor) {
            this.transcriptEditor = editor;
            return this;
        }

        /**
         * Sets the Android Context for the pipeline. Should be an Application
         * Context rather than an Activity Context.
         *
         * @param androidContext the Android Application Context.
         * @return the updated builder
         */
        public Builder withAndroidContext(Context androidContext) {
            this.appContext = androidContext;
            this.pipelineBuilder.setAndroidContext(androidContext);
            this.ttsBuilder.setAndroidContext(androidContext);
            return this;
        }

        /**
         * Sets the Android Lifecycle used for management of TTS playback.
         *
         * @param lifecycle the Android Lifecycle.
         * @return the updated builder
         */
        public Builder withLifecycle(Lifecycle lifecycle) {
            this.appLifecycle = lifecycle;
            this.ttsBuilder.setLifecycle(lifecycle);
            return this;
        }

        /**
         * Signal that Spokestack's speech pipeline should not be used to
         * recognize speech.
         *
         * @return the updated builder
         */
        public Builder withoutSpeechPipeline() {
            this.useAsr = false;
            return this;
        }

        /**
         * Signal that Spokestack's TensorFlow Lite wakeword detector should not
         * be used. This is equivalent to calling
         * <pre>
         * builder
         *     .getPipelineBuilder()
         *     .useProfile(
         *         "io.spokestack.spokestack.profile.PushToTalkAndroidASR");
         * </pre>
         * <p>
         * If a different profile is specified using the above approach, or if
         * the speech pipeline is disabled altogether with {@link
         * #withoutSpeechPipeline()}, this method should not be called.
         *
         * @return the updated builder
         */
        public Builder withoutWakeword() {
            String profileClass =
                  "io.spokestack.spokestack.profile.PushToTalkAndroidASR";
            this.pipelineBuilder.useProfile(profileClass);
            return this;
        }

        /**
         * Signal that Spokestack's NLU module should not be used.
         *
         * @return the updated builder
         */
        public Builder withoutNlu() {
            this.useNLU = false;
            return this;
        }

        /**
         * Signal that Spokestack's NLU module should not be automatically run
         * on ASR transcripts. NLU will still be initialized and available from
         * the {@code Spokestack} instance unless explicitly disabled via {@link
         * #withoutNlu()}.
         *
         * @return the updated builder
         */
        public Builder withoutAutoClassification() {
            this.autoClassify = false;
            return this;
        }

        /**
         * Signal that Spokestack's TTS module should not be used.
         *
         * @return the updated builder
         */
        public Builder withoutTts() {
            this.useTTS = false;
            return this;
        }

        /**
         * Signal that Spokestack should not automatically manage TTS playback.
         * To disable TTS altogether, call {@link #withoutTts()}; calling both
         * is unnecessary.
         *
         * @return the updated builder
         */
        public Builder withoutAutoPlayback() {
            this.useTTSPlayback = false;
            return this;
        }

        /**
         * Add a listener that receives events from all modules. This method is
         * provided as a convenience; if desired, specific listeners can still
         * be added by retrieving the relevant module builder and adding a
         * purpose-built listener to it.
         *
         * @param listener A listener that will receive events from all
         *                 Spokestack modules.
         * @return the updated builder
         */
        public Builder addListener(SpokestackAdapter listener) {
            this.pipelineBuilder.addOnSpeechEventListener(listener);
            this.nluBuilder.addTraceListener(listener);
            this.ttsBuilder.addTTSListener(listener);
            this.dialogueBuilder.addListener(listener);
            this.listeners.add(listener);
            return this;
        }

        /**
         * Use the current state of the builder to construct a full Spokestack
         * system.
         *
         * @return A Spokestack system configured with the current state of the
         * builder.
         * @throws Exception if required configuration is missing, or there is
         *                   an error during Spokestack initialization.
         */
        public Spokestack build() throws Exception {
            if (useTTS && useTTSPlayback) {
                if (this.appContext == null) {
                    throw new IllegalArgumentException("app context is "
                          + "required for playback management; see"
                          + "TTSManager.Builder.setAndroidContext()");
                }
                if (this.appLifecycle == null) {
                    throw new IllegalArgumentException("app lifecycle is "
                          + "required for playback management; see"
                          + "TTSManager.Builder.setLifecycle()");
                }
            }
            if (!this.speechConfig.containsKey("dialogue-policy-file")
                  && !this.speechConfig.containsKey("dialogue-policy-class")) {
                this.useDialogue = false;
            }
            return new Spokestack(this);
        }
    }
}
