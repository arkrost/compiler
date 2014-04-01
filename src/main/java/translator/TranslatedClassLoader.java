package translator;

/**
 * @author Arkady Rost
 */
public class TranslatedClassLoader extends ClassLoader {
    public final Class<?> defineClass(String name, byte[] b) {
        return defineClass(name, b, 0, b.length);
    }
}
