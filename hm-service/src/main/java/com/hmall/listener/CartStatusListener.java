package com.hmall.listener;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmall.common.utils.UserContext;
import com.hmall.domain.dto.CartDTO;
import com.hmall.service.ICartService;
import com.hmall.service.IOrderService;
import io.github.classgraph.json.JSONUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CartStatusListener {

    private final ICartService cartService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "cart.clear.queue", durable = "true"),
            exchange = @Exchange(name = "trade.topic", type = ExchangeTypes.TOPIC),
            key = "order.create"
    ))
    public void listenOrderPay(CartDTO cartDTO){
        Long userId = cartDTO.getUserId();
        Collection<Long> itemIds = cartDTO.getItemIds();
        cartService.removeByItemIds(itemIds,userId);
    }
}
