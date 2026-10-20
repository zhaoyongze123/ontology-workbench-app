package com.devin.ontology.common.utils.r;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 2025/7/14 15:56.
 *
 * <p></p>
 *
 * @author <a href="https://github.com/wzh-devin">devin</a>
 * @version 1.0
 * @since 1.0
 */
@Data
public class PageResult<T> {

    /**
     * 当前页码.
     */
    private int pageNum;

    /**
     * 每页数量.
     */
    private int pageSize;

    /**
     * 总数.
     */
    private int total;

    /**
     * 数据列表.
     */
    private List<T> records;

    /**
     * 对象数据.
     */
    private T data;

    /**
     * 构造方法.
     *
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @param total    总数
     * @param records  数据列表
     * @param <T>      数据类型
     * @return PageResult
     */
    public static <T> PageResult<T> of(final long pageNum, final long pageSize, final long total, final List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setPageNum((int) pageNum);
        result.setPageSize((int) pageSize);
        result.setTotal((int) total);
        result.setRecords(records);
        return result;
    }

    /**
     * 构造方法.
     *
     * @param page    分页对象
     * @param records 数据列表
     * @param <T>     数据类型
     * @return PageResult
     */
    public static <T> PageResult<T> of(final List<T> records, final Page<?> page) {
        PageResult<T> result = new PageResult<>();
        result.setPageNum((int) page.getCurrent());
        result.setPageSize((int) page.getSize());
        result.setTotal((int) page.getTotal());
        result.setRecords(records);
        return result;
    }

    /**
     * 构造方法.
     *
     * @param page Page对象
     * @param <T>  数据类型
     * @return PageResult
     */
    public static <T> PageResult<T> of(final Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setPageNum((int) page.getCurrent());
        result.setPageSize((int) page.getSize());
        result.setTotal((int) page.getTotal());
        result.setRecords(page.getRecords());
        return result;
    }

    /**
     * 构造方法.
     *
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @param total    总数
     * @param data     数据
     * @param <T>      数据类型
     * @return PageResult
     */
    public static <T> PageResult<T> of(final long pageNum, final long pageSize, final long total, final T data) {
        PageResult<T> result = new PageResult<>();
        result.setPageNum((int) pageNum);
        result.setPageSize((int) pageSize);
        result.setTotal((int) total);
        result.setData(data);
        return result;
    }

    /**
     * 构造方法.
     *
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @param <T>      数据类型
     * @return PageResult
     */
    public static <T> PageResult<T> ofEmpty(final Integer pageNum, final Integer pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setTotal(0);
        result.setRecords(new ArrayList<>());
        return result;
    }

    /**
     * 构造方法.
     *
     * @param page     分页对象
     * @param <T>      数据类型
     * @return PageResult
     */
    public static <T> PageResult<T> ofEmpty(final Page<?> page) {
        return ofEmpty(Integer.valueOf(String.valueOf(page.getCurrent())), Integer.valueOf(String.valueOf(page.getSize())));
    }
}
