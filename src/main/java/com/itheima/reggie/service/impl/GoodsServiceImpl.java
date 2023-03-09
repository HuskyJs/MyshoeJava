package com.itheima.reggie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.reggie.common.CustomException;
import com.itheima.reggie.dto.GoodsDto;
import com.itheima.reggie.entity.GoodsFlavor;
import com.itheima.reggie.entity.Goods;
import com.itheima.reggie.mapper.GoodsMapper;
import com.itheima.reggie.service.GoodsFlavorService;
import com.itheima.reggie.service.GoodsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, Goods> implements GoodsService {

    @Autowired
    private GoodsFlavorService goodsFlavorService;

    /**
     * 新增菜品，同时保存对应的口味数据
     * @param goodsDto
     */
    @Transactional
    public void saveWithFlavor(GoodsDto goodsDto) {
        //保存菜品的基本信息到菜品表dish
        this.save(goodsDto);

        Long goodsId = goodsDto.getId();//菜品id

        //菜品口味
        List<GoodsFlavor> flavors = goodsDto.getFlavors();
        flavors = flavors.stream().map((item) -> {
            item.setGoodsId(goodsId);
            return item;
        }).collect(Collectors.toList());

        //保存菜品口味数据到菜品口味表dish_flavor
        goodsFlavorService.saveBatch(flavors);

    }

    /**
     * 根据id查询菜品信息和对应的口味信息
     * @param id
     * @return
     */
    public GoodsDto getByIdWithFlavor(Long id) {
        //查询菜品基本信息，从dish表查询
        Goods goods = this.getById(id);

        GoodsDto goodsDto = new GoodsDto();
        BeanUtils.copyProperties(goods, goodsDto);

        //查询当前菜品对应的口味信息，从dish_flavor表查询
        LambdaQueryWrapper<GoodsFlavor> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GoodsFlavor::getGoodsId,goods.getId());
        List<GoodsFlavor> flavors = goodsFlavorService.list(queryWrapper);
        goodsDto.setFlavors(flavors);

        return goodsDto;
    }

    /**
     * 修改商品信息
     * @param goodsDto
     */
    @Override
    @Transactional
    public void updateWithFlavor(GoodsDto goodsDto) {
        //更新dish表基本信息
        this.updateById(goodsDto);

        //清理当前菜品对应口味数据---dish_flavor表的delete操作
        LambdaQueryWrapper<GoodsFlavor> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(GoodsFlavor::getGoodsId, goodsDto.getId());

        goodsFlavorService.remove(queryWrapper);

        //添加当前提交过来的口味数据---dish_flavor表的insert操作
        List<GoodsFlavor> flavors = goodsDto.getFlavors();

        flavors = flavors.stream().map((item) -> {
            item.setGoodsId(goodsDto.getId());
            return item;
        }).collect(Collectors.toList());

        goodsFlavorService.saveBatch(flavors);
    }

    /**
     *套餐批量删除和单个删除
     * @param ids
     */
    @Override
    @Transactional
    public void deleteByIds(List<Long> ids) {

        //构造条件查询器
        LambdaQueryWrapper<Goods> queryWrapper = new LambdaQueryWrapper<>();
        //先查询该菜品是否在售卖，如果是则抛出业务异常
        queryWrapper.in(ids!=null,Goods::getId,ids);
        List<Goods> list = this.list(queryWrapper);
        for (Goods good : list) {
            Integer status = good.getStatus();
            //如果不是在售卖,则可以删除
            if (status == 0){
                this.removeById(good.getId());
            }else {
                //此时应该回滚,因为可能前面的删除了，但是后面的是正在售卖
                throw new CustomException("删除菜品中有正在售卖菜品,无法全部删除");
            }
        }
    }

}
