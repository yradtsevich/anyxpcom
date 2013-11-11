package org.jboss.tools.vpe.anyxpcom;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.eclipse.swt.browser.Browser;

/**
 * @author Yahor Radtsevich (yradtsevich)
 */
public class NsiProxy implements InvocationHandler {
	Browser browser;
	int nsiId;
	public NsiProxy(Browser browser, int nsiId) {
		this.browser = browser;
		this.nsiId = nsiId;
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args)
			throws Throwable {
		if ("getNsiId".equals(method.getName()) && args == null) {
			return nsiId;
		}
		if ("getBrowser".equals(method.getName()) && args == null) {
			return browser;
		}
		if ("equals".equals(method.getName()) && args != null && args.length == 1) {
			Object obj = args[0];
			if (obj instanceof NumeratedNsi) {
				return this.nsiId == ((NumeratedNsi) obj).getNsiId();
			} else {
				return false;
			}
		}
		if ("hashCode".equals(method.getName()) && args == null) {
			return nsiId;
		}
		Class<?> returnType = method.getReturnType();
//		System.out.println("\n returnType = " + returnType.getCanonicalName());
		StringBuilder expression = new StringBuilder();
		expression.append("nsiArray[").append(nsiId).append("].");
		// TODO generate correct js method name: without "getN..."
		String methodName = method.getName();
//		System.out.println(" methodName = " + methodName);
		if (methodName.startsWith("get") && methodName.length() > 3 && args == null) {
			char firstLetter = Character.toLowerCase(methodName.charAt(3));
			String propertyName = firstLetter + methodName.substring(4);
			expression.append(propertyName);
		} else {
			expression.append(methodName).append('(');
			if (args != null) {
				for (int i = 0; i < args.length - 1; i++) {
					Object arg = args[i];
					appendArg(expression, arg);
					expression.append(',');
				}
				if (args.length > 0) {
					appendArg(expression, args[args.length - 1]);
				}
			}
			expression.append(')');
		}

//		System.out.println(" expression = " + expression);
		Object result;
		try {
			if (returnType == void.class) {
//			browser.execute(expression.toString());
				browser.evaluate(expression.toString());
				result = null;
			} else { 
				result = browser.evaluate("return convertNsi(" + expression.toString() + ')');
				result = AnyXPCOM.convertFromNsi(result, returnType, browser);
			}			
		} catch (Exception e) {
			result = null;// XXX
		}
		
		return result;
		
//		try {
//			returnType.cast(result);
//		} catch (ClassCastException e) {
//			System.out.println("!!! ClassCastException !!! Error in the Result TYPE !!!");
//			return null;
//		}

	}

	private void appendArg(StringBuilder expression, Object arg) {
		if (arg == null) {
			expression.append("null");
		} else if (arg instanceof Number) {
			expression.append(((Number) arg).doubleValue());
		} else if (arg instanceof Boolean) {
			expression.append(arg.toString());
		} else if (arg instanceof String) {
			String escapedArg = ((String) arg)
					.replace("\\", "\\\\")
					.replace("\n", "\\n")
					.replace("\t", "\\t")
					.replace("\r", "\\r")
					.replace("\'", "\\\'")
					.replace("\"", "\\\"");
			expression.append('\'').append(escapedArg).append('\'');
		} else if (arg instanceof NumeratedNsi) {
			expression.append("window.nsiArray[" ).append(((NumeratedNsi) arg).getNsiId()).append(']');
		} else if (arg.getClass().isArray()) {
			int length = Array.getLength(arg);
			expression.append('[');
			for (int i = 0; i < length - 1; i++) {
				appendArg(expression, Array.get(arg, i));
				expression.append(',');
			}
			if (length > 0) {
				appendArg(expression, Array.get(arg, length - 1));
			}
			expression.append(']');
		} else {
			expression.append("null"); //XXX unknown type
		}
	}
}
