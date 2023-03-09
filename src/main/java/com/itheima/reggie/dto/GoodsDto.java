package com.itheima.reggie.dto;

import com.itheima.reggie.entity.GoodsFlavor;
import com.itheima.reggie.entity.Goods;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class GoodsDto extends Goods {

    //菜品对应的口味数据
    private List<GoodsFlavor> flavors = new ArrayList<>();

    private String categoryName;

    private Integer copies;
}
