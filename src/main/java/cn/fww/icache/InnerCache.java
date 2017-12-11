package cn.fww.icache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @description: 一级缓存
 * @author: Wen
 * @date : create in 2017/11/23 11:57
 */
public class InnerCache {
    private static final Logger logger = LoggerFactory.getLogger(InnerCache.class);

    /**
     * 数据缓存时间(-1为随应用的生命周期)
     */
    private static final int TTL = -1;
    /**
     * 最多缓存多少数据
     */
    private static final int MAX_OBJECT_COUNT = 100000;
    /**
     * 缓存map
     */
    private static final ConcurrentHashMap<String, CacheObject> CACHE_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> CACHE_VERSION_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> CACHE_ACTOR_PATH = new ConcurrentHashMap<>();

    private InnerCache() {
    }

    /**
     * 添加缓存
     *
     * @param key
     * @param value
     * @param ttl
     * @return
     */
    protected static boolean put(String key, Object value, int ttl, boolean isVersion) {
        if (CACHE_MAP.size() > MAX_OBJECT_COUNT) {
            return false;
        }
        try {
            long timeStamp = System.currentTimeMillis();
            CacheObject cacheObject = new CacheObject();
            cacheObject.setObj(value);
            cacheObject.setExpireTime(ttl == -1 ? ttl : timeStamp + ttl);
            CACHE_MAP.put(key, cacheObject);
            return true;
        } catch (Exception e) {
            logger.error("添加内部缓存出错。", e);
            return false;
        }
    }


    /**
     * 获取缓存数据
     *
     * @param key
     * @return
     */
    protected static Object get(String key) {
        CacheObject cacheObject = CACHE_MAP.get(key);
        if (cacheObject == null) {
            return null;
        }
        return cacheObject.getObj();
    }

    /**
     * 删除缓存
     *
     * @param key
     */
    protected static void remove(String key) {
        CACHE_MAP.remove(key);
    }

    /**
     * 版本存储
     *
     * @param key
     * @param version
     * @return
     */
    protected static String putToVersion(String key, String version) {
        return CACHE_VERSION_MAP.put(key, version);
    }

    /**
     * 版本获取
     *
     * @param key
     * @return
     */
    protected static String getVersion(String key) {
        return CACHE_VERSION_MAP.get(key);
    }

    /**
     * 角色路径存储
     *
     * @param key
     * @param actorPath
     * @return
     */
    protected static String putActorPath(String key, String actorPath) {
        return CACHE_ACTOR_PATH.put(key, actorPath);
    }

    /**
     * 角色路径获取
     *
     * @param key
     * @return
     */
    protected static String getActorPath(String key) {
        return CACHE_ACTOR_PATH.get(key);
    }

    /**
     * 获取所有缓存
     *
     * @return
     */
    public static ConcurrentHashMap<String, CacheObject> getAll() {
        return CACHE_MAP;
    }

    /**
     * 获取版本缓存
     *
     * @return
     */
    public static ConcurrentHashMap<String, String> getCacheVersionMap() {
        return CACHE_VERSION_MAP;
    }
}
