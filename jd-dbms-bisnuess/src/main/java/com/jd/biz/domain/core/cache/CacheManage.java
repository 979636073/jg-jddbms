package com.jd.biz.domain.core.cache;

import com.alibaba.fastjson2.JSON;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

public class CacheManage {
    private static final String PATH = System.getProperty("user.home") + File.separator + ".jddms"
        + File.separator
        + "cache" + File.separator + "ehcache-data_" +"dev";

    private static final String CACHE = "meta_cache";
    private static final String COLUMN_CACHE = "table_columns";
    private static final String RELATION_CACHE = "table_relations";
    private static CacheManager cacheManager;

    static {
        cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
            .with(CacheManagerBuilder.persistence(PATH)) // 确保这个路径是存在且有写权限的
            .withCache(CACHE, CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class,
                ResourcePoolsBuilder.newResourcePoolsBuilder()
                    .heap(1000, EntryUnit.ENTRIES)
                    .disk(20, MemoryUnit.GB, true))) // 磁盘持久化设置为true
            .withCache(COLUMN_CACHE, CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class,
                    ResourcePoolsBuilder.heap(1000))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(5))))
            .withCache(RELATION_CACHE, CacheConfigurationBuilder.newCacheConfigurationBuilder(String.class, String.class,
                    ResourcePoolsBuilder.heap(1000))
                    .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(5))))
            .build(true);
    }

    public static <T> T get(String key, Class<T> clazz) {
        Cache<String, String> myCache = cacheManager.getCache(CACHE, String.class, String.class);
        String value = myCache.get(key);
        if (!StringUtils.isEmpty(value)) {
            return JSON.parseObject(value, clazz);
        }
        return null;
    }

    public static <T> List<T> getList(String key, Class<T> clazz) {
        Cache<String, String> myCache = cacheManager.getCache(CACHE, String.class, String.class);
        String value = myCache.get(key);
        if (!StringUtils.isEmpty(value)) {
            return JSON.parseArray(value, clazz);
        }
        return null;
    }

    public static <T> T get(String key, Class<T> clazz, Function<Object, Boolean> refresh,
        Function<Object, T> function) {
        T t;
        if (refresh.apply(key)) {
            t = function.apply(key);
            put(key, t);
        } else {
            t = get(key, clazz);
            if (t == null) {
                t = function.apply(key);
                put(key, t);
            }
        }
        return t;
    }

    public static <T> List<T> getList(String key, Class<T> clazz, Function<Object, Boolean> refresh,
        Function<Object, List<T>> function) {
        List<T> t;
        if (refresh.apply(key)) {
            t = function.apply(key);
            put(key, t);
        } else {
            t = getList(key, clazz);
            if (t == null) {
                t = function.apply(key);
                put(key, t);
            }
        }
        return t;
    }

    public static void put(String s, Object value) {
        Cache<String, String> myCache = cacheManager.getCache(CACHE, String.class, String.class);
        myCache.put(s, JSON.toJSONString(value));
    }

    public static <T> List<T> getColumns(String key, Class<T> clazz, boolean refresh,
                                         Function<Object, List<T>> loader) {
        Cache<String, String> cache = cacheManager.getCache(COLUMN_CACHE, String.class, String.class);
        String value = refresh ? null : cache.get(key);
        if (value != null) return JSON.parseArray(value, clazz);
        List<T> columns = loader.apply(key);
        if (columns != null) cache.put(key, JSON.toJSONString(columns));
        return columns;
    }

    public static void invalidateColumns(Long dataSourceId) {
        if (dataSourceId == null) return;
        Cache<String, String> cache = cacheManager.getCache(COLUMN_CACHE, String.class, String.class);
        String prefix = CacheKey.getColumnPrefix(dataSourceId);
        for (Cache.Entry<String, String> entry : cache) {
            if (entry.getKey().startsWith(prefix)) cache.remove(entry.getKey());
        }
    }

    public static <T> T getRelations(String key, Class<T> clazz, boolean refresh, Function<Object, T> loader) {
        Cache<String, String> cache = cacheManager.getCache(RELATION_CACHE, String.class, String.class);
        String value = refresh ? null : cache.get(key);
        if (value != null) return JSON.parseObject(value, clazz);
        T result = loader.apply(key);
        // Cache null too: tables without relationships must not repeat catalog scans.
        cache.put(key, JSON.toJSONString(result));
        return result;
    }

    public static void invalidateRelations(Long dataSourceId) {
        if (dataSourceId == null) return;
        Cache<String, String> cache = cacheManager.getCache(RELATION_CACHE, String.class, String.class);
        String prefix = CacheKey.getRelationPrefix(dataSourceId);
        for (Cache.Entry<String, String> entry : cache) {
            if (entry.getKey().startsWith(prefix)) cache.remove(entry.getKey());
        }
    }

    public static void close() {
        cacheManager.close();
    }

}
