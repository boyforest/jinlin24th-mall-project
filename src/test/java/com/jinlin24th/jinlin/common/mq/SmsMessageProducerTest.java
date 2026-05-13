package com.jinlin24th.jinlin.common.mq;

import com.jinlin24th.jinlin.common.config.RocketMQConfig;
import com.jinlin24th.jinlin.common.mq.producer.SmsMessageProducer;
import com.jinlin24th.jinlin.common.mq.support.RocketMQSendHelper;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信 MQ 生产者测试。
 */
class SmsMessageProducerTest {

    /**
     * 验证注册验证码会发送到短信 Topic 和验证码 Tag。
     */
    @Test
    void sendVerifyCodeShouldUseSmsTopicAndVerifyCodeTag() {
        RocketMQConfig config = new RocketMQConfig();
        RocketMQSendHelper helper = mock(RocketMQSendHelper.class);
        when(helper.syncSendWithRetry(any(), any(), any(), any())).thenReturn(true);

        SmsMessageProducer producer = new SmsMessageProducer(config, helper);
        producer.sendVerifyCode("13800138000", "123456");

        verify(helper).syncSendWithRetry(
            eq(config.getTopicSms()),
            eq(config.getTagSmsVerifyCode()),
            any(),
            any()
        );
    }
}
