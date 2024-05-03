package com.yupi.springbootinit.constant;


public interface BIMqConstant {
    String BI_EXCHANGE_NAME="bi.chart.exchange";
    String BI_QUEUE_NAME="bi.chart.queue";
    String BI_ROUTING_KEY="bi.chart.routingKey";
    String DEAD_LETTER_EXCHANGE_KEY="x-dead-letter-exchange";
    String DEAD_LETTER_ROUTING_KEY="x-dead-letter-routing-key";
    String BI_DEAD_QUEUE="bi.chart.dead.queue";
    String BI_DEAD_EXCHANGE="bi.chart.dead.exchange";
    String BI_DEAD_ROUTING_KEY="bi.chart.dead.routingKey";
    String DEAD_LETTER_TTL="x-message-ttl";
    int BI_DEAD_TTL=120000;

    String ORDER_DEAD_QUEUE = "bi.order.dead.queue";
    String ORDER_DEAD_EXCHANGE = "bi.order.dead.exchange";
    String ORDER_DEAD_ROUTING_KEY = "bi.order.dead.routingKey";
    int ORDER_DEAD_TTL = 60 * 10 * 1000;
    String ORDER_QUEUE_NAME = "bi.order.queue";
    String ORDER_EXCHANGE_NAME = "bi.order.exchange";
    String ORDER_ROUTING_KEY = "bi.order.routingKey";
}
