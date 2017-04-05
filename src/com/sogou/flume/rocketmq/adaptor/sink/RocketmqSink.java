package com.sogou.flume.rocketmq.adaptor.sink;

import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.sink.AbstractSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.client.producer.MQProducer;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.common.message.Message;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/**
 * 自定义实现的Rocketmq Sink，通过继承flume AbstractSink实现
 * <p>
 * 即：该RocketmqSink作为sink，将数据send到Rocketmq消息队列中。
 * 
 * @author libo211321
 *
 */
public class RocketmqSink extends AbstractSink implements Configurable {

    private static final Logger LOG = LoggerFactory.getLogger(RocketmqSink.class);

    private String topic;
    private String tag;
    private MQProducer producer;

    @Override
    public void configure(Context context) {
        // 获取配置项
        topic = context.getString(RockmqSinkConstants.TOPIC_CONFIG, RockmqSinkConstants.TOPIC_DEFAULT);
        tag = context.getString(RockmqSinkConstants.TAG_CONFIG, RockmqSinkConstants.TAG_DEFAULT);
        // 初始化Producer
        producer = Preconditions.checkNotNull(RockmqSinkConstants.getProducer(context));
    }

    @Override
    public Status process() throws EventDeliveryException {
        Channel channel = getChannel();
        Transaction tx = channel.getTransaction();
        try {
            tx.begin();
            Event event = channel.take();
            if (event == null || event.getBody() == null || event.getBody().length == 0) {
                tx.commit();
                return Status.READY;
            }
            // 发送消息
            SendResult sendResult = producer.send(new Message(topic, tag, event.getBody()));

            if (LOG.isDebugEnabled()) {
                LOG.debug("ProducerMaxMessageSize={}, SendResult={}, Message={}",
                        ((DefaultMQProducer) producer).getMaxMessageSize(),
                        sendResult, event.getBody());
            }

            tx.commit();
            return Status.READY;
        } catch (Exception e) {
            LOG.error("RocketMQSink send message exception", e);
            try {
                tx.rollback();
                return Status.BACKOFF;
            } catch (Exception e2) {
                LOG.error("Rollback exception", e2);
            }
            return Status.BACKOFF;
        } finally {
            tx.close();
        }
    }

    @Override
    public synchronized void start() {
        try {
            // 启动Producer
            producer.start();
        } catch (MQClientException e) {
            LOG.error("RocketMQSink start producer failed", e);
            Throwables.propagate(e);
        }
        super.start();
    }

    @Override
    public synchronized void stop() {
        // 停止Producer
        producer.shutdown();
        super.stop();
    }
}
