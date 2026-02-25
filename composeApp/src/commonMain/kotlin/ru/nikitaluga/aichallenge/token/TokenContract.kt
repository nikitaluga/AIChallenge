package ru.nikitaluga.aichallenge.token

import ru.nikitaluga.aichallenge.domain.model.TokenStats

object TokenContract {

    data class State(
        val messages: List<DisplayMessage> = emptyList(),
        /** Accumulated text of the currently-streaming assistant reply. */
        val streamingText: String = "",
        val isStreaming: Boolean = false,
        val inputText: String = "",
        val tokenStats: TokenStats = TokenStats(),
        /** Whether the stats panel is expanded. */
        val showStats: Boolean = true,
    )

    data class DisplayMessage(
        val role: String,       // "user" | "assistant"
        val content: String,
        /** Estimated token count for this individual message's content. */
        val tokenCount: Int = 0,
    )

    sealed interface Event {
        data class InputChanged(val text: String) : Event
        data object SendMessage : Event
        data object ClearHistory : Event
        data object ToggleStats : Event
        /** Fill the input with a pre-built scenario message for demo purposes. */
        data class LoadScenario(val scenario: Scenario) : Event
    }

    sealed interface Effect {
        data object ScrollToBottom : Effect
    }

    /**
     * Three demo scenarios described in the Day-8 task.
     * Loading a scenario fills the input with the next message in the sequence.
     */
    enum class Scenario(val label: String, val messages: List<String>) {
        SHORT(
            label = "А. Короткий",
            messages = listOf(
                "Привет, как дела?",
                "Хорошо, расскажи анекдот",
                "Спасибо, пока",
            ),
        ),
        LONG(
            label = "Б. Длинный",
            messages = listOf(
                "Расскажи о своей любимой книге подробно.",
                "Какой фильм ты бы порекомендовал посмотреть этим вечером и почему?",
                "Расскажи подробно о каком-нибудь интересном хобби.",
                "Опиши своё идеальное место для путешествия с деталями.",
                "Что думаешь о влиянии искусственного интеллекта на рынок труда?",
                "Расскажи о самом интересном научном открытии последних лет.",
                "Напиши небольшое эссе о важности чтения книг.",
                "Что такое квантовые вычисления? Объясни доступно.",
                "Как технологии изменят образование в ближайшие 10 лет?",
                "Напиши подробный рецепт своего любимого блюда.",
                "Опиши историю развития Интернета от истоков до наших дней.",
                "Расскажи о психологии принятия решений развёрнуто.",
                "Как медитация влияет на мозг? Приведи научные факты.",
                "Что такое стоицизм и как применять его в жизни?",
                "Подведи итог нашего разговора — о чём мы говорили?",
            ),
        ),
        OVERFLOW(
            label = "В. Переполнение",
            messages = listOf(
                "Напиши подробное эссе на 800 слов о истории Древнего Рима, включая все основные события, правителей и их достижения. Не пропускай ни одной детали.",
                "Теперь напиши такое же подробное эссе о Древней Греции — философия, искусство, политика, военные кампании. Минимум 800 слов.",
                "Расскажи подробно обо всех 12 цезарях Рима — биография, реформы, смерть каждого.",
                "Переведи всё сказанное выше на английский язык полностью.",
                "Теперь напиши резюме всего нашего разговора, включая все факты, имена и даты.",
            ),
        ),
    }
}
