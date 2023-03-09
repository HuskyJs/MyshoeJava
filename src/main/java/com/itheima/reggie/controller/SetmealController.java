package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.common.R;
import com.itheima.reggie.dto.GoodsDto;
import com.itheima.reggie.dto.SetmealDto;
import com.itheima.reggie.entity.*;
import com.itheima.reggie.service.CategoryService;
import com.itheima.reggie.service.GoodsService;
import com.itheima.reggie.service.SetmealGoodsService;
import com.itheima.reggie.service.SetmealService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 套餐管理
 */

@RestController
@RequestMapping("/setmeal")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private SetmealGoodsService setmealGoodsService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 新增套餐
     * @param setmealDto
     * @return
     */
    @PostMapping
    public R<String> save(@RequestBody SetmealDto setmealDto){
        log.info("套餐信息：{}",setmealDto);

        setmealService.saveWithDish(setmealDto);

        return R.success("新增套餐成功");
    }
    /**
     * 修改套餐
     * @param setmealDto
     * @return
     */
    @PutMapping
    public R<String> update(@RequestBody SetmealDto setmealDto){
        log.info(setmealDto.toString());

        setmealService.updateWithGoods(setmealDto);

        return R.success("修改套餐成功");
    }

    /**
     * 套餐分页查询
     * @param page
     * @param pageSize
     * @param name
     * @return
     */
    @GetMapping("/page")
    public R<Page> page(int page,int pageSize,String name){
        //分页构造器对象
        Page<Setmeal> pageInfo = new Page<>(page,pageSize);
        Page<SetmealDto> dtoPage = new Page<>();

        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper<>();
        //添加查询条件，根据name进行like模糊查询
        queryWrapper.like(name != null,Setmeal::getName,name);
        //添加排序条件，根据更新时间降序排列
        queryWrapper.orderByDesc(Setmeal::getUpdateTime);

        setmealService.page(pageInfo,queryWrapper);

        //对象拷贝
        BeanUtils.copyProperties(pageInfo,dtoPage,"records");
        List<Setmeal> records = pageInfo.getRecords();

        List<SetmealDto> list = records.stream().map((item) -> {
            SetmealDto setmealDto = new SetmealDto();
            //对象拷贝
            BeanUtils.copyProperties(item,setmealDto);
            //分类id
            Long categoryId = item.getCategoryId();
            //根据分类id查询分类对象
            Category category = categoryService.getById(categoryId);
            if(category != null){
                //分类名称
                String categoryName = category.getName();
                setmealDto.setCategoryName(categoryName);
            }
            return setmealDto;
        }).collect(Collectors.toList());

        dtoPage.setRecords(list);
        return R.success(dtoPage);
    }

    /**
     * 根据条件查询套餐数据
     * @param setmeal
     * @return
     */
    @GetMapping("/list")
    public R<List<Setmeal>> list(Setmeal setmeal){
        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(setmeal.getCategoryId() != null,Setmeal::getCategoryId,setmeal.getCategoryId());
        queryWrapper.eq(setmeal.getStatus() != null,Setmeal::getStatus,setmeal.getStatus());
        queryWrapper.orderByDesc(Setmeal::getUpdateTime);

        List<Setmeal> list = setmealService.list(queryWrapper);

        return R.success(list);
    }

    /**
     * 点击查看套餐中的菜品
     */
    @GetMapping("/dish/{id}")
    public R<List<GoodsDto>> dish(@PathVariable("id") Long SetmealId) {
        LambdaQueryWrapper<SetmealGoods> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SetmealGoods::getSetmealId, SetmealId);
        //获取套餐里面的所有菜品  这个就是SetmealDish表里面的数据
        List<SetmealGoods> list = setmealGoodsService.list(queryWrapper);

        List<GoodsDto> goodsDtos = list.stream().map((setmealDish) -> {
            GoodsDto goodsDto = new GoodsDto();
            //将套餐菜品关系表中的数据拷贝到dishDto中
            BeanUtils.copyProperties(setmealDish, goodsDto);
            //这里是为了把套餐中的菜品的基本信息填充到dto中，比如菜品描述，菜品图片等菜品的基本信息
            Long goodsId = setmealDish.getGoodsId();
            Goods goods = goodsService.getById(goodsId);
            //将菜品信息拷贝到dishDto中
            BeanUtils.copyProperties(goods, goodsDto);

            return goodsDto;
        }).collect(Collectors.toList());

        return R.success(goodsDtos);
    }


    /**
     * 套餐批量删除和单个删除
     * @return
     */
    @DeleteMapping
    public R<String> delete(@RequestParam("ids") List<Long> ids){
        //删除套餐  这里的删除是逻辑删除
        setmealService.removeWithDish(ids);
        //删除套餐关联的菜品  也是逻辑删除
        LambdaQueryWrapper<SetmealGoods> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(SetmealGoods::getSetmealId,ids);
        setmealGoodsService.remove(queryWrapper);

        //清理所有套餐的缓存数据
        Set keys = redisTemplate.keys("setmeal_*");
        redisTemplate.delete(keys);
        return R.success("菜品删除成功");
    }
    /**
     * 对套餐批量或者是单个 进行停售或者是起售
     * @return
     */
    @PostMapping("/status/{status}")
    //这个参数这里一定记得加注解才能获取到参数，否则这里非常容易出问题
    public R<String> status(@PathVariable Integer status,@RequestParam List<Long> ids){
        log.info("status:{}",status);
        log.info("ids:{}",ids);
        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.in(ids !=null,Setmeal::getId,ids);
        //根据传入的id集合进行批量查询
        List<Setmeal> list = setmealService.list(queryWrapper);

        for (Setmeal setmeal : list) {
            if (setmeal != null){
                setmeal.setStatus(status);
                setmealService.updateById(setmeal);
            }
        }
        //清理所有套餐的缓存数据
        Set keys = redisTemplate.keys("setmeal_*");
        redisTemplate.delete(keys);

        return R.success("售卖状态修改成功");
    }
}
