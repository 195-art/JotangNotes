package org.example.jotangnote;

import org.example.jotangnote.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "rabbit.integration", matches = "true")
class RabbitBrokerTests {
    private String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }

    @Test
    void twoConsumersCannotOvertakeFailedUpdateAndFailoverKeepsOrder() throws Exception {
        var connection = new CachingConnectionFactory(env("RABBITMQ_HOST", "localhost"),
                Integer.parseInt(env("RABBITMQ_PORT", "5672")));
        connection.setUsername(env("RABBITMQ_USER", "guest"));
        connection.setPassword(env("RABBITMQ_PASSWORD", "guest"));
        connection.setVirtualHost(env("RABBITMQ_VHOST", "/"));
        var admin = new RabbitAdmin(connection);
        String queue = "jotangnote.test.ordered." + UUID.randomUUID();
        var config = new RabbitConfig();
        var props = new RabbitProperties();
        props.getListener().getSimple().setAutoStartup(false);
        var factory = config.orderedNoteListenerFactory(
                new SimpleRabbitListenerContainerFactoryConfigurer(props), connection);
        var permitUpdate = new AtomicBoolean(false);
        var attempts = new CopyOnWriteArrayList<String>();
        var completed = new CopyOnWriteArrayList<String>();
        var failedTwice = new CountDownLatch(2);
        var done = new CountDownLatch(2);
        var afterFailover = new CountDownLatch(1);
        MessageListener listener = message -> {
            String operation = new String(message.getBody(), StandardCharsets.UTF_8);
            attempts.add(operation);
            if (operation.equals("UPDATE") && !permitUpdate.get()) {
                failedTwice.countDown();
                throw new IllegalStateException("simulated UPDATE failure");
            }
            completed.add(operation);
            if (operation.equals("AFTER_FAILOVER")) afterFailover.countDown();
            else done.countDown();
        };
        var endpoint1 = new SimpleRabbitListenerEndpoint();
        endpoint1.setId("ordered-test-first");
        endpoint1.setQueueNames(queue);
        endpoint1.setMessageListener(listener);
        var endpoint2 = new SimpleRabbitListenerEndpoint();
        endpoint2.setId("ordered-test-second");
        endpoint2.setQueueNames(queue);
        endpoint2.setMessageListener(listener);
        var first = factory.createListenerContainer(endpoint1);
        var second = factory.createListenerContainer(endpoint2);
        try {
            var desired = config.noteQueue();
            admin.declareQueue(new Queue(queue, desired.isDurable(), false, false, desired.getArguments()));
            first.start();
            // 等第一个消费者注册后再启动备用，保证后续停止的是活跃者。
            awaitConsumers(admin, queue, 1);
            second.start();
            awaitConsumers(admin, queue, 2);
            var template = new RabbitTemplate(connection);
            template.invoke(operations -> {
                operations.convertAndSend("", queue, "UPDATE");
                operations.convertAndSend("", queue, "DELETE");
                return null;
            });
            assertTrue(failedTwice.await(10, TimeUnit.SECONDS), "UPDATE 应在原队列重试");
            assertTrue(completed.isEmpty(), "失败 UPDATE 后面的 DELETE 不能先完成");
            assertFalse(attempts.contains("DELETE"), "备用消费者不能越过活跃消费者取走 DELETE");
            permitUpdate.set(true);
            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertEquals(List.of("UPDATE", "DELETE"), completed);
            first.stop();
            awaitConsumers(admin, queue, 1);
            template.convertAndSend("", queue, "AFTER_FAILOVER");
            assertTrue(afterFailover.await(10, TimeUnit.SECONDS), "备用消费者应接管");
            assertEquals(List.of("UPDATE", "DELETE", "AFTER_FAILOVER"), completed);
        } finally {
            first.stop();
            second.stop();
            // 只删除本测试随机创建的队列，不操作业务队列。
            try { admin.deleteQueue(queue); } finally { connection.destroy(); }
        }
    }

    private void awaitConsumers(RabbitAdmin admin, String queue, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            var properties = admin.getQueueProperties(queue);
            if (properties != null && Integer.valueOf(expected).equals(properties.get("QUEUE_CONSUMER_COUNT"))) return;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        fail("消费者未按预期注册：" + expected);
    }
}
