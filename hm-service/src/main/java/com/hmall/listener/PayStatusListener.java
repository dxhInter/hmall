package com.hmall.listener;

import com.hmall.domain.po.Order;
import com.hmall.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PayStatusListener {

    private final IOrderService orderService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "mark.order.pay.queue", durable = "true"),
            exchange = @Exchange(name = "pay.topic", type = ExchangeTypes.TOPIC),
            key = "pay.success"
    ))
    public void listenOrderPay(Long orderId){
//        // 查询订单状态
//        Order order = orderService.getById(orderId);
//        // 是否为未支付状态
//        if (order == null || order.getStatus() != 1) {
//            // 不是未支付状态，直接返回
//            return;
//        }
//        // 如果是未支付状态，修改订单状态
//        orderService.markOrderPaySuccess(orderId);

        // 基于乐观锁cas update order set status = 2 where id = #{orderId} and status = 1, 实现业务幂等
        orderService.lambdaUpdate()
                .set(Order::getStatus, 2)
                .set(Order::getPayTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, 1)
                .update();
    }
}
