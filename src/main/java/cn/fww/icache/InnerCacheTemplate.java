package cn.fww.icache;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Inbox;
import akka.actor.Props;
import cn.fww.icache.actor.eume.ActorCommand;
import cn.fww.icache.actor.schedule.CacheExpiredActor;
import cn.fww.icache.spring.SpringExt;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import scala.concurrent.duration.Duration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Description: 内部缓存的一系列封装
 * @author: Wen
 * @Date: create in 2017/11/30 14:39
 */
public class InnerCacheTemplate implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(InnerCacheTemplate.class);

    private StringRedisTemplate stringRedisTemplate;

    private ActorSystem actorSystem;

    /**
     * 删除时间间隔
     */
    private int expireInterval = 30000;
    /**
     * 删除时间间隔
     */
    private int checkVersionInterval = 60000;

    private String cacheVersionNamespace;

    /**
     * 默认版本号(必须为数字)
     */
    private String defaultVersion = "1";

    @Autowired
    private SpringExt springExt;

    @Override
    public void afterPropertiesSet() throws Exception {
        loadVersionToRedis();
        startUpSchedule();
    }

    public boolean put(String key, Object value, boolean isVersion) {
        return put(key, value, -1, isVersion);
    }

    public boolean put(String key, Object value, int ttl, boolean isVersion) {
        if (isVersion) {
            String version = InnerCache.getVersion(key);
            if (StringUtils.isNotBlank(version)) {
                key += version;
            } else {
                // 落地版本信息
                stringRedisTemplate.opsForHash().put(cacheVersionNamespace, key, defaultVersion);
                // 放到缓存中去
                InnerCache.putToVersion(key, defaultVersion);
                key += defaultVersion;
            }
        }
        return InnerCache.put(key, value, ttl, isVersion);
    }

    public Object get(String key) {
        String version = InnerCache.getVersion(key);
        if (StringUtils.isNotBlank(version)) {
            key += version;
        }
        return InnerCache.get(key);
    }

    public void remove(String key) {
        String version = InnerCache.getVersion(key);
        if (StringUtils.isNotBlank(version)) {
            key += version;
        }
        InnerCache.remove(key);
    }

    /**
     * 获取redis中该项目的版本数据
     *
     * @return
     */
    public Map<Object, Object> getAllVersion() {
        return stringRedisTemplate.opsForHash().entries(cacheVersionNamespace);
    }

    /**
     * 获取该缓存的actor路径
     *
     * @param key
     * @return
     */
    public String getActorPathByKey(String key) {
        return InnerCache.getActorPath(key);
    }

    /**
     * 插入actor path信息
     *
     * @param key
     * @param path
     * @return
     */
    public String putActorPath(String key, String path) {
        return InnerCache.putActorPath(key, path);
    }

    /**
     * 查找redis是否含有数据
     *
     * @param key
     * @return
     */
    public boolean isExistRedisCache(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /**
     * 更新版本号
     *
     * @param key
     * @return
     */
    public long updateVersion(String key) {
        return stringRedisTemplate.opsForHash().increment(cacheVersionNamespace, key, 1);
    }

    /**
     * 从redis加载信息中redis
     */
    public void loadVersionToRedis() {
        logger.info("初始化工作");
        // 把版本信息放到内存中
        Map<Object, Object> cacheVersionMap = stringRedisTemplate.opsForHash().entries(cacheVersionNamespace);
        for (Map.Entry<Object, Object> o : cacheVersionMap.entrySet()) {
            InnerCache.putToVersion(o.getKey().toString(), o.getValue().toString());
        }
    }

    /**
     * 开启调度任务
     */
    private void startUpSchedule() {
        // 启动调度任务
        final ActorRef cacheExpiredActor = actorSystem.actorOf(Props.create(CacheExpiredActor.class), "cacheExpiredActor");
        final ActorRef checkCacheVersionActor = actorSystem.actorOf(springExt.props("CheckCacheVersionActor"), "checkCacheVersionActor");
        final Inbox inbox = Inbox.create(actorSystem);
        inbox.watch(cacheExpiredActor);
        inbox.watch(checkCacheVersionActor);
        actorSystem.scheduler().schedule(Duration.create(0, TimeUnit.MILLISECONDS), Duration.create(expireInterval, TimeUnit.MILLISECONDS), new Runnable() {
            @Override
            public void run() {
                inbox.send(cacheExpiredActor, ActorCommand.START);
            }
        }, actorSystem.dispatcher());
        actorSystem.scheduler().schedule(Duration.create(0, TimeUnit.MILLISECONDS), Duration.create(checkVersionInterval, TimeUnit.MILLISECONDS), new Runnable() {
            @Override
            public void run() {
                inbox.send(checkCacheVersionActor, ActorCommand.START);
            }
        }, actorSystem.dispatcher());
    }

    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void setActorSystem(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public void setExpireInterval(int expireInterval) {
        this.expireInterval = expireInterval;
    }

    public void setCheckVersionInterval(int checkVersionInterval) {
        this.checkVersionInterval = checkVersionInterval;
    }

    public void setCacheVersionNamespace(String cacheVersionNamespace) {
        this.cacheVersionNamespace = cacheVersionNamespace;
    }
}
