package com.devin.ontology.project;

import java.util.List;

/** 成员和搜索结果均使用有界分页，避免返回无界集合。 */
public record ProjectPage<T>(List<T> records, long pageNum, long pageSize, long total) {
}
