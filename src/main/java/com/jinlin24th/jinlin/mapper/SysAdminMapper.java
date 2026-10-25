package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.SysAdmin;
import org.apache.ibatis.annotations.*;

/**
 * 系统管理员 Mapper。
 */
@Mapper
public interface SysAdminMapper extends BaseMapper<SysAdmin> {
    @Select("SELECT * FROM sys_admin WHERE id=#{id} AND deleted=0 FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    SysAdmin lockById(Long id);
}
