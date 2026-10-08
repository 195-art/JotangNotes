package org.example.jotangnote.config;

import org.example.jotangnote.mq.OrderedNoteErrorHandler;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * 笔记消息队列配置，包括保序消费、失败重试和消息转换
 */
@Configuration
public class RabbitConfig {
    // 笔记保序队列、交换机和路由key
    public static final String NOTE_QUEUE = "note.ordered.queue";
    public static final String NOTE_EXCHANGE = "note.exchange";
    public static final String NOTE_ROUTING_KEY = "note.ordered.operation";

    // 保留旧版失败队列供升级排查；新保序队列不自动跳过失败操作。
    public static final String NOTE_DLX = "note.dlx";
    public static final String NOTE_DLQ = "note.queue.dlq";
    public static final String NOTE_DEAD_ROUTING_KEY = "note.dead";

    /**
     * 创建持久化笔记队列，启用单活跃消费者
     *
     * @return 笔记保序队列
     */
    @Bean
    public Queue noteQueue() {
        // SAC 是不可变声明参数，使用新队列。旧队列需升级前排空，不能删除积压。
        return new Queue(NOTE_QUEUE, true, false, false,
                Map.of("x-single-active-consumer", true, "x-queue-type", "classic"));
    }

    /**
     * 配置本地重试耗尽后的处理方式，将当前消息重新入队
     *
     * @return 保留失败消息的重试恢复器
     */
    @Bean
    public MessageRecoverer noteMessageRecoverer() {
        return (message, cause) -> {
            // 失败 UPDATE 不能被移走后继续 DELETE，否则恢复时无法按原顺序执行。
            throw new ImmediateRequeueAmqpException("笔记操作失败，保留原消息并重试", cause);
        };
    }

    /**
     * 配置笔记保序监听器，每次仅处理一条消息
     *
     * @param configurer Spring Boot监听器配置器
     * @param connectionFactory RabbitMQ连接工厂
     * @return 笔记消息监听器工厂
     */
    @Bean
    public SimpleRabbitListenerContainerFactory orderedNoteListenerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        // 固定单消费者和单条预取，避免同一队列中的操作并行处理
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        factory.setBatchSize(1);
        factory.setConsumerBatchEnabled(false);
        factory.setBatchListener(false);
        // 消费成功后由容器确认消息，失败时保留消息并重新入队
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(true);
        factory.setErrorHandler(new OrderedNoteErrorHandler());
        return factory;
    }

    /**
     * 创建笔记直连交换机
     *
     * @return 笔记交换机
     */
    @Bean
    public DirectExchange noteExchange() {
        return new DirectExchange(NOTE_EXCHANGE);
    }

    /**
     * 绑定笔记队列与交换机
     *
     * @param noteQueue 笔记队列
     * @param noteExchange 笔记交换机
     * @return 使用笔记路由key的绑定关系
     */
    @Bean
    public Binding binding(Queue noteQueue, DirectExchange noteExchange) {
        return BindingBuilder.bind(noteQueue).to(noteExchange).with(NOTE_ROUTING_KEY);
    }

    /**
     * 创建旧版死信交换机，保留用于升级排查
     *
     * @return 旧版死信交换机
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(NOTE_DLX);
    }

    /**
     * 创建旧版死信队列，保留已有失败消息
     *
     * @return 旧版死信队列
     */
    @Bean
    public Queue deadLetterQueue() {
        return new Queue(NOTE_DLQ, true);
    }

    /**
     * 绑定旧版死信队列与交换机
     *
     * @return 旧版死信绑定关系，新保序队列不自动转入该队列
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(NOTE_DEAD_ROUTING_KEY);
    }

    /**
     * 配置消息转换器，限定允许反序列化的业务类范围
     *
     * @return 消息转换器
     */
    @Bean
    public SimpleMessageConverter messageConverter() {
        SimpleMessageConverter converter = new SimpleMessageConverter();
        converter.setAllowedListPatterns(List.of("org.example.jotangnote.*"));
        return converter;
    }
}
