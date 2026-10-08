package org.example.jotangnote;

import org.example.jotangnote.config.RabbitConfig;
import org.example.jotangnote.mq.OrderedNoteErrorHandler;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NoteOrderingTests {
    @Test
    void orderingFactoryOverridesUnsafeConcurrencyPrefetchAndAckSettings() {
        var props = new RabbitProperties();
        props.getListener().getSimple().setConcurrency(4);
        props.getListener().getSimple().setMaxConcurrency(8);
        props.getListener().getSimple().setPrefetch(50);
        props.getListener().getSimple().setAcknowledgeMode(AcknowledgeMode.NONE);
        var config = new RabbitConfig();
        var factory = config.orderedNoteListenerFactory(
                new SimpleRabbitListenerContainerFactoryConfigurer(props), mock(ConnectionFactory.class));
        var endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setId("ordering-settings-test");
        endpoint.setQueueNames(RabbitConfig.NOTE_QUEUE);
        endpoint.setMessageListener(message -> {});
        var container = factory.createListenerContainer(endpoint);
        assertEquals(1, ReflectionTestUtils.getField(container, "concurrentConsumers"));
        assertEquals(1, ReflectionTestUtils.getField(container, "maxConcurrentConsumers"));
        assertEquals(1, ReflectionTestUtils.getField(container, "prefetchCount"));
        assertEquals(1, ReflectionTestUtils.getField(container, "batchSize"));
        assertEquals(AcknowledgeMode.AUTO, container.getAcknowledgeMode());
        assertInstanceOf(OrderedNoteErrorHandler.class, container.getErrorHandler());
        assertEquals(true, config.noteQueue().getArguments().get("x-single-active-consumer"));
        assertEquals("classic", config.noteQueue().getArguments().get("x-queue-type"));
        assertFalse(config.noteQueue().isIgnoreDeclarationExceptions());
    }

    @Test
    void exhaustedRetriesKeepMessageInsteadOfSkippingToNextOperation() {
        var cause = new IllegalStateException("database unavailable");
        var thrown = assertThrows(ImmediateRequeueAmqpException.class,
                () -> new RabbitConfig().noteMessageRecoverer().recover(new Message(new byte[0]), cause));
        assertSame(cause, thrown.getCause());
    }

    @Test
    void malformedMessageAlsoRequeuesRatherThanBeingDiscarded() {
        var cause = new org.springframework.amqp.support.converter.MessageConversionException("invalid payload");
        var thrown = assertThrows(ImmediateRequeueAmqpException.class,
                () -> new OrderedNoteErrorHandler().handleError(cause));
        assertSame(cause, thrown.getCause());
    }
}
