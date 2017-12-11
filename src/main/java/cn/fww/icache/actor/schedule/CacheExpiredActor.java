package cn.fww.icache.actor.schedule;

import akka.actor.UntypedActor;
import cn.fww.icache.CacheObject;
import cn.fww.icache.InnerCache;
import cn.fww.icache.actor.eume.ActorCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @Description:
 * @author: Wen
 * @Date: create in 2017/11/28 14:49
 */
public class CacheExpiredActor extends UntypedActor {

    private Logger logger = LoggerFactory.getLogger(CacheExpiredActor.class);

    @Override
    public void onReceive(Object message) throws Exception {
        if (message == ActorCommand.START) {
            logger.info("检查缓存是会否会过期。");
            long now = System.currentTimeMillis();
            Map<String, CacheObject> cacheMap = InnerCache.getAll();
            for (Map.Entry<String, CacheObject> o : cacheMap.entrySet()) {
                long expireTime = o.getValue().getExpireTime();
                if (expireTime > 0 && now >= expireTime) {
                    cacheMap.remove(o.getKey());
                }
            }
        }
    }

}
