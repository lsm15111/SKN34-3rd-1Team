package ai.govbiz.core.assistant.repository.exception

class AssistantConversationStoreException(cause: Throwable? = null) :
    RuntimeException("The assistant conversation store is unavailable.", cause)
