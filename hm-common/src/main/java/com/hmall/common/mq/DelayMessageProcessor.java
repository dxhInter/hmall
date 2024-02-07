package com.hmall.common.mq;

import lombok.AllArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;

@AllArgsConstructor
public class DelayMessageProcessor implements MessagePostProcessor {
    private final int delay;
    @Override
    public Message postProcessMessage(Message message) throws AmqpException {
        message.getMessageProperties().setDelay(delay);
        return message;
    }
}
