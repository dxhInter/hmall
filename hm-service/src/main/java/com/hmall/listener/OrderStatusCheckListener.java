package com.hmall.listener;


import com.hmall.common.domain.MultiDelayMessage;
import com.hmall.common.mq.DelayMessageProcessor;
import com.hmall.constants.MqConstants;
import com.hmall.domain.po.Order;
import com.hmall.domain.po.PayOrder;
import com.hmall.service.IOrderService;
import com.hmall.service.IPayOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderStatusCheckListener {
    private final IOrderService orderService;
    private final RabbitTemplate rabbitTemplate;
    private final IPayOrderService payOrderService;
    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(name = MqConstants.DELAY_ORDER_QUEUE, durable = "true"),
                    exchange = @Exchange(value = MqConstants.DELAY_EXCHANGE,delayed = "true", type = ExchangeTypes.TOPIC),
                    key = MqConstants.DELAY_ORDER_ROUTING_KEY
            ))
    public void listenOrderDelayMessage(MultiDelayMessage<Long> msg){
        // 查询订单状态
        Long orderId = msg.getData();
        Order order = orderService.getById(orderId);
        // 订单是否已经支付
        if(order == null || order.getStatus() == 2){
            // 订单不存在或者已经处理，直接返回
            return;
        }
        // 去支付服务查询真正订单状态
        PayOrder payOrder = payOrderService.queryByBizOrderNo(orderId);
        if (payOrder != null && payOrder.getStatus() == 3) {
            // 支付成功，更新订单状态
            orderService.markOrderPaySuccess(orderId);
            return;
        }
         // 未支付，获取下次检查时间，重新发送延迟消息
        if (msg.hasNextDelay()){
            // 重新发送延迟消息
            Long delay = msg.removeNextDelay();
            rabbitTemplate.convertAndSend(MqConstants.DELAY_EXCHANGE, MqConstants.DELAY_ORDER_ROUTING_KEY, msg, new DelayMessageProcessor(delay.intValue()));
            return;
        }
        // 标记支付订单状态为2已取消, 已经释放库存并且标记订单状态为已取消
        if (payOrder != null) {
            payOrderService.cancelPayOrder(payOrder.getId());
        }
        orderService.cancelOrder(order.getId());
    }
}
