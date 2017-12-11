package cn.fww.icache;

import java.io.Serializable;

/**
 * @Description: 缓存对象
 * @author: Wen
 * @Date: create in 2017/11/23 12:17
 */
public class CacheObject implements Serializable {

    private static final long serialVersionUID = 780809354027063160L;
    private Object obj;
    private long expireTime;

    public Object getObj() {
        return obj;
    }

    public void setObj(Object obj) {
        this.obj = obj;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }
}
