package box.star.js;

import org.mozilla.javascript.Context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class Android {

  private static boolean tryDalvik = true;
  private static Class dalvikClassLoader;
  private static Constructor<ClassLoader> dalvikConstructor;

  public static boolean isNotLoaded() {
    return dalvikConstructor == null;
  }

  public static boolean isPlatform() {
    if (tryDalvik) {
      try {
        dalvikClassLoader = Class.forName("dalvik.system.PathClassLoader");
        dalvikConstructor = dalvikClassLoader.getConstructor(String.class, ClassLoader.class);
      }
      catch (Exception ignored) {}
      finally { tryDalvik = false; }
    }
    return dalvikConstructor != null;
  }

  public static Context init() {
    try {
      Class rhinoHelper = Class.forName("com.faendir.rhino_android.RhinoAndroidHelper");
      Constructor<?> constructor = rhinoHelper.getConstructor();
      Object rh = constructor.newInstance();
      Method m = rhinoHelper.getDeclaredMethod("enterContext");
      return (Context) m.invoke(rh);
    }
    catch (Exception e) {throw new RuntimeException(e);}
  }

}
