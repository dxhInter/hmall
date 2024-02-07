package com.hmall.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.common.domain.MultiDelayMessage;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.mq.DelayMessageProcessor;
import com.hmall.common.utils.UserContext;
import com.hmall.constants.MqConstants;
import com.hmall.domain.dto.CartDTO;
import com.hmall.domain.dto.ItemDTO;
import com.hmall.domain.dto.OrderDetailDTO;
import com.hmall.domain.dto.OrderFormDTO;
import com.hmall.domain.po.Order;
import com.hmall.domain.po.OrderDetail;
import com.hmall.mapper.OrderMapper;
import com.hmall.service.ICartService;
import com.hmall.service.IItemService;
import com.hmall.service.IOrderDetailService;
import com.hmall.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final IItemService itemService;
    private final IOrderDetailService detailService;
    private final ICartService cartService;
    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // 1.订单数据
        Order order = new Order();
        // 1.1.查询商品
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 1.2.获取商品id和数量的Map
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品
        List<ItemDTO> items = itemService.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        order.setTotalFee(total);
        // 1.5.其它属性
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(UserContext.getUser());
        order.setStatus(1);
        // 1.6.将Order写入数据库order表中
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);

        // 3.清理购物车商品
//        cartService.removeByItemIds(itemIds);
        CartDTO cartDTO = new CartDTO();
        cartDTO.setItemIds(itemIds);
        cartDTO.setUserId(UserContext.getUser());
        try {
            rabbitTemplate.convertAndSend("trade.topic", "order.create", cartDTO);
        } catch (AmqpException e){
            log.error("清理购物车商品失败", e);
        }
        // 4.扣减库存
        try {
            itemService.deductStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("库存不足！");
        }
        // 延迟检查订单是否支付
        try {
            MultiDelayMessage<Long> msg = MultiDelayMessage.of(order.getId(), 10000L, 10000L, 10000L, 15000L, 15000L, 30000L);
            rabbitTemplate.convertAndSend(MqConstants.DELAY_EXCHANGE, MqConstants.DELAY_ORDER_ROUTING_KEY, msg, new DelayMessageProcessor(msg.removeNextDelay().intValue()));
        } catch (AmqpException e){
            log.error("延迟检查订单是否支付失败", e);
        }
        return order.getId();
    }

    @Override
    public void markOrderPaySuccess(Long orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(2);
        order.setPayTime(LocalDateTime.now());
        updateById(order);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        lambdaUpdate()
                .set(Order::getStatus, 5)
                .set(Order::getCloseTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .update();
         // 退还库存
        List<OrderDetail> orderDetails = detailService.query().eq("order_id", orderId).list();
        List<OrderDetailDTO> detailDTOS = new ArrayList<>(orderDetails.size());
        for (OrderDetail detail : orderDetails) {
            OrderDetailDTO detailDTO = new OrderDetailDTO();
            detailDTO.setItemId(detail.getItemId());
            detailDTO.setNum(detail.getNum());
            detailDTOS.add(detailDTO);
        }
        itemService.increaseStock(detailDTOS);
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }
}
