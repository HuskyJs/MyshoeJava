package com.itheima.reggie.dto;

import com.itheima.reggie.entity.WxUserInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;


    //姓名
    private String name;


    //手机号
    private String phone;


    //性别 0 女 1 男
    private String sex;


    //身份证号
    private String idNumber;


    //头像
    private String avatar;


    //状态 0:禁用，1:正常
    private Integer status;

    //微信id
    private String wxUnionId;

    //openid
    private String openId;

    //dto拓展属性
    private String token;
    List<String> permissions;
    List<String> roles;
    //验证码
    private String code;

    public void from(WxUserInfo wxUserInfo) {
        this.name = wxUserInfo.getNickName();
        this.avatar = wxUserInfo.getAvatarUrl();
        this.phone = "";
        this.sex = wxUserInfo.getGender();
        this.openId = wxUserInfo.getOpenId();
        this.wxUnionId = wxUserInfo.getUnionId();
    }
}
