package ru.nikitaluga.aichallenge.personalization

import ru.nikitaluga.aichallenge.domain.model.UserProfileConfig

object PersonalizationContract {

    data class State(
        val profiles: List<UserProfileConfig> = emptyList(),
        val activeProfileId: String? = null,
        val messages: List<DisplayMessage> = emptyList(),
        val inputText: String = "",
        val isLoading: Boolean = false,
        val showDialog: Boolean = false,
        /** null = создание нового, non-null = редактирование существующего. */
        val editingProfile: UserProfileConfig? = null,
        val lastUsagePrompt: Int = 0,
        val lastUsageCompletion: Int = 0,
        val showUsage: Boolean = false,
    ) {
        val activeProfile: UserProfileConfig?
            get() = profiles.firstOrNull { it.id == activeProfileId }
    }

    data class DisplayMessage(val role: String, val content: String)

    sealed interface Event {
        data class SelectProfile(val id: String) : Event
        data object CreateProfile : Event
        data class EditProfile(val id: String) : Event
        data class DeleteProfile(val id: String) : Event
        data class SaveProfile(val profile: UserProfileConfig) : Event
        data object DismissDialog : Event
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }
}
