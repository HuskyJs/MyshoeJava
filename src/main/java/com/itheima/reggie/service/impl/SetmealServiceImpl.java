package com.itheima.reggie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.reggie.common.CustomException;
import com.itheima.reggie.dto.SetmealDto;
import com.itheima.reggie.entity.Setmeal;
import com.itheima.reggie.entity.SetmealGoods;
import com.itheima.reggie.mapper.SetmealMapper;
import com.itheima.reggie.service.SetmealGoodsService;
import com.itheima.reggie.service.SetmealService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SetmealServiceImpl extends ServiceImpl<SetmealMapper,Setmeal> implements SetmealService {

    @Autowired
    private SetmealGoodsService setmealGoodsService;

    /**
     * 新增套餐，同时需要保存套餐和菜品的关联关系
     * @param setmealDto
     */
    @Transactional
    public void saveWithDish(SetmealDto setmealDto) {
        //保存套餐的基本信息，操作setmeal，执行insert操作
        this.save(setmealDto);

        List<SetmealGoods> setmealGoods = setmealDto.getSetmealGoods();
        setmealGoods.stream().map((item) -> {
            item.setSetmealId(setmealDto.getId());
            return item;
        }).collect(Collectors.toList());

        //保存套餐和菜品的关联信息，操作setmeal_dish,执行insert操作
        setmealGoodsService.saveBatch(setmealGoods);
    }

    @Override
    public SetmealDto getByIdWithGoods(Long id) {
        //查询套餐基本信息，从setmeal表查询
        Setmeal setmeal = this.getById(id);
        SetmealDto setmealDto = new SetmealDto();
        BeanUtils.copyProperties(setmeal,setmealDto);
        //查询当前套餐对应的菜品信息，从setmeal_dish表查询
        LambdaQueryWrapper<SetmealGoods> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SetmealGoods::getSetmealId,setmeal.getId());
        List<SetmealGoods> goods = setmealGoodsService.list(queryWrapper);
        setmealDto.setSetmealGoods(goods);
        return setmealDto;
    }

    @Override
    @Transactional
    public void updateWithGoods(SetmealDto setmealDto) {
        //更新setmeal表的基本信息
        this.updateById(setmealDto);
        //清理当前套餐对应的菜品数据---setmeal_dish表的delete操作
        LambdaQueryWrapper<SetmealGoods> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SetmealGoods::getSetmealId,setmealDto.getId());
        setmealGoodsService.remove(queryWrapper);
        //添加当前提交过来的菜品数据---setmeal_dish表的insert操作
        List<SetmealGoods> dishes = setmealDto.getSetmealGoods();
        dishes = dishes.stream().map((item)->{
            item.setId(IdWorker.getId());
            item.setSetmealId(setmealDto.getId());
            return item;
        }).collect(Collectors.toList());
        setmealGoodsService.saveBatch(dishes);
    }



    /**
     * 删除套餐，同时需要删除套餐和菜品的关联数据
     * @param ids
     */
    @Transactional
    public void removeWithDish(List<Long> ids) {
        //select count(*) from setmeal where id in (1,2,3) and status = 1
        //查询套餐状态，确定是否可用删除
        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.in(Setmeal::getId,ids);
        queryWrapper.eq(Setmeal::getStatus,1);

        int count = this.count(queryWrapper);
        if(count > 0){
            //如果不能删除，抛出一个业务异常
            throw new CustomException("套餐正在售卖中，不能删除");
        }

        //如果可以删除，先删除套餐表中的数据---setmeal
        this.removeByIds(ids);

        //delete from setmeal_dish where setmeal_id in (1,2,3)
        LambdaQueryWrapper<SetmealGoods> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.in(SetmealGoods::getSetmealId,ids);
        //删除关系表中的数据----setmeal_dish
        setmealGoodsService.remove(lambdaQueryWrapper);
    }
}
