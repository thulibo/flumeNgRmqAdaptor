package com.sogou.flume.rocketmq.adaptor.source;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.PollableSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.event.SimpleEvent;
import org.apache.flume.source.AbstractSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.rocketmq.client.consumer.MQPullConsumer;
import com.alibaba.rocketmq.client.consumer.PullResult;
import com.alibaba.rocketmq.client.consumer.PullStatus;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.common.consumer.ConsumeFromWhere;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.alibaba.rocketmq.common.message.MessageQueue;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * 自定义实现的Rocketmq Source，通过继承flume AbstractSource和实现PollableSource
 * <p>
 * 即：该RocketmqSource作为source，从Rocketmq消息队列中拉取、消费消息
 * 
 * @author libo211321
 *
 */
public class RocketmqSource extends AbstractSource implements Configurable, PollableSource {

    private static final Logger LOG = LoggerFactory.getLogger(RocketmqSource.class);

    private String topic;
    private String tags;
    private String topicHeaderName;
    private String tagsHeaderName;
    private int maxNums;
    private MQPullConsumer consumer;
    private Set<MessageQueue> mqs;
    private ConsumeFromWhere consumeFrom;

    @Override
    public void configure(Context context) {
        // 初始化配置项
        topic = Preconditions.checkNotNull(context.getString(RocketmqSourceConstants.TOPIC_CONFIG),
                "RocketMQ topic must be specified. For example: a1.sources.r1.topic=TestTopic");
        tags = context.getString(RocketmqSourceConstants.TAGS_CONFIG, RocketmqSourceConstants.TAGS_DEFAULT);
        topicHeaderName = context.getString(RocketmqSourceConstants.TOPIC_HEADER_NAME_CONFIG,
                RocketmqSourceConstants.TOPIC_HEADER_NAME_DEFAULT);
        tagsHeaderName = context.getString(RocketmqSourceConstants.TAGS_HEADER_NAME_CONFIG,
                RocketmqSourceConstants.TAGS_HEADER_NAME_DEFAULT);
        maxNums = context.getInteger(RocketmqSourceConstants.MAXNUMS_CONFIG, RocketmqSourceConstants.MAXNUMS_DEFAULT);

        // 初始化Consumer
        consumer = Preconditions.checkNotNull(RocketmqSourceConstants.getConsumer(context));
        consumeFrom = ConsumeFromWhere.valueOf(context.getString(RocketmqSourceConstants.CONSUME_FROM_WHERE_CONFIG,
                RocketmqSourceConstants.CONSUME_FROM_WHERE_DEFAULT));
    }

    @Override
    public Status process() throws EventDeliveryException {
        List<Event> eventList = Lists.newArrayList();
        Map<MessageQueue, Long> offsetMap = Maps.newHashMap();
        Event event;
        Map<String, String> headers;

        try {
            for (MessageQueue mq : mqs) {
                // 获取offset
                long offset = this.getMessageQueueOffset(mq);
                PullResult pullResult = consumer.pull(mq, tags, offset, maxNums);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("MessageQueue={}, Offset : {}, getPullStatus : {}",
                            new Object[] { mq, offset, pullResult.getPullStatus() });
                }
                // 发现新消息写入Event
                if (pullResult.getPullStatus() == PullStatus.FOUND) {
                    for (MessageExt messageExt : pullResult.getMsgFoundList()) {
                        event = new SimpleEvent();
                        headers = new HashMap<String, String>();
                        headers.put(topicHeaderName, messageExt.getTopic());
                        headers.put(tagsHeaderName, messageExt.getTags());
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("MessageQueue={}, Topic={}, Tags={}, Message: {}", new Object[] { mq,
                                    messageExt.getTopic(), messageExt.getTags(), messageExt.getBody() });
                        }
                        event.setBody(messageExt.getBody());
                        event.setHeaders(headers);
                        eventList.add(event);
                    }
                    offsetMap.put(mq, pullResult.getNextBeginOffset());
                }
            }
            // 批量处理事件
            getChannelProcessor().processEventBatch(eventList);
            for (Map.Entry<MessageQueue, Long> entry : offsetMap.entrySet()) {
                // 更新offset
                this.putMessageQueueOffset(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            LOG.error("RocketMQSource consume message exception", e);
            return Status.BACKOFF;
        }
        return Status.READY;
    }

    @Override
    public synchronized void start() {
        try {
            // 启动Consumer
            consumer.start();

            mqs = Preconditions.checkNotNull(consumer.fetchSubscribeMessageQueues(topic));
            consumer.registerMessageQueueListener(topic, null);

            long consumeOffset = 0L;
            for (MessageQueue mq : mqs) {
                switch (consumeFrom) {
                    case CONSUME_FROM_MAX_OFFSET:
                        consumeOffset = consumer.maxOffset(mq);
                        break;
                    case CONSUME_FROM_MIN_OFFSET:
                        consumeOffset = consumer.minOffset(mq);
                        break;
                    case CONSUME_FROM_LAST_OFFSET:
                    default:
                        consumeOffset = consumer.fetchConsumeOffset(mq, false);
                        long minOffset = consumer.minOffset(mq);
                        long maxOffset = consumer.maxOffset(mq);
                        if (-1 == consumeOffset) {
                            LOG.warn("current offset == -1. set consume offset as max offset : " + maxOffset + ", "
                                    + mq);
                            consumeOffset = maxOffset;
                        } else if (consumeOffset < minOffset) {
                            LOG.warn("current offset < min offset. set consume offset as min offset : " + minOffset
                                    + ", " + mq);
                            consumeOffset = minOffset;
                        }
                        break;
                }
                LOG.info("consume offset = " + consumeOffset + ", " + mq);
                putMessageQueueOffset(mq, consumeOffset);
            }
        } catch (MQClientException e) {
            LOG.error("RocketMQSource start consumer failed", e);
            Throwables.propagate(e);
        }
        super.start();
    }

    @Override
    public synchronized void stop() {
        // 停止Consumer
        consumer.shutdown();
        super.stop();
    }

    private void putMessageQueueOffset(MessageQueue mq, long offset) throws MQClientException {
        // 存储Offset，客户端每隔5s会定时刷新到Broker或者写入本地缓存文件
        consumer.updateConsumeOffset(mq, offset);
    }

    private long getMessageQueueOffset(MessageQueue mq) throws MQClientException {
        // 从Broker获取Offset
        long offset = consumer.fetchConsumeOffset(mq, false);

        if (offset < 0L) {
            offset = 0L;
        }

        return offset;
    }
}
