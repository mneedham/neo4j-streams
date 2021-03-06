package streams.service

import streams.serialization.JSONUtils
import streams.service.sink.strategy.IngestionStrategy
import streams.service.sink.strategy.SourceIdIngestionStrategy
import streams.service.sink.strategy.SchemaIngestionStrategy


const val STREAMS_TOPIC_KEY: String = "streams.sink.topic"
const val STREAMS_TOPIC_CDC_KEY: String = "streams.sink.topic.cdc"

enum class TopicTypeGroup { CYPHER, CDC }
enum class TopicType(val group: TopicTypeGroup, val key: String) {
    CDC_SOURCE_ID(group = TopicTypeGroup.CDC, key = "$STREAMS_TOPIC_CDC_KEY.sourceId"),
    CYPHER(group = TopicTypeGroup.CYPHER, key = "$STREAMS_TOPIC_KEY.cypher"),
    CDC_SCHEMA(group = TopicTypeGroup.CDC, key = "$STREAMS_TOPIC_CDC_KEY.schema")
}

abstract class StreamsSinkService(private val strategyMap: Map<TopicType, Any>) {

    abstract fun getTopicType(topic: String): TopicType?
    abstract fun getCypherTemplate(topic: String): String?
    abstract fun write(query: String, events: Collection<Any>)

    private fun writeWithStrategy(params: Collection<Any>, strategy: IngestionStrategy) {
        val data = params
                .map { JSONUtils.asStreamsTransactionEvent(it) }

        strategy.mergeNodeEvents(data).forEach { write(it.query, it.events) }
        strategy.deleteNodeEvents(data).forEach { write(it.query, it.events) }

        strategy.mergeRelationshipEvents(data).forEach { write(it.query, it.events) }
        strategy.deleteRelationshipEvents(data).forEach { write(it.query, it.events) }
    }

    private fun writeWithCypherTemplate(topic: String, params: Collection<Any>) {
        val query = getCypherTemplate(topic) ?: return
        write(query, params)
    }

    fun writeForTopic(topic: String, params: Collection<Any>) {
        val topicType = getTopicType(topic) ?: return
        when (topicType.group) {
            TopicTypeGroup.CYPHER -> writeWithCypherTemplate(topic, params)
            TopicTypeGroup.CDC -> writeWithStrategy(params, strategyMap.getValue(topicType) as IngestionStrategy)
        }
    }
}