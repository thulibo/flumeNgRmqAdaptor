package com.sogou.flume.rocketmq.adaptor.sink;

import org.apache.flume.Context;

import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.client.producer.MQProducer;
import com.google.common.base.Preconditions;

/**
 * RockmqSink������
 * 
 * @author libo211321
 *
 */
public class RockmqSinkConstants {
    
    /**
     * Topic������磺a1.sinks.s1.topic=TestTopic
     */
    public static final String TOPIC_CONFIG = "topic";
    public static final String TOPIC_DEFAULT = "FLUME_ROCKETMQ";
    
    /**
     * Tags������磺a1.sinks.s1.tags=Tag1,Tag2
     */
    public static final String TAG_CONFIG = "tag";
    public static final String TAG_DEFAULT = "";
    
    /**
     * Producer����������磺a1.sinks.s1.producerGroup=please_rename_unique_group_name
     */
    public static final String PRODUCER_GROUP_CONFIG = "producerGroup";
    public static final String PRODUCER_GROUP_DEFAULT = "DEFAULT_PRODUCER";
    
    /**
     * Namesrv��ַ������磺a1.sinks.s1.namesrvAddr=localhost:9876
     */
    public static final String NAMESRV_ADDR_CONFIG = "namesrvAddr";

    /**
     * ��Ϣ��С���ֵ������磺a1.sinks.s1.maxMessageSize=131072,defaultΪ131072
     */
    public static final String MAX_MESSAGE_SIZE_CONFIG = "maxMessageSize";
    public static final String MAX_MESSAGE_SIZE = "131072";

    /**
     * ���������ļ�������ϢProducer
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
