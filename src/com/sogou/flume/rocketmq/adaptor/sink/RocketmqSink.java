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
 * �Զ���ʵ�ֵ�Rocketmq Sink��ͨ���̳�flume AbstractSinkʵ��
 * <p>
 * ������RocketmqSink��Ϊsink��������send��Rocketmq��Ϣ�����С�
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
        // ��ȡ������
        topic = context.getString(RockmqSinkConstants.TOPIC_CONFIG, RockmqSinkConstants.TOPIC_DEFAULT);
        tag = context.getString(RockmqSinkConstants.TAG_CONFIG, RockmqSinkConstants.TAG_DEFAULT);
        // ��ʼ��Producer
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
            // ������Ϣ
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
            // ����Producer
            producer.start();
        } catch (MQClientException e) {
            LOG.error("RocketMQSink start producer failed", e);
            Throwables.propagate(e);
        }
        super.start();
    }

    @Override
    public synchronized void stop() {
        // ֹͣProducer
        producer.shutdown();
        super.stop();
    }
}
