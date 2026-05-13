package com.jinlin24th.jinlin.service;

/**
 * 短信验证码服务。
 * <p>
 * 负责验证码生成、Redis 缓存和验证码校验；真实短信发送通过 RocketMQ 异步完成。
 */
public interface SmsService {

    /**
     * 发送短信验证码，并将验证码写入 Redis。
     *
     * @param phone 手机号
     * @param scene 业务场景，例如 register、login、order
     * @return 是否发送成功
     */
    boolean sendCode(String phone, String scene);

    /**
     * 校验短信验证码。
     *
     * @param phone 手机号
     * @param scene 业务场景
     * @param code 用户输入的验证码
     * @return true-校验通过，false-校验失败
     */
    boolean verifyCode(String phone, String scene, String code);
}
