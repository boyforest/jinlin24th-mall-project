package com.jinlin24th.jinlin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlin24th.jinlin.pojo.entity.SysRole;
import org.apache.ibatis.annotations.*;

/**
 * 系统角色 Mapper。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
    @Select("SELECT * FROM sys_role WHERE role_code='SUPER_ADMIN' FOR UPDATE")
    @Options(useCache=false, flushCache=Options.FlushCachePolicy.TRUE)
    SysRole lockSuperRole();
}
