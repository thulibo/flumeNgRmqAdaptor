package com.sogou.flume.rocketmq.adaptor.sink;

import org.apache.flume.Context;

import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.client.producer.MQProducer;
import com.google.common.base.Preconditions;

/**
 * RockmqSink常量类
 * 
 * @author libo211321
 *
 */
public class RockmqSinkConstants {
    
    /**
     * Topic配置项，如：a1.sinks.s1.topic=TestTopic
     */
    public static final String TOPIC_CONFIG = "topic";
    public static final String TOPIC_DEFAULT = "FLUME_ROCKETMQ";
    
    /**
     * Tags配置项，如：a1.sinks.s1.tags=Tag1,Tag2
     */
    public static final String TAG_CONFIG = "tag";
    public static final String TAG_DEFAULT = "";
    
    /**
     * Producer分组配置项，如：a1.sinks.s1.producerGroup=please_rename_unique_group_name
     */
    public static final String PRODUCER_GROUP_CONFIG = "producerGroup";
    public static final String PRODUCER_GROUP_DEFAULT = "DEFAULT_PRODUCER";
    
    /**
     * Namesrv地址配置项，如：a1.sinks.s1.namesrvAddr=localhost:9876
     */
    public static final String NAMESRV_ADDR_CONFIG = "namesrvAddr";

    /**
     * 消息大小最大值配置项，如：a1.sinks.s1.maxMessageSize=131072,default为131072
     */
    public static final String MAX_MESSAGE_SIZE_CONFIG = "maxMessageSize";
    public static final String MAX_MESSAGE_SIZE = "131072";

    /**
     * 根据配置文件构造消息Producer
     * 
     * @param context
     * @return
     */
    public static MQProducer getProducer(Context context) {
        final String producerGroup = context.getString(PRODUCER_GROUP_CONFIG, PRODUCER_GROUP_DEFAULT);
        final String maxMessageSize = context.getString(MAX_MESSAGE_SIZE_CONFIG, MAX_MESSAGE_SIZE);
        final String namesrvAddr = Preconditions.checkNotNull(context.getString(NAMESRV_ADDR_CONFIG), "RocketMQ namesrvAddr must be specified. For example: a1.sinks.s1.namesrvAddr=127.0.0.1:9876");

        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setMaxMessageSize(Integer.valueOf(maxMessageSize));
        producer.setNamesrvAddr(namesrvAddr);

        return producer;
    }

}
