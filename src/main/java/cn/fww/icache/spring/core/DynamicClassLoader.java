package cn.fww.icache.spring.core;

/**
 * @description:
 * @author: Wen
 * @date: create in 2018/1/6 17:37
 */
public class DynamicClassLoader extends ClassLoader {

    public DynamicClassLoader() {
        super(DynamicClassLoader.class.getClassLoader());
    }

    public DynamicClassLoader(ClassLoader parent) {
        super(parent);
    }

    public Class loadByte(byte[] classByte, String className) {
        return defineClass(className, classByte, 0, classByte.length);
    }

}
