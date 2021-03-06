package io.spokestack.spokestack.dialogue;

import io.spokestack.spokestack.nlu.NLUResult;

/**
 * <p>
 * The API for dialogue policies used by Spokestack's {@link
 * io.spokestack.spokestack.dialogue.DialogueManager DialogueManager}
 * component.
 * </p>
 *
 * <p>
 * A dialogue policy must have a constructor that accepts a {@link
 * io.spokestack.spokestack.SpeechConfig} instance to be used by the dialogue
 * management system.
 * </p>
 */
public interface DialoguePolicy {

    /**
     * Store the internal state of the dialogue policy in the specified data
     * store for cross-session persistence.
     *
     * @param conversationData The data store where policy state should be
     *                         saved.
     * @return The policy state that was dumped.
     */
    String dump(ConversationData conversationData);

    /**
     * Load previously serialized internal state.
     *
     * @param state            Policy state serialized using {@link
     *                         #dump(ConversationData) dump()}.
     * @param conversationData The data store where any relevant policy state
     *                         should be loaded.
     * @throws Exception if there was an error loading state from the supplied
     *                   arguments.
     */
    void load(String state, ConversationData conversationData) throws Exception;

    /**
     * Process a user turn and return a relevant response.
     *
     * @param userTurn         The user input as determined by the NLU
     *                         component.
     * @param conversationData Conversation data used to resolve and prepare a
     *                         response.
     * @param eventDispatcher  Dispatcher used to notify listeners of dialogue
     *                         events.
     */
    void handleTurn(NLUResult userTurn,
                    ConversationData conversationData,
                    DialogueDispatcher eventDispatcher);

    /**
     * Complete the pending user turn.
     * <p>
     * This method should be called after any actions or data retrieval pending
     * in the app are completed.
     * </p>
     *
     * @param success          {@code true} if the user's request/desired action
     *                         was fulfilled successfully; {@code false}
     *                         otherwise.
     * @param conversationData Conversation data used to resolve and prepare a
     *                         response.
     * @param eventDispatcher  Dispatcher used to notify listeners of dialogue
     */
    void completeTurn(boolean success, ConversationData conversationData,
                      DialogueDispatcher eventDispatcher);
}
