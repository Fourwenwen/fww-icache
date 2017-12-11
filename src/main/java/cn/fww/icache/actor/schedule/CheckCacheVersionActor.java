package cn.fww.icache.actor.schedule;

import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import cn.fww.icache.InnerCache;
import cn.fww.icache.InnerCacheTemplate;
import cn.fww.icache.actor.eume.ActorCommand;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;

import javax.inject.Named;
import java.util.Map;
import java.util.Set;

/**
 * @Description:
 * @author: Wen
 * @Date: create in 2017/11/28 17:36
 */
@Named("CheckCacheVersionActor")
@Scope("prototype")
public class CheckCacheVersionActor extends UntypedActor {

    private Logger logger = LoggerFactory.getLogger(CacheExpiredActor.class);

    @Autowired
    private InnerCacheTemplate innerCacheTemplate;

    @Override
    public void onReceive(Object message) throws Exception {
        if (message == ActorCommand.START) {
            logger.info("检查缓存版本是否更新了。");
            Map<String, String> versionMap = InnerCache.getCacheVersionMap();
            Set<String> versionKey = versionMap.keySet();
            if (versionKey.size() > 0) {
                Map<Object, Object> versionRedisMap = innerCacheTemplate.getAllVersion();
                for (Map.Entry<Object, Object> o : versionRedisMap.entrySet()) {
                    String keyRedis = o.getKey().toString();
                    String version = versionMap.get(keyRedis);
                    String versionRedis = o.getValue().toString();
                    // 版本更新
                    if (StringUtils.isNotBlank(version) && !versionRedis.equals(version)) {
                        // 清理缓存，然后更新版本到缓存，通知actor执行缓存更新操作
                        innerCacheTemplate.remove(keyRedis);
                        versionMap.put(keyRedis, versionRedis);
                        notifyActor(keyRedis);
                    } else if (!innerCacheTemplate.isExistRedisCache(keyRedis)) {
                        // redis缓存失效
                        notifyActor(keyRedis);
                    }
                }
            }
        }
    }

    /**
     * 通知对应的actor进行处理
     *
     * @param keyRedis
     */
    private void notifyActor(String keyRedis) {
        String actorPath = innerCacheTemplate.getActorPathByKey(keyRedis);
        if (StringUtils.isNotBlank(actorPath)) {
            ActorSelection selection = getContext().actorSelection("../" + actorPath);
            selection.tell(ActorCommand.START, getSelf());
        }
    }

}
