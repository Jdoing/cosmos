package example.cosmos.extension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jiangyu.666@bytedance.com
 * @date 2024/1/29
 */
public class ExtensionLoader<T> {

    private static ConcurrentHashMap<Class<?>, ExtensionLoader<?>> loaderMap = new ConcurrentHashMap<>();

    private ConcurrentHashMap<String, T> extensionMap = new ConcurrentHashMap<>();

    private final Class<T> type;

    private static final String EXTENSION_PATH = "META-INF/services/";

    public ExtensionLoader(Class<T> type) {
        this.type = type;
    }


    public T getExtension(String name) {
        return extensionMap.get(name);
    }

    public static <S> ExtensionLoader<S> load(Class<S> clazz) throws IOException {
        ExtensionLoader<?> loader = loaderMap.get(clazz);
        if (loader != null) {
            return (ExtensionLoader<S>) loader;
        }

        ExtensionLoader tmp = new ExtensionLoader(clazz);
        ExtensionLoader prev = loaderMap.putIfAbsent(clazz, tmp);
        if (prev != null) {
            tmp = prev;
        }

        synchronized (tmp) {
            tmp.loadFile(EXTENSION_PATH, ExtensionLoader.class.getClassLoader());
        }

        return tmp;
    }

    private void putExtension(String name, T extension) {
        extensionMap.put(name, extension);
    }

    private void loadFile(String dir, ClassLoader loader)
            throws IOException {
        String fileName = dir + type.getName();
        Enumeration<URL> urls;
        if (loader != null) {
            urls = loader.getResources(fileName);
        } else {
            urls = ClassLoader.getSystemResources(fileName);
        }
        if (urls != null) {
            while (urls.hasMoreElements()) {
                java.net.URL url = urls.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final int ci = line.indexOf('#');
                        if (ci >= 0) {
                            line = line.substring(0, ci);
                        }
                        line = line.trim();
                        if (line.length() > 0) {
                            try {
                                Class<?> clazz = Class.forName(line, true, loader);

                                Spi spi = clazz.getAnnotation(Spi.class);
                                if (spi == null) {
                                    continue;
                                }

                                T ext = (T) clazz.newInstance();

                                this.putExtension(spi.value(), ext);
                            } catch (LinkageError | ClassNotFoundException e) {
                                System.out.println(e);
                            } catch (ClassCastException e) {
                                System.out.println(e);
                            }
                        }
                    }
                } catch (Throwable e) {
                    System.out.println(e);
                }
            }
        }
    }

}
