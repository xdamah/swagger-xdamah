package io.github.xdamah.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

///lets remove these extra methods and just instead enrich the Map and see

public class MyPropertyUtils {

	private static final boolean test = true;

	public static Class getListItemType(Type type) {
		Class ret = null;
		if (type != null && type instanceof ParameterizedType) {

			ParameterizedType pt = (ParameterizedType) type;
			Class rawType = (Class) pt.getRawType();
			if (List.class.isAssignableFrom(rawType)) {
				Type[] actualTypeArguments = pt.getActualTypeArguments();
				if (actualTypeArguments != null && actualTypeArguments.length == 1) {
					ret = (Class) actualTypeArguments[0];
				}
			}

		}
		return ret;
	}

	public static Class getRawType(Type type) {
		Class ret = null;
		if (type != null) {
			if (type instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) type;
				ret = (Class) pt.getRawType();

			} else if (type instanceof Class) {
				ret = (Class) type;
			}
		}

		return ret;
	}

	public static Map<String, Type> buildPropertyTypes(Class beanClass) {
		Map<String, Type> proprtyTypes = new LinkedHashMap<>();
		Field[] declaredFields = beanClass.getDeclaredFields();
		for (Field field : declaredFields) {

			proprtyTypes.put(field.getName(), field.getGenericType());
		}
		Method[] declaredMethods = beanClass.getDeclaredMethods();
		for (Method declaredMethod : declaredMethods) {
			String declaredMethodName = declaredMethod.getName();
			Parameter[] parameters = declaredMethod.getParameters();
			Type[] genericParameterTypes = declaredMethod.getGenericParameterTypes();
			Type genericReturnType = declaredMethod.getGenericReturnType();
			// logger.debug("genericReturnType="+genericReturnType+" for
			// "+declaredMethodName+"?"+(genericReturnType.getTypeName()));
			if (declaredMethodName.startsWith("get") && genericParameterTypes.length == 0
					&& !genericReturnType.getTypeName().equals("void")) {
				if (declaredMethodName.length() > 3) {
					String temp = declaredMethodName.substring(3);
					String name = String.valueOf(Character.toLowerCase(temp.charAt(0)));
					if (temp.length() > 1) {
						name = name + temp.substring(1);
					}
					proprtyTypes.put(name, genericReturnType);
				}

			} else if (declaredMethodName.startsWith("is") && genericParameterTypes.length == 0
					&& !genericReturnType.getTypeName().equals("void")) {
				if (declaredMethodName.length() > 2) {
					String temp = declaredMethodName.substring(2);
					String name = String.valueOf(Character.toLowerCase(temp.charAt(0)));
					if (temp.length() > 1) {
						name = name + temp.substring(1);
					}
					proprtyTypes.put(name, genericReturnType);
				}

			} else if (declaredMethodName.startsWith("set") && genericParameterTypes.length == 1
					&& genericReturnType.getTypeName().equals("void")) {
				if (declaredMethodName.length() > 3) {
					String temp = declaredMethodName.substring(3);
					String name = String.valueOf(Character.toLowerCase(temp.charAt(0)));
					if (temp.length() > 1) {
						name = name + temp.substring(1);
					}
					proprtyTypes.put(name, genericParameterTypes[0]);
				}
			}
		}
		if (test) {
			Set<Entry<String, Type>> entrySet = proprtyTypes.entrySet();
			for (Entry<String, Type> entry : entrySet) {
				String propertyName = entry.getKey();
				Type type = entry.getValue();
				if (type != null) {
					Class rawType = getRawType(type);
					if (rawType == null) {
						throw new RuntimeException(
								propertyName + " in " + beanClass + " getRawType() logic needs improvement");
					}
				}
			}
		}

		return proprtyTypes;
	}

}
