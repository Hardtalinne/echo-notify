package com.echonotify.core.infrastructure.observability

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.Headers

object KafkaTracing {
    private val tracer = GlobalOpenTelemetry.getTracer("echo-notify.kafka")
    private val propagator = W3CTraceContextPropagator.getInstance()

    private val headersGetter = object : TextMapGetter<Headers> {
        override fun keys(carrier: Headers): Iterable<String> = carrier.map(Header::key)

        override fun get(carrier: Headers?, key: String): String? {
            if (carrier == null) return null
            return carrier.lastHeader(key)?.value()?.toString(Charsets.UTF_8)
        }
    }

    private val headersSetter = TextMapSetter<Headers> { carrier, key, value ->
        if (carrier == null) {
            return@TextMapSetter
        }
        carrier.remove(key)
        carrier.add(key, value.toByteArray(Charsets.UTF_8))
    }

    fun injectCurrentContext(headers: Headers) {
        propagator.inject(Context.current(), headers, headersSetter)
    }

    suspend fun <T> withConsumerSpan(
        headers: Headers,
        spanName: String,
        block: suspend () -> T
    ): T {
        val parent = propagator.extract(Context.root(), headers, headersGetter)
        val span = tracer.spanBuilder(spanName)
            .setParent(parent)
            .setSpanKind(SpanKind.CONSUMER)
            .startSpan()

        val scope = span.makeCurrent()
        return try {
            block()
        } catch (ex: Exception) {
            span.recordException(ex)
            span.setStatus(StatusCode.ERROR)
            throw ex
        } finally {
            scope.close()
            span.end()
        }
    }
}
