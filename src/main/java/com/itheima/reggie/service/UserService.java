package com.itheima.reggie.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.reggie.common.R;
import com.itheima.reggie.dto.UserDto;
import com.itheima.reggie.entity.User;
import com.itheima.reggie.entity.WxAuth;

public interface UserService extends IService<User> {

    WxAuth getSessionId(String code);

    R<UserDto> authLogin(WxAuth wxAuth);
}
