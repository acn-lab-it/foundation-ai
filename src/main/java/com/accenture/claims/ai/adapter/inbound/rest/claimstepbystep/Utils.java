package com.accenture.claims.ai.adapter.inbound.rest.claimstepbystep;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Utils {
    public static Method getMethod(Object object, String methodName) throws NoSuchMethodException {
        return object.getClass().getMethod(methodName);
    }

    public static Method getSetter(Object target, String fieldName) throws NoSuchMethodException {
        try {
            for (PropertyDescriptor pd : Introspector.getBeanInfo(target.getClass()).getPropertyDescriptors()) {
                if (pd.getName().equals(fieldName) && pd.getWriteMethod() != null) {
                    Method m = pd.getWriteMethod();
                    if (!m.canAccess(target)) m.setAccessible(true);
                    return m;
                }
            }
        } catch (Exception e) {
            // fall-through to fallback
        }
        // Fallback: cerca "setXxx" con un solo parametro
        String methodName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == 1) {
                if (!m.canAccess(target)) m.setAccessible(true);
                return m;
            }
        }
        // Se conosci il tipo del field, preferisci: target.getClass().getMethod(methodName, fieldType)
        throw new NoSuchMethodException("Setter non trovato per property '" + fieldName + "' in " + target.getClass().getName());
    }

    public static Method getGetter(Object object, String fieldName) throws NoSuchMethodException {
        String methodName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        return getMethod(object, methodName);
    }

    public static void setByFieldName(Object object, String fieldName, Object value) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Method m = getSetter(object, fieldName);
        m.invoke(object, value);
    }

    public static Object getByFieldName(Object object, String fieldName) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Method m = getGetter(object, fieldName);
        return m.invoke(object);
    }

}
