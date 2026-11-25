package net.peregrine.client.v1_21_9;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Viewmodel effects on 1.21.9-1.21.11: wraps the real collector for the held-item pass. Items are
 * submitted differently here, so for now the effects only drop the extra copies an afterimage
 * would add (ghost passes draw nothing); everything else goes straight through.
 */
public final class FxCollector implements InvocationHandler {

    private final Object d;
    private final SubmitNodeCollector root;
    private final float ghost;

    private FxCollector(Object d, SubmitNodeCollector root, float ghost) {
        this.d = d;
        this.root = root;
        this.ghost = ghost;
    }

    public static SubmitNodeCollector of(SubmitNodeCollector root, float ghost, String skin) {
        return (SubmitNodeCollector) Proxy.newProxyInstance(FxCollector.class.getClassLoader(),
                new Class<?>[] {SubmitNodeCollector.class}, new FxCollector(root, root, ghost));
    }

    public static boolean is(Object c) {
        return c != null && Proxy.isProxyClass(c.getClass()) && Proxy.getInvocationHandler(c) instanceof FxCollector;
    }

    public static SubmitNodeCollector root(Object c) {
        return ((FxCollector) Proxy.getInvocationHandler(c)).root;
    }

    @Override
    public Object invoke(Object self, java.lang.reflect.Method m, Object[] args) throws Throwable {
        String n = m.getName();
        if (n.equals("order") && args != null && args.length == 1) {
            Object inner = ((SubmitNodeCollector) root).order((Integer) args[0]);
            return Proxy.newProxyInstance(FxCollector.class.getClassLoader(), new Class<?>[] {OrderedSubmitNodeCollector.class},
                    new FxCollector(inner, root, ghost));
        }
        if (m.getDeclaringClass() == Object.class) {
            if (n.equals("equals")) {
                return self == args[0];
            }
            if (n.equals("hashCode")) {
                return System.identityHashCode(self);
            }
            return "FxCollector";
        }
        if (ghost >= 0 && n.startsWith("submit")) {
            return null;   // an afterimage pass: nothing extra here
        }
        try {
            return m.invoke(d, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
