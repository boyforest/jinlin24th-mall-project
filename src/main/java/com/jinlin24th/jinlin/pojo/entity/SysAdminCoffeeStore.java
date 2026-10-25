package com.jinlin24th.jinlin.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;

import lombok.Data;

import java.time.*;

/** 咖啡独立业务持久化数据。 */
@Data
@TableName("sys_admin_coffee_store")
public class SysAdminCoffeeStore {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adminId;
    private Long storeId;
}
