package com.pinyougou.search.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.core.query.Criteria;
import org.springframework.data.solr.core.query.GroupOptions;
import org.springframework.data.solr.core.query.HighlightOptions;
import org.springframework.data.solr.core.query.HighlightQuery;
import org.springframework.data.solr.core.query.Query;
import org.springframework.data.solr.core.query.SimpleHighlightQuery;
import org.springframework.data.solr.core.query.SimpleQuery;
import org.springframework.data.solr.core.query.result.GroupEntry;
import org.springframework.data.solr.core.query.result.GroupPage;
import org.springframework.data.solr.core.query.result.GroupResult;
import org.springframework.data.solr.core.query.result.HighlightEntry;
import org.springframework.data.solr.core.query.result.HighlightEntry.Highlight;
import org.springframework.data.solr.core.query.result.HighlightPage;

import com.alibaba.dubbo.config.annotation.Service;
import com.pinyougou.pojo.TbItem;
import com.pinyougou.search.service.ItemSearchService;

@Service(timeout = 10000) // 超时5S，默认是1S
public class ItemSearchServiceImpl implements ItemSearchService {

	@Autowired
	private SolrTemplate solrTemplate;
	
	@Autowired
	private RedisTemplate<String, ?> redisTemplate;
	
	@Override
	public Map search(Map searchMap) {
		Map<Object, Object> map = new HashMap<>();
		
		// 查询列表
		map.putAll(searchLsit(searchMap));
	
		// 查询分组 商品分类列表
		List<String> searchCategoryList = searchCategoryList(searchMap);
		map.put("categoryList", searchCategoryList);
		
		if (searchCategoryList.size()>0) {
			Map brandAndSpecList = searchBrandAndSpecList(searchCategoryList.get(0));
			map.putAll(brandAndSpecList);
		}
		
		return map;
	}

	// 查询列表
	private Map<Object, Object> searchLsit(Map<?, ?> searchMap) {
		Map<Object, Object> map = new HashMap<>();
		// 高亮显示
		HighlightQuery query = new SimpleHighlightQuery();

		// 构建高亮选项
		HighlightOptions highlightOptions = new HighlightOptions().addField("item_title"); // 高亮域（可以为多个）
		highlightOptions.setSimplePrefix("<em style='color:red'>"); // 前缀
		highlightOptions.setSimplePostfix("</em>"); // 后缀

		query.setHighlightOptions(highlightOptions); // 为查询设置高亮查询

		Criteria criteria = new Criteria("item_keywords").is(searchMap.get("keywords"));
		query.addCriteria(criteria);

		HighlightPage<TbItem> page = solrTemplate.queryForHighlightPage(query, TbItem.class);
		// 高亮入口集合（每条高亮结果的入口）
		List<HighlightEntry<TbItem>> entryList = page.getHighlighted();

		for (HighlightEntry<TbItem> entry : entryList) {
			// 获取高亮列表（高亮域的个数）
			List<Highlight> hightLightList = entry.getHighlights();
			/*
			 * for (Highlight highLight : hightLightList) { // 每个域可能存在多值（复制域） List<String>
			 * sns = highLight.getSnipplets(); System.out.println(sns); }
			 */
			if (hightLightList.size() > 0 && hightLightList.get(0).getSnipplets().size() > 0) {
				TbItem item = entry.getEntity();
				item.setTitle(hightLightList.get(0).getSnipplets().get(0)); // 用高亮标签结果替换
			}
		}
		map.put("rows", page.getContent());
		return map;
	}
	
	private List<String> searchCategoryList(Map searchMap) {
		List<String> list = new ArrayList<>();
		
		Query query = new SimpleQuery("*:*");
		
		// 根据关键字查询
		Criteria criteria = new Criteria("item_keywords").is(searchMap.get("keywords"));	// where ...
		query.addCriteria(criteria);
		
		// 设置分组选项
		GroupOptions groupOptions = new GroupOptions().addGroupByField("item_category");	// group by ....（可以有多个分组域）
		query.setGroupOptions(groupOptions);
		
		// 获取分组页
		GroupPage<TbItem> queryForGroupPage = solrTemplate.queryForGroupPage(query, TbItem.class);
		// 获取分组结果对象
		GroupResult<TbItem> groupResult = queryForGroupPage.getGroupResult("item_category");
		// 获取分组入口页
		Page<GroupEntry<TbItem>> groupEntries = groupResult.getGroupEntries();
		// 遍历获取每个对象的值
		for(GroupEntry<TbItem> entry : groupEntries) {
			list.add(entry.getGroupValue());
		}
		return list;
	}

	/**
	 *  根据商品分类名称来查询品牌和规格信息
	 * @param categoryName
	 * @return
	 */
	private Map searchBrandAndSpecList(String categoryName) {
		Map<Object, Object> map = new HashMap<>();
		
		// 1.根据商品分类名称得到模板ID
		Long categoryId = (Long) redisTemplate.boundHashOps("itemCat").get(categoryName);
		if (categoryId == null) return null;
		
		// 2.根据模板ID获取品牌列表
		List brandList = (List) redisTemplate.boundHashOps("brandList").get(categoryId);
		map.put("brandList", brandList);
		
		// 3.根据模板ID获取规格列表
		List specList = (List) redisTemplate.boundHashOps("specList").get(categoryId);
		map.put("specList", specList);
		
		return map;
	}
	
	
}
