package com.rcszh.tax.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rcszh.tax.common.ApiResponse;
import com.rcszh.tax.dto.WordAreaDto;
import com.rcszh.tax.entity.WordArea;
import com.rcszh.tax.mapper.WordAreaMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/areas")
public class AreaController {
    private final WordAreaMapper wordAreaMapper;

    public AreaController(WordAreaMapper wordAreaMapper) {
        this.wordAreaMapper = wordAreaMapper;
    }

    @GetMapping
    public ApiResponse<List<WordAreaDto>> getAreaList() {
        List<WordArea> parentArea = wordAreaMapper.selectList(new LambdaQueryWrapper<WordArea>().isNull(WordArea::getParentId));
        List<WordAreaDto> result = new ArrayList<>();
        for (WordArea parent : parentArea) {
            WordAreaDto wordArea = new WordAreaDto();
            BeanUtil.copyProperties(parent, wordArea);
            wordArea.setChildren(new ArrayList<>());
            result.add(wordArea);
        }
        List<WordArea> wordAreas = wordAreaMapper.selectList(new LambdaQueryWrapper<WordArea>().isNotNull(WordArea::getParentId));
        Map<Long, List<WordArea>> areaMap = wordAreas.stream().collect(Collectors.groupingBy(WordArea::getParentId));
        for (WordAreaDto wordArea : result) {
            wordArea.setChildren(areaMap.getOrDefault(wordArea.getId(), new ArrayList<>()));
        }
        return ApiResponse.success(result);
    }
}
