# 黑马商城
此项目实现了虎哥文档里的一些练习题
## 实现改造下单功能，基于RabbitMQ的异步通知
- 定义topic类型交换机，命名为trade.topic
- 定义消息队列，命名为cart.clear.queue
- 将cart.clear.queue与trade.topic绑定，BindingKey为order.create
- 下单成功时不再调用清理购物车接口，而是发送一条消息到trade.topic，发送消息的RoutingKey  为order.create，消息内容是下单的具体商品、当前登录用户信息
- 购物车服务监听cart.clear.queue队列，接收到消息后清理指定用户的购物车中的指定商品

## 实现支付订单超时取消功能
![订单超时流程](./1.jpeg)
- 在IOrderService接口中定义cancelOrder方法, 并且在OrderServiceImpl中实现该方法
- 实现查询支付服务功能
- 实现更改支付订单未超时未支付功能
