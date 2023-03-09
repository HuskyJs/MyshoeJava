package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.common.R;
import com.itheima.reggie.dto.GoodsDto;
import com.itheima.reggie.entity.Category;
import com.itheima.reggie.entity.GoodsFlavor;
import com.itheima.reggie.entity.Goods;
import com.itheima.reggie.service.CategoryService;
import com.itheima.reggie.service.GoodsService;
import com.itheima.reggie.service.GoodsFlavorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 服务管理
 */
@RestController
@RequestMapping("/goods")
@Slf4j
public class GoodsController {
    @Autowired
    private GoodsService goodsService;

    @Autowired
    private GoodsFlavorService goodsFlavorService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 新增服务
     * @param goodsDto
     * @return
     */
    @PostMapping
    public R<String> save(@RequestBody GoodsDto goodsDto){
        log.info(goodsDto.toString());

        goodsService.saveWithFlavor(goodsDto);

        return R.success("新增菜品成功");
    }

    /**
     * 菜品信息分页查询
     * @param page
     * @param pageSize
     * @param name
     * @return
     */
    @GetMapping("/page")
    public R<Page<GoodsDto>> page(int page, int pageSize, String name){

        //构造分页构造器对象
        Page<Goods> pageInfo = new Page<>(page,pageSize);
        Page<GoodsDto> goodsDtoPage = new Page<>();

        //条件构造器
        LambdaQueryWrapper<Goods> queryWrapper = new LambdaQueryWrapper<>();
        //添加过滤条件
        queryWrapper.like(name != null,Goods::getName,name);
        //添加排序条件
        queryWrapper.orderByDesc(Goods::getUpdateTime);

        //执行分页查询
        goodsService.page(pageInfo,queryWrapper);

        //对象拷贝
        BeanUtils.copyProperties(pageInfo,goodsDtoPage,"records");

        List<Goods> records = pageInfo.getRecords();

        List<GoodsDto> list = records.stream().map((item) -> {
            GoodsDto goodsDto = new GoodsDto();

            BeanUtils.copyProperties(item, goodsDto);

            Long categoryId = item.getCategoryId();//分类id
            //根据id查询分类对象
            Category category = categoryService.getById(categoryId);

            if(category != null){
                String categoryName = category.getName();
                goodsDto.setCategoryName(categoryName);
            }
            return goodsDto;
        }).collect(Collectors.toList());

        goodsDtoPage.setRecords(list);

        return R.success(goodsDtoPage);
    }

    /**
     * 根据id查询菜品信息和对应的口味信息
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public R<GoodsDto> get(@PathVariable Long id){

        GoodsDto goodsDto = goodsService.getByIdWithFlavor(id);

        return R.success(goodsDto);
    }

    /**
     * 修改菜品
     * @param goodsDto
     * @return
     */
    @PutMapping
    public R<String> update(@RequestBody GoodsDto goodsDto){
        log.info(goodsDto.toString());

        goodsService.updateWithFlavor(goodsDto);

        return R.success("修改菜品成功");
    }


    /**
     * 根据条件查询对应的菜品数据
     * @param goods
     * @return
     */
    @GetMapping("/list")
    public R<List<GoodsDto>> list(Goods goods){
        //构造查询条件
        LambdaQueryWrapper<Goods> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(goods.getCategoryId() != null ,Goods::getCategoryId,goods.getCategoryId());
        //添加条件，查询状态为1（起售状态）的菜品
        queryWrapper.eq(Goods::getStatus,1);

        //添加排序条件
        queryWrapper.orderByAsc(Goods::getSort).orderByDesc(Goods::getUpdateTime);

        List<Goods> list = goodsService.list(queryWrapper);

        List<GoodsDto> goodsDtoList = list.stream().map((item) -> {
            GoodsDto goodsDto;
            goodsDto = new GoodsDto();

            BeanUtils.copyProperties(item, goodsDto);

            Long categoryId = item.getCategoryId();//分类id
            //根据id查询分类对象
            Category category = categoryService.getById(categoryId);

            if(category != null){
                String categoryName = category.getName();
                goodsDto.setCategoryName(categoryName);
            }

            //当前菜品的id
            Long goodsId = item.getId();
            LambdaQueryWrapper<GoodsFlavor> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(GoodsFlavor::getGoodsId,goodsId);
            //SQL:select * from dish_flavor where dish_id = ?
            List<GoodsFlavor> goodsFlavorList = goodsFlavorService.list(lambdaQueryWrapper);
            goodsDto.setFlavors(goodsFlavorList);
            return goodsDto;
        }).collect(Collectors.toList());


        return R.success(goodsDtoList);
    }

    /**
     * 对菜品批量或者是单个 进行停售或者是起售
     * @return
     */
    @PostMapping("/status/{status}")
    //这个参数这里一定记得加注解才能获取到参数，否则这里非常容易出问题
    public R<String> status(@PathVariable Integer status,@RequestParam List<Long> ids){
        log.info("status:{}",status);
        log.info("ids:{}",ids);
        LambdaQueryWrapper<Goods> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.in(ids !=null,Goods::getId,ids);
        //根据传入的id集合进行批量查询
        List<Goods> list = goodsService.list(queryWrapper);

        for (Goods dish : list) {
            if (dish != null){
                dish.setStatus(status);
                goodsService.updateById(dish);
            }
        }
        //清理所有菜品的缓存数据
        Set keys = redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);

        return R.success("售卖状态修改成功");
    }


    /**
     * 商品批量删除和单个删除
     * @return
     */
    @DeleteMapping
    public R<String> delete(@RequestParam("ids") List<Long> ids){
        //删除商品  这里的删除是逻辑删除
        goodsService.deleteByIds(ids);
        //删除商品对应的口味  也是逻辑删除
        LambdaQueryWrapper<GoodsFlavor> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(GoodsFlavor::getGoodsId,ids);
        goodsFlavorService.remove(queryWrapper);

        //清理所有商品的缓存数据
        Set keys = redisTemplate.keys("dish_*");
        redisTemplate.delete(keys);
        return R.success("菜品删除成功");
    }



}
