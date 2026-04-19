package ru.nikitaluga.aichallenge.common

import kotlinx.serialization.Serializable

@Serializable
internal data class ErrorResponse(val error: String)
