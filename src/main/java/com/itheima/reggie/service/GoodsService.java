package com.itheima.reggie.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.itheima.reggie.dto.GoodsDto;
import com.itheima.reggie.entity.Goods;

import java.util.List;

public interface GoodsService extends IService<Goods> {

    //新增菜品，同时插入菜品对应的口味数据，需要操作两张表：dish、dish_flavor
    public void saveWithFlavor(GoodsDto goodsDto);

    //根据id查询菜品信息和对应的口味信息
    public GoodsDto getByIdWithFlavor(Long id);

    //更新菜品信息，同时更新对应的口味信息
    public void updateWithFlavor(GoodsDto goodsDto);

    void deleteByIds(List<Long> ids);
}
