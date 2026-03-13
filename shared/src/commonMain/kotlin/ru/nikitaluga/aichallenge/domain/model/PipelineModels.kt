package ru.nikitaluga.aichallenge.domain.model

/** One step in the tool-composition pipeline. */
data class PipelineToolStep(
    val toolName: String,
    val toolInput: String,   // raw JSON arguments string from LLM
    val toolResult: String,
)

/** A single message in the pipeline chat UI. */
data class PipelineChatMessage(
    val role: String,                               // "user" | "assistant"
    val content: String,
    val toolSteps: List<PipelineToolStep> = emptyList(),
)

/** Result returned by PipelineAgent.sendMessage(). */
data class PipelineAgentResult(
    val content: String,
    val toolSteps: List<PipelineToolStep> = emptyList(),
)

/** A file saved by the pipeline in the reports/ folder. */
data class SavedFileInfo(
    val filename: String,
    val sizeBytes: Long,
)
