package com.jinlin24th.jinlin.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;

import com.jinlin24th.jinlin.common.coffee.CoffeeTime;
import com.jinlin24th.jinlin.common.config.CoffeePayConfig;
import com.jinlin24th.jinlin.mapper.*;
import com.jinlin24th.jinlin.pojo.entity.*;

import me.chanjar.weixin.common.error.WxErrorException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.format.DateTimeFormatter;

/** 唯一 READY 任务；先提交发送占位，再调用现有 SDK。UNKNOWN 不自动重发。 */
@Service
public class CoffeeNotificationService {
    private final CoffeeNotificationTaskMapper tasks;
    private final CoffeeOrderMapper orders;
    private final AppUserMapper users;
    private final WxMaService wx;
    private final CoffeePayConfig config;
    private final TransactionTemplate tx;

    public CoffeeNotificationService(
            CoffeeNotificationTaskMapper tasks,
            CoffeeOrderMapper orders,
            AppUserMapper users,
            WxMaService wx,
            CoffeePayConfig config,
            PlatformTransactionManager manager) {
        this.tasks = tasks;
        this.orders = orders;
        this.users = users;
        this.wx = wx;
        this.config = config;
        this.tx = new TransactionTemplate(manager);
    }

    public void send(Long id) {
        CoffeeNotificationTask task =
                tx.execute(
                        status -> {
                            CoffeeNotificationTask t = tasks.lockById(id);
                            if (t == null || !"PENDING".equals(t.getStatus())) return null;
                            t.setStatus("UNKNOWN");
                            t.setReason("SEND_OUTCOME_NOT_CONFIRMED");
                            t.setUpdatedAt(CoffeeTime.now());
                            tasks.updateById(t);
                            return t;
                        });
        if (task == null) return;
        CoffeeOrder o = orders.selectById(task.getOrderId());
        AppUser user = users.selectById(o.getUserId());
        if (config.getReadyTemplateId() == null || config.getReadyTemplateId().isBlank()) {
            finish(id, "SKIPPED", "TEMPLATE_NOT_CONFIGURED");
            return;
        }
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            finish(id, "SKIPPED", "RECIPIENT_UNAVAILABLE");
            return;
        }
        if (!"READY".equals(o.getFulfillmentStatus()) || !"NONE".equals(o.getRefundStatus())) {
            finish(id, "SKIPPED", "NO_LONGER_AWAITING_PICKUP");
            return;
        }
        WxMaSubscribeMessage message =
                new WxMaSubscribeMessage()
                        .setToUser(user.getOpenid())
                        .setTemplateId(config.getReadyTemplateId());
        message.addData(
                new WxMaSubscribeMessage.MsgData(config.getReadyPickupField(), o.getPickupCode()));
        message.addData(
                new WxMaSubscribeMessage.MsgData(
                        config.getReadyStoreField(), truncate(o.getStoreName(), 20)));
        message.addData(
                new WxMaSubscribeMessage.MsgData(
                        config.getReadyTimeField(),
                        o.getReadyAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
        try {
            wx.getMsgService().sendSubscribeMsg(message);
            finish(id, "SENT", "WECHAT_ACCEPTED");
        } catch (WxErrorException e) {
            int code = e.getError().getErrorCode();
            finish(id, (code == 43101 || code == 40037) ? "SKIPPED" : "FAILED", "WECHAT_" + code);
        } catch (Exception e) {
            finish(id, "UNKNOWN", "NETWORK_OUTCOME_UNKNOWN");
        }
    }

    private static String truncate(String s, int max) {
        return s.codePointCount(0, s.length()) <= max
                ? s
                : s.substring(0, s.offsetByCodePoints(0, max));
    }

    private void finish(Long id, String state, String reason) {
        tx.executeWithoutResult(
                status -> {
                    CoffeeNotificationTask t = tasks.lockById(id);
                    t.setStatus(state);
                    t.setReason(reason);
                    t.setUpdatedAt(CoffeeTime.now());
                    tasks.updateById(t);
                });
    }
}
