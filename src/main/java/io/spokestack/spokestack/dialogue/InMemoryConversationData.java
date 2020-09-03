package io.spokestack.spokestack.dialogue;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple data store for conversation data that resides in memory and lasts
 * only as long as the dialogue manager holding it.
 */
public class InMemoryConversationData implements ConversationData {

    private final Map<String, Object> data;

    /**
     * Create a new in-memory conversation data store.
     */
    public InMemoryConversationData() {
        this.data = new HashMap<>();
    }

    @Override
    public void set(String key, Object value) {
        this.data.put(key, value);
    }

    @Override
    public String getFormatted(String key, Format mode) {
        Object val = this.data.get(key);
        return (val == null) ? null : String.valueOf(val);
    }
}
