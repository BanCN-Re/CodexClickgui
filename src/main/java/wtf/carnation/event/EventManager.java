package wtf.carnation.event;

import wtf.carnation.event.api.Event;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventManager {
    private final Map<Class<? extends Event>, List<Handler>> handlerMap = new ConcurrentHashMap<>();

    public void register(Object obj) {
        if (obj == null) return;

        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getAnnotation(EventTarget.class) == null) continue;
                if (method.getParameterCount() != 1) continue;
                Class<?> param = method.getParameterTypes()[0];
                if (!Event.class.isAssignableFrom(param)) continue;

                EventPriority pr = method.getAnnotation(EventPriority.class);
                int priority = pr != null ? pr.value() : 10;

                Consumer<Event> consumer = buildConsumer(method, obj, param);
                if (consumer == null) continue;

                addHandler(param.asSubclass(Event.class), new Handler(obj, method, consumer, priority));
            }
            clazz = clazz.getSuperclass();
        }
    }

    private void addHandler(Class<? extends Event> eventClass, Handler handler) {
        List<Handler> handlers = handlerMap.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>());
        for (Handler existing : handlers) {
            if (existing.owner == handler.owner && handler.method.equals(existing.method)) return;
        }
        int index = 0;
        for (Handler existing : handlers) {
            if (existing.priority <= handler.priority) index++;
            else break;
        }
        handlers.add(index, handler);
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Event> buildConsumer(Method method, Object owner, Class<?> param) {
        method.setAccessible(true);
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup());
            MethodHandle handle = lookup.unreflect(method);
            CallSite site = LambdaMetafactory.metafactory(
                    lookup,
                    "accept",
                    MethodType.methodType(Consumer.class, method.getDeclaringClass()),
                    MethodType.methodType(void.class, Object.class),
                    handle,
                    MethodType.methodType(void.class, param)
            );
            return (Consumer<Event>) site.getTarget().invoke(owner);
        } catch (Throwable lmf) {
            // Fallback: a bound MethodHandle, still much faster than plain reflection.
            try {
                MethodHandle bound = MethodHandles.lookup().unreflect(method)
                        .bindTo(owner)
                        .asType(MethodType.methodType(void.class, Event.class));
                return e -> {
                    try { bound.invokeExact(e); } catch (Throwable t) { t.printStackTrace(); }
                };
            } catch (IllegalAccessException ex) {
                ex.printStackTrace();
                return null;
            }
        }
    }

    public void unregister(Object obj) {
        if (obj == null) return;
        for (List<Handler> handlers : handlerMap.values()) {
            handlers.removeIf(h -> h.owner == obj);
        }
        handlerMap.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public <E extends Event> E call(E event) {
        List<Handler> handlers = handlerMap.get(event.getClass());
        if (handlers != null) {
            for (Handler h : handlers) {
                try {
                    h.consumer.accept(event);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
        return event;
    }

    private record Handler(Object owner, Method method, Consumer<Event> consumer, int priority) {
    }
}
