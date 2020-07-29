package io.spokestack.spokestack.nlu.tensorflow;

import android.os.SystemClock;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.nlu.NLUResult;
import io.spokestack.spokestack.nlu.Slot;
import io.spokestack.spokestack.tensorflow.TensorflowModel;
import io.spokestack.spokestack.util.EventTracer;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SystemClock.class)
public class TensorflowNLUTest {

    @Before
    public void before() {
        mockStatic(SystemClock.class);
    }

    @Test
    public void initialization() throws Exception {
        SpeechConfig config = testConfig();

        // default config throws no errors
        new TestEnv(config);

        AtomicBoolean loadError = new AtomicBoolean(false);
        ControllableFactory factory = new ControllableFactory();
        String brokenParserClass = "io.spokestack.spokestack.nlu.tensorflow."
              + "TensorflowNLUTest$NoInitParser";

        // error loading parser
        TestEnv env = new TestEnv(config);
        env.nluBuilder
              .setThreadFactory(factory)
              .registerSlotParser("integer", brokenParserClass)
              .addTraceListener((level, message) -> {
                  if (level.equals(EventTracer.Level.ERROR)) {
                      loadError.set(true);
                  }
              });
        env.nluBuilder.build();
        factory.theOneThread.join();
        assertTrue(loadError.get());
    }

    @Test
    public void classify() throws Exception {
        TestEnv env = new TestEnv(testConfig());

        String utterance = "error";
        NLUResult result = env.classify(utterance).get();
        assertEquals(IllegalStateException.class, result.getError().getClass());
        assertEquals(utterance, result.getUtterance());
        assertEquals(0, result.getConfidence());
        assertNull(result.getIntent());
        assertTrue(result.getContext().isEmpty());
        assertTrue(result.getSlots().isEmpty());

        StringBuilder tooManyTokens = new StringBuilder();
        for (int i = 0; i <= env.nlu.getMaxTokens(); i++) {
           tooManyTokens.append("a ");
        }
        utterance = tooManyTokens.toString();
        result = env.classify(utterance).get();
        assertEquals(IllegalArgumentException.class,
              result.getError().getClass());
        assertEquals(utterance, result.getUtterance());
        assertEquals(0, result.getConfidence());
        assertNull(result.getIntent());
        assertTrue(result.getContext().isEmpty());
        assertTrue(result.getSlots().isEmpty());

        utterance = "this code is for test 1";
        float[] intentResult =
              buildIntentResult(2, env.metadata.getIntents().length);
        float[] tagResult =
              new float[utterance.split(" ").length
                    * env.metadata.getTags().length];
        setTag(tagResult, env.metadata.getTags().length, 0, 1);
        setTag(tagResult, env.metadata.getTags().length, 1, 2);
        setTag(tagResult, env.metadata.getTags().length, 5, 3);
        env.testModel.setOutputs(intentResult, tagResult);
        result = env.classify(utterance).get();

        Map<String, Slot> slots = new HashMap<>();
        slots.put("noun_phrase",
              new Slot("noun_phrase", "entity", "this code", "this code"));
        slots.put("test_num", new Slot("test_num", "integer", "1", 1));

        assertNull(result.getError());
        assertEquals("describe_test", result.getIntent());
        assertEquals(10.0, result.getConfidence());
        for (String slotName : slots.keySet()) {
            assertEquals(slots.get(slotName), result.getSlots().get(slotName));
        }
        assertEquals(slots, result.getSlots());
        assertEquals(utterance, result.getUtterance());
        assertTrue(result.getContext().isEmpty());

        // simulate two different spans being tagged as the same slot
        // in this example, "bad" doesn't get tagged as part of the noun phrase
        // (which is incorrect, but we're just testing the slot extraction
        // logic here)
        utterance = "this bad code is for test 1";
        intentResult = buildIntentResult(2, env.metadata.getIntents().length);
        tagResult = new float[utterance.split(" ").length
                    * env.metadata.getTags().length];
        setTag(tagResult, env.metadata.getTags().length, 0, 1);
        setTag(tagResult, env.metadata.getTags().length, 2, 1);
        setTag(tagResult, env.metadata.getTags().length, 6, 3);
        env.testModel.setOutputs(intentResult, tagResult);
        result = env.classify(utterance).get();

        slots = new HashMap<>();
        slots.put("noun_phrase",
              new Slot("noun_phrase", "entity", "this code", "this code"));
        slots.put("test_num", new Slot("test_num", "integer", "1", 1));

        assertNull(result.getError());
        assertEquals("describe_test", result.getIntent());
        assertEquals(10.0, result.getConfidence());
        for (String slotName : slots.keySet()) {
            assertEquals(slots.get(slotName), result.getSlots().get(slotName));
        }
        assertEquals(slots, result.getSlots());
        assertEquals(utterance, result.getUtterance());
        assertTrue(result.getContext().isEmpty());
    }

    private float[] buildIntentResult(int index, int numIntents) {
        float[] result = new float[numIntents];
        result[index] = 10;
        return result;
    }

    private void setTag(float[] tagResult, int numTags,
                        int tokenIndex, int tagIndex) {
        tagResult[tokenIndex * numTags + tagIndex] = 10;
    }

    public SpeechConfig testConfig() {
        return new SpeechConfig()
              .put("nlu-model-path", "model-path")
              .put("nlu-metadata-path", "src/test/resources/nlu.json")
              .put("wordpiece-vocab-path", "src/test/resources/vocab.txt");
    }

    public static class TestModel extends TensorflowModel {
        public TestModel(TensorflowModel.Loader loader) {
            super(loader);
        }

        public void run() {
            this.inputs(0).rewind();
            this.outputs(0).rewind();
            this.outputs(1).rewind();
        }

        public final void setOutputs(float[] intentsOut, float[] tagsOut) {
            this.outputs(0).rewind();
            for (float f : intentsOut) {
                this.outputs(0).putFloat(f);
            }
            this.outputs(1).rewind();
            for (float o : tagsOut) {
                this.outputs(1).putFloat(o);
            }
        }
    }

    public static class TestEnv implements TextEncoder {
        public final TensorflowModel.Loader loader;
        public final TensorflowNLUTest.TestModel testModel;
        public final TensorflowNLU.Builder nluBuilder;
        public final Metadata metadata;

        public TensorflowNLU nlu;

        public TestEnv(SpeechConfig config) throws Exception {
            // fetch configuration parameters
            String metadataPath = config.getString("nlu-metadata-path");
            this.metadata = loadMetadata(metadataPath);

            // create/mock tensorflow-lite models
            int maxTokens = 100;
            this.loader = spy(TensorflowModel.Loader.class);
            this.testModel = mock(TensorflowNLUTest.TestModel.class);

            doReturn(ByteBuffer
                  .allocateDirect(maxTokens * metadata.getIntents().length * 4)
                  .order(ByteOrder.nativeOrder()))
                  .when(this.testModel).inputs(0);
            doReturn(ByteBuffer
                  .allocateDirect(metadata.getIntents().length * 4)
                  .order(ByteOrder.nativeOrder()))
                  .when(this.testModel).outputs(0);
            doReturn(ByteBuffer
                  .allocateDirect(maxTokens * metadata.getTags().length * 4)
                  .order(ByteOrder.nativeOrder()))
                  .when(this.testModel).outputs(1);
            doReturn(4).when(this.testModel).getInputSize();
            doCallRealMethod().when(this.testModel).run();
            doReturn(this.testModel)
                  .when(this.loader).load();

            this.nluBuilder =
                  new TensorflowNLU.Builder()
                        .setConfig(config)
                        .setModelLoader(this.loader)
                        .setTextEncoder(this);
        }

        private Metadata loadMetadata(String metadataPath)
              throws FileNotFoundException {
            FileReader fileReader = new FileReader(metadataPath);
            Gson gson = new Gson();
            JsonReader reader = new JsonReader(fileReader);
            return gson.fromJson(reader, Metadata.class);
        }

        public Future<NLUResult> classify(String utterance) {
            if (this.nlu == null) {
                this.nlu = this.nluBuilder.build();
            }
            return this.nlu.classify(utterance);
        }

        @Override
        public int encodeSingle(String token) {
            return 1;
        }

        @Override
        public EncodedTokens encode(String text) {
            if (text.equals("error")) {
                throw new IllegalStateException("forced test error");
            }
            String[] split = text.split(" ");
            EncodedTokens encoded = new EncodedTokens(split);
            List<Integer> ids = new ArrayList<>();
            List<Integer> originalIndices = new ArrayList<>();
            for (int i = 0; i < split.length; i++) {
                ids.add(0);
                originalIndices.add(i);
            }
            encoded.addTokenIds(ids);
            encoded.setOriginalIndices(originalIndices);
            return encoded;
        }
    }

    static class ControllableFactory implements ThreadFactory {
        private Thread theOneThread;

        @Override
        public Thread newThread(@NotNull Runnable r) {
            theOneThread = new Thread(r);
            return theOneThread;
        }
    }

    static class NoInitParser implements SlotParser {

        NoInitParser() {
            throw new NullPointerException();
        }

        @Override
        public Object parse(Map<String, Object> metadata, String rawValue)
              throws Exception {
            return null;
        }
    }
}
