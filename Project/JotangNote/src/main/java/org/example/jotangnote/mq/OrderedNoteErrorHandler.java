package org.example.jotangnote.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.util.ErrorHandler;

/**
 * 笔记保序消费异常处理，反序列化等失败也保留原消息并重试
 */
public class OrderedNoteErrorHandler implements ErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(OrderedNoteErrorHandler.class);

    /**
     * 消费失败后短暂等待，再要求当前消息重新入队
     *
     * @param error 消费过程中的异常
     */
    @Override
    public void handleError(Throwable error) {
        log.error("笔记顺序队列处理失败，将重试当前消息；持续失败时需停止消费并排查", error);
        // 反序列化失败可能发生在重试拦截器之外，等待后再重入队以限制重试频率
        try {
            Thread.sleep(1000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        throw new ImmediateRequeueAmqpException("当前笔记消息尚未成功，禁止跳过", error);
    }
}
