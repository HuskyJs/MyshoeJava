package com.itheima.reggie.entity;

import lombok.Data;

@Data
public class WxAuth {
    /**
     * 第一步信息
     */

    private String openid;
    //第一步传递前端的sessionId
    private String sessionId;

    /**
     * 第二步
     */
    //微信传递的加密数据，后端解密
    private String encryptedData;
    //微信传递，解密算法初始向量
    private String iv;

}
