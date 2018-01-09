package cn.fww.icache.spring.reload;

/**
 * @description:
 * @author: Wen
 * @date: create in 2018/1/6 16:18
 */
public class FileDesc {

    private String fileName;

    private long lastTm;

    private String path;

    private long tm;

    public FileDesc(String fileName, long lastTm) {
        this.fileName = fileName;
        this.lastTm = lastTm;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getLastTm() {
        return lastTm;
    }

    public void setLastTm(long lastTm) {
        this.lastTm = lastTm;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getTm() {
        return tm;
    }

    public void setTm(long tm) {
        this.tm = tm;
    }
}
