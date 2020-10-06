package io.spokestack.spokestack.google;

import java.util.*;
import java.nio.ByteBuffer;

import androidx.annotation.NonNull;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

import com.google.api.gax.rpc.ApiStreamObserver;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.stub.SpeechStub;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.cloud.speech.v1.StreamingRecognitionResult;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;

import io.spokestack.spokestack.OnSpeechEventListener;
import io.spokestack.spokestack.SpeechConfig;
import io.spokestack.spokestack.SpeechContext;

public class GoogleSpeechRecognizerTest implements OnSpeechEventListener {
    private SpeechContext.Event event;

    @Test
    @SuppressWarnings("unchecked")
    public void testRecognize() throws Exception {
        SpeechConfig config = createConfig();
        SpeechContext context = createContext(config);
        MockSpeechClient client = spy(MockSpeechClient.class);
        GoogleSpeechRecognizer recognizer =
            new GoogleSpeechRecognizer(config, client);

        // inactive
        recognizer.process(context, context.getBuffer().getLast());
        verify(client.getRequests(), never())
            .onNext(any(StreamingRecognizeRequest.class));

        // active/buffered frames
        context.setActive(true);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client.getRequests(), times(context.getBuffer().size() + 1))
            .onNext(any(StreamingRecognizeRequest.class));

        // subsequent frame
        reset(client.getRequests());
        recognizer.process(context, context.getBuffer().getLast());
        verify(client.getRequests())
            .onNext(any(StreamingRecognizeRequest.class));

        // complete
        context.setActive(false);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client.getRequests())
            .onCompleted();

        // responses
        client.getResponses().onNext(StreamingRecognizeResponse.newBuilder()
              .addResults(StreamingRecognitionResult.newBuilder()
                    .setIsFinal(false)
                    .addAlternatives(SpeechRecognitionAlternative.newBuilder()
                          .setTranscript("test")
                          .setConfidence((float) 0.95)
                          .build())
                    .build())
              .build()
        );
        assertEquals("test", context.getTranscript());
        assertEquals(0.95, context.getConfidence(), 1e-5);
        assertEquals(SpeechContext.Event.PARTIAL_RECOGNIZE, this.event);

        client.getResponses().onNext(StreamingRecognizeResponse.newBuilder()
              .addResults(StreamingRecognitionResult.newBuilder()
                    .setIsFinal(true)
                    .addAlternatives(SpeechRecognitionAlternative.newBuilder()
                          .setTranscript("final test")
                          .setConfidence((float) 0.75)
                          .build())
                    .build())
              .build()
        );
        client.getResponses().onCompleted();
        assertEquals("final test", context.getTranscript());
        assertEquals(0.75, context.getConfidence(), 1e-5);
        assertEquals(SpeechContext.Event.RECOGNIZE, this.event);

        // shutdown
        recognizer.close();
        verify(client.getStub()).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTimeout() throws Exception {
        SpeechConfig config = createConfig();
        SpeechContext context = createContext(config);
        MockSpeechClient client = spy(MockSpeechClient.class);
        GoogleSpeechRecognizer recognizer =
            new GoogleSpeechRecognizer(config, client);

        // active/buffered frames
        context.setActive(true);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client.getRequests(), times(context.getBuffer().size() + 1))
            .onNext(any(StreamingRecognizeRequest.class));

        // timeout
        context.setActive(false);
        recognizer.process(context, context.getBuffer().getLast());
        verify(client.getRequests())
            .onCompleted();

        // event
        client.getResponses().onCompleted();
        assertEquals("", context.getTranscript());
        assertEquals(0, context.getConfidence(), 1e-5);
        assertEquals(SpeechContext.Event.TIMEOUT, this.event);

        // shutdown
        recognizer.close();
        verify(client.getStub()).close();
    }

    @Test
    public void testError() throws Exception {
        SpeechConfig config = createConfig();
        SpeechContext context = createContext(config);
        MockSpeechClient client = spy(MockSpeechClient.class);
        GoogleSpeechRecognizer recognizer =
            new GoogleSpeechRecognizer(config, client);

        // trigger recognition
        context.setActive(true);
        recognizer.process(context, context.getBuffer().getLast());
        context.setActive(false);
        recognizer.process(context, context.getBuffer().getLast());

        // inject fault
        client.getResponses().onError(new Exception("test error"));
        assertEquals("test error", context.getError().getMessage());
        assertEquals("", context.getTranscript());
        assertEquals(0, context.getConfidence(), 1e-5);
        assertEquals(SpeechContext.Event.ERROR, this.event);
    }

    private SpeechConfig createConfig() {
        SpeechConfig config = new SpeechConfig();
        config.put("google-credentials", "{}");
        config.put("sample-rate", 16000);
        config.put("locale", "en-US");
        return config;
    }

    private SpeechContext createContext(SpeechConfig config) {
        SpeechContext context = new SpeechContext(config);
        context.addOnSpeechEventListener(this);

        context.attachBuffer(new LinkedList<ByteBuffer>());
        for (int i = 0; i < 3; i++)
            context.getBuffer().addLast(ByteBuffer.allocateDirect(320));

        return context;
    }

    public void onEvent(@NonNull SpeechContext.Event event,
                        @NonNull SpeechContext context) {
        this.event = event;
    }

    @SuppressWarnings("unchecked")
    private static class MockSpeechClient extends SpeechClient {
        private final BidiStreamingCallable callable;
        private final ApiStreamObserver requests;

        public MockSpeechClient() {
            super(mock(SpeechStub.class));
            this.callable = mock(BidiStreamingCallable.class);
            this.requests = mock(ApiStreamObserver.class);
            when(getStub().streamingRecognizeCallable())
                .thenReturn(this.callable);
            when(this.callable.bidiStreamingCall(any(ApiStreamObserver.class)))
                .thenReturn(this.requests);
        }

        public ApiStreamObserver getRequests() {
            return this.requests;
        }

        public ApiStreamObserver getResponses() {
            ArgumentCaptor<ApiStreamObserver> captor =
                ArgumentCaptor.forClass(ApiStreamObserver.class);
            verify(this.callable)
                .bidiStreamingCall(captor.capture());
            return captor.getValue();
        }
    }
}
