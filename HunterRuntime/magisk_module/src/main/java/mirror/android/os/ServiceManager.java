package mirror.android.os;


import com.runtime.magisk.utils.reflect.Reflection;

/**
 * Mirror class of android.os.ServiceManager
 * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/os/ServiceManager.java;l=122?q=ServiceManager
 * @author canyie
 */
public final class ServiceManager {
    public static final String NAME = "android.os.ServiceManager";
    public static final Reflection<?> REF = Reflection.on(NAME);

    public static final Reflection.MethodWrapper getIServiceManager = REF.method("getIServiceManager");
    public static final Reflection.MethodWrapper getService = REF.method("getService", String.class);
    public static final Reflection.FieldWrapper sServiceManager = REF.field("sServiceManager");

    private ServiceManager() {
        throw new InstantiationError("Mirror class " + NAME);
    }
}
