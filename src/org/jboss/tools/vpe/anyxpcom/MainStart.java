package org.jboss.tools.vpe.anyxpcom;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.browser.ProgressListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.mozilla.interfaces.nsIDOMWindow;

/**
 * @author Yahor Radtsevich (yradtsevich)
 */
public class MainStart {

	public MainStart() {
		// TODO Auto-generated constructor stub
	}
	
	public static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display);
		shell.setLayout(new FillLayout());
		shell.setText("WebKit");
		final Browser browser;
		System.setProperty("org.eclipse.swt.browser.XULRunnerPath", "D:/doc/prj/jboss/svn/trunk/xulrunner/plugins/org.mozilla.xulrunner.win32.win32.x86/xulrunner");
		try {
			browser = new Browser(shell, SWT.WEBKIT); // will work with NONE and WEBKIT too!
		} catch (SWTError e) {
			System.out.println("Could not instantiate Browser: " + e.getMessage());
			display.dispose();
			return;
		}
		shell.open();
		browser.setUrl("http://webkit.org");
		browser.addProgressListener(new ProgressListener() {
			@Override
			public void completed(ProgressEvent event) {
//				nsIDOMElement element = doc.createElement("DIV");
//				String name = element.getNodeName();
//				System.out.println(" name = " + name);
				
				browser.execute(
						"window.nsiArray = [window];" +
						"window.nsiId = 0;"+
						"window.convertNsi = function(param) {" +
							"if(param !== null) {"+
							   "if (typeof param === 'object' || typeof param === 'function') {"+ // in webkit typeof document.childNodes is 'function'
							     "if (param.constructor === Array) {"+
							       "var nsiParam = [];"+
							       "for ( var i = 0; i < param.length; i++) {"+
							          "nsiParam[i] = convertNsi(param[i]);"+
							       "}"+
							       "return nsiParam;"+
							     "} else {"+
							        "if (!param.hasOwnProperty('nsiId')) {"+
							            "param.nsiId = nsiArray.length;"+
							            "nsiArray[nsiArray.length] = param;"+
							        "}"+
							        "return 'nsiId=' + param.nsiId;"+
							     "}"+
							   "}" +
							 "}"+    
						   "return param;"+
						"}");
				nsIDOMWindow window = NsiUtil.createProxy(browser, 0, nsIDOMWindow.class);
				browser.execute("window.foo = {};" +
						"foo.bar = function (arg) {" +
							"return document.createElement(arg);" +
						"}");
				Foo foo = NsiUtil.convertFromNsi(
						browser.evaluate("return convertNsi(foo)"), Foo.class, browser);
				
//				long t2 = System.nanoTime();
//				for (int i = 0; i < 10000; i++) {
//					browser.evaluate("return convertNsi(foo.bar('div'))");
//				}
//				long t3 = System.nanoTime();
//				System.out.println((t3 - t2) * 1e-9);
//				
//				long t0 = System.nanoTime();
//				for (int i = 0; i < 10000; i++) {
					System.out.println(foo.bar("div"));
//					foo.bar("div");
//				}
//				long t1 = System.nanoTime();
//				System.out.println((t1 - t0) * 1e-9);

			}
			@Override
			public void changed(ProgressEvent event) {
				
			}
		});
		
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) display.sleep();
		}
		display.dispose();

	}
}
