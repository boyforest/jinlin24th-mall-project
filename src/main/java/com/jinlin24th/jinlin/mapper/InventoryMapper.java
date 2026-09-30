package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 原子增加库存（用于订单取消/退款时归还占用）。
     * <p>
     * 必须在数据库端自增，不能"读出当前值 → 算出新值 → 写回"：
     * 后者在并发下会丢失更新；而此前那段 CAS + 同事务内重试的代码在
     * MySQL REPEATABLE-READ 下重试必然读到同一快照、注定失败，
     * 最终静默丢库存（订单取消但库存没还）。
     *
     * @param id       库存记录 ID
     * @param quantity 归还数量，必须为正
     * @return 影响行数；0 表示记录不存在，调用方应视为失败并回滚
     */
    @Update("UPDATE inventory SET stock = stock + #{quantity}, update_time = NOW() WHERE id = #{id}")
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);
}
