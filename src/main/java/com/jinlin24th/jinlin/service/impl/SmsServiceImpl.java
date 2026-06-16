package com.jinlin24th.jinlin.service.impl;

import com.jinlin24th.jinlin.common.exception.BizException;
import com.jinlin24th.jinlin.common.mq.producer.SmsMessageProducer;
import com.jinlin24th.jinlin.common.util.RedisUtil;
import com.jinlin24th.jinlin.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Random;

/**
 * 短信验证码服务实现。
 * <p>
 * 缓存策略：验证码 key 使用 ecommerce:sms:code:{scene}:{phone}，设置短 TTL；
 * 校验成功后立即删除 key，防止同一个验证码被重复使用。
 */
@Slf4j
@Service
public class SmsServiceImpl implements SmsService {

    private final RedisUtil redisUtil;
    private final ObjectProvider<SmsMessageProducer> smsMessageProducerProvider;
    private final Duration codeTtl;

    public SmsServiceImpl(
        RedisUtil redisUtil,
        ObjectProvider<SmsMessageProducer> smsMessageProducerProvider,
        @Value("${app.sms.code-ttl-seconds:300}") long codeTtlSeconds
    ) {
        this.redisUtil = redisUtil;
        this.smsMessageProducerProvider = smsMessageProducerProvider;
        this.codeTtl = Duration.ofSeconds(codeTtlSeconds);
    }

    /**
     * 生成验证码、写入 Redis，并异步发送短信。
     */
    @Override
    public boolean sendCode(String phone, String scene) {
        String normalizedPhone = requireText(phone, "手机号不能为空");
        String normalizedScene = normalizeScene(scene);
        String code = String.valueOf(new Random().nextInt(900000) + 100000);

        try {
            redisUtil.set(codeKey(normalizedPhone, normalizedScene), code, codeTtl);
            SmsMessageProducer producer = smsMessageProducerProvider.getIfAvailable();
            if (producer == null) {
                log.info("RocketMQ 未开启，验证码已写入 Redis: phone={}, scene={}",
                    maskPhone(normalizedPhone), normalizedScene);
                return true;
            }
            return producer.sendVerifyCode(normalizedPhone, code);
        } catch (Exception e) {
            log.error("发送短信验证码失败: phone={}, scene={}", maskPhone(normalizedPhone), normalizedScene, e);
            throw BizException.badRequest("短信验证码发送失败，请稍后重试");
        }
    }

    /**
     * 校验验证码，成功后删除缓存。
     */
    @Override
    public boolean verifyCode(String phone, String scene, String code) {
        String normalizedPhone = requireText(phone, "手机号不能为空");
        String normalizedScene = normalizeScene(scene);
        String normalizedCode = requireText(code, "验证码不能为空");
        String key = codeKey(normalizedPhone, normalizedScene);

        try {
            String cachedCode = redisUtil.get(key);
            boolean passed = normalizedCode.equals(cachedCode);
            if (passed) {
                redisUtil.delete(key);
            }
            return passed;
        } catch (Exception e) {
            log.error("校验短信验证码失败: phone={}, scene={}", maskPhone(normalizedPhone), normalizedScene, e);
            return false;
        }
    }

    /**
     * 构建验证码缓存 key。
     */
    private String codeKey(String phone, String scene) {
        return RedisUtil.KEY_PREFIX + "sms:code:" + scene + ":" + phone;
    }

    /**
     * 标准化业务场景，避免空 scene 产生脏 key。
     */
    private String normalizeScene(String scene) {
        return StringUtils.hasText(scene) ? scene.trim().toLowerCase() : "default";
    }

    /**
     * 基础参数校验。
     */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw BizException.badRequest(message);
        }
        return value.trim();
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "***";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
