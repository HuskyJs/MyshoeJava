package com.itheima.reggie.service.impl;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.reggie.common.R;
import com.itheima.reggie.dto.UserDto;
import com.itheima.reggie.entity.User;
import com.itheima.reggie.entity.WxAuth;
import com.itheima.reggie.entity.WxUserInfo;
import com.itheima.reggie.mapper.UserMapper;
import com.itheima.reggie.service.UserService;
import com.itheima.reggie.service.WxService;
import com.itheima.reggie.utils.JWTUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper,User> implements UserService{

    @Resource
    private UserMapper userMapper;
    @Autowired
    private WxService wxService;

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Value("${wxmini.secret}")
    private String secret;
    @Value("${wxmini.appid}")
    private String appid;

    public WxAuth getSessionId(String code) {
        /**
         * 1.拼接一个url
         */
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid={0}&secret={1}&js_code={2}&grant_type=authorization_code";
        url = url.replace("{0}", appid).replace("{1}", secret).replace("{2}", code);
        String res = HttpUtil.get(url);
        String s = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("wechatSessionId" + s, url);


        /**
         * 取出session_key、openid
         */
        //res: {"session_key":"z2UQTc36MUtPP7rz\/GBLMw==","openid":"oqOp15O4Il36d6WB4E4RmAzNdUvg"}
        String[] split = res.split("\"");
        //split: {"session_key":"z2UQTc36MUtPP7rz\/GBLMw==","openid":"oqOp15O4Il36d6WB4E4RmAzNdUvg"}
        WxAuth wxAuth = new WxAuth();
        wxAuth.setSessionId(split[3]);
        wxAuth.setOpenid(split[7]);
        return wxAuth;
    }

    /**
     * 微信登录第二步
     * @param userDto
     * @return
     */
    public UserDto login(UserDto userDto) {
        // 登录成功 封装用户信息到token
        userDto.setOpenId(null);
        userDto.setWxUnionId(null);
        String token = JWTUtils.sign(userDto.getId());
        userDto.setToken(token);
        //保存到redis内,下次就直接跳过验证
        redisTemplate.opsForValue().set("tokenkey" + token, JSON.toJSONString(userDto), 7, TimeUnit.DAYS);
        return userDto;
    }

    public R<UserDto> register(UserDto userDto) {
        User user = new User();
        BeanUtils.copyProperties(userDto,user);
        User queryUser = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getOpenId, user.getOpenId()));
        if (queryUser == null) {
            userMapper.insert(user);
        }
        //已存在直接登录
        return R.success(login(userDto));
    }

    public R<UserDto> authLogin(WxAuth wxAuth) {
        try {
            String wxRes = wxService.wxDecrypt(wxAuth.getEncryptedData(), wxAuth.getSessionId(), wxAuth.getIv());
            WxUserInfo wxUserInfo = JSON.parseObject(wxRes,WxUserInfo.class);

            User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getOpenId, wxUserInfo.getOpenId()));
            UserDto userDto = new UserDto();
            if (user != null) {
                //登录成功
                BeanUtils.copyProperties(user,userDto);
                return R.success(this.login(userDto));
            } else {
                userDto.from(wxUserInfo);
                return this.register(userDto);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return R.error("登录失败");
    }

}
