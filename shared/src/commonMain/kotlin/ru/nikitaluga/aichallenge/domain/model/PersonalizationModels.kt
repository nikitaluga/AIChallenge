package ru.nikitaluga.aichallenge.domain.model

import kotlinx.serialization.Serializable
import ru.nikitaluga.aichallenge.api.Usage

/** Профиль пользователя со свободными полями key→value (как в День 11, но жёстко задан). */
@Serializable
data class UserProfileConfig(
    val id: String,
    val name: String,
    val fields: Map<String, String> = emptyMap(),
)

/** Результат одного хода персонализированного агента. */
data class PersonalizedResult(
    val content: String,
    val usage: Usage?,
)
