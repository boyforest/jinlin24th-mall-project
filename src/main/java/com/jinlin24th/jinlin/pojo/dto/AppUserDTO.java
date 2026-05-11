package com.jinlin24th.jinlin.pojo.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class AppUserDTO implements Serializable {
    private String nickname;
    private String avatar;
    private Integer gender;
    private String phone;
}
