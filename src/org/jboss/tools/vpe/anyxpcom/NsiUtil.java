package org.jboss.tools.vpe.anyxpcom;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;

import org.eclipse.swt.browser.Browser;
import org.mozilla.interfaces.nsISupports;
import org.mozilla.xpcom.XPCOMException;

/**
 * @author Yahor Radtsevich (yradtsevich)
 * @author Denis Maliarevich (dmaliarevich)
 */
public class NsiUtil {
	public static <T extends nsISupports> T queryInterface(
			nsISupports object,	Class<T> type) throws XPCOMException {
		NumeratedNsi numeratedNsi = (NumeratedNsi)object;
		return createProxy(numeratedNsi.getBrowser(), numeratedNsi.getNsiId(), type);
	}
	
	public static <T> T createProxy(Browser browser, int id, Class<T> type) {
//		System.out.println(String.format("id = %s, type = %s", id, type));
		return (T) Proxy.newProxyInstance(
				NsiUtil.class.getClassLoader(), 
				new Class[] {type, NumeratedNsi.class}, 
				new NsiProxy(browser, id));
	}
	
	/**
	 * 
	 * @param param
	 * @param returnType may be
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> T convertFromNsi(Object param, Class<T> returnType, Browser browser) {
		if (param == null) {
			return null;
		} else if (returnType == Boolean.class || returnType == boolean.class 
				|| returnType == String.class || returnType == Double.class || returnType == double.class) {
			return (T) param;
		} else if ((returnType == Long.class || returnType == long.class) && param instanceof Double) {
			Double paramDouble = (Double) param;
			return (T) ((Long) paramDouble.longValue());
		} else if ((returnType == Integer.class || returnType == int.class) && param instanceof Double) {
			Double paramDouble = (Double) param;
			return (T) ((Integer) paramDouble.intValue());
		} else if (param instanceof Object[] && returnType.isArray()) {
			Object[] paramArray = (Object[]) param;
			Class<?> paramElementType = returnType.getComponentType();
			Object resultArray = Array.newInstance(paramElementType, paramArray.length);
 
			for (int i = 0; i < paramArray.length; i++) {
				Object objFromNsi = convertFromNsi(paramArray[i], paramElementType, browser);
				Array.set(resultArray, i, objFromNsi);
			}
			
			return (T) resultArray;
		} else if (returnType.isInterface()) {
			// Create interface proxy
			int id = 0;
			if ((param instanceof String) && ((String)param).startsWith("nsiId=")) {
				id = Integer.parseInt(((String)param).substring(6));
				return NsiUtil.createProxy(browser, id, returnType);
			}
		}
		return (T) param;
	}
}
