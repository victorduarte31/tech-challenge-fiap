package br.com.oficina.testsupport;

import java.lang.reflect.Field;

/**
 * Utilitário para popular campos encapsulados em fixtures de teste sem expor setters em produção.
 * Usa reflection — restrito ao classpath de teste.
 */
public final class DomainTestFixtures {

    private DomainTestFixtures() {}

    public static <T> T setField(T target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
            return target;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Falha ao setar campo '" + fieldName + "' em " + target.getClass(), e);
        }
    }

    public static <T> T setId(T target, Long id) {
        return setField(target, "id", id);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " in " + type);
    }
}
