package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class BindRecommenderDTO implements Serializable {
    /**
     * 推荐官用户ID，对应 app_user.id。
     */
    private Long recommenderUserId;
}
