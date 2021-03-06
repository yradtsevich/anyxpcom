package org.jboss.tools.vpe.anyxpcom;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mozilla.interfaces.nsIComponentManager;
import org.mozilla.interfaces.nsIComponentRegistrar;
import org.mozilla.interfaces.nsIDOMNode;
import org.mozilla.interfaces.nsIServiceManager;
import org.mozilla.interfaces.nsISimpleEnumerator;
import org.mozilla.interfaces.nsISupports;
import org.mozilla.interfaces.nsISupportsCString;
import org.mozilla.xpcom.Mozilla;
import org.mozilla.xpcom.XPCOMException;

/**
 * @author Sergey Vasilyev (svasilyev@exadel.com): initial creation.
 * @author Yahor Radtsevich (yradtsevich): methods {@link #queryInterface},
 * {@link #printAllContractIDs(Class)},
 * {@link #printSupportedInterfaces(nsISupports, boolean)}
 * and related stuff (JBIDE-6393).
 */
public final class XPCOM {
	private XPCOM() {}
	/*
	 * Contract IDs
	 */
	public static final String NS_DRAGSERVICE_CONTRACTID = "@mozilla.org/widget/dragservice;1"; //$NON-NLS-1$
	public static final String NS_TRANSFERABLE_CONTRACTID = "@mozilla.org/widget/transferable;1"; //$NON-NLS-1$
	public static final String NS_WINDOWWATCHER_CONTRACTID = "@mozilla.org/embedcomp/window-watcher;1"; //$NON-NLS-1$
	public static final String NS_PREFSERVICE_CONTRACTID = "@mozilla.org/preferences-service;1"; //$NON-NLS-1$
	public static final String NS_SUPPORTSSTRING_CONTRACTID = "@mozilla.org/supports-string;1"; //$NON-NLS-1$
	public static final String NS_SUPPORTSARRAY_CONTRACTID = "@mozilla.org/supports-array;1"; //$NON-NLS-1$
	public static final String NS_ACCESSIBILITYSERVICE_CONTRACTID = "@mozilla.org/accessibilityService;1"; //$NON-NLS-1$
	
	public static final String IN_FLASHER_CONTRACTID = "@mozilla.org/inspector/flasher;1"; //$NON-NLS-1$
	/**
	 * Editing Session Contract ID
	 * see http://www.xulplanet.com/references/xpcomref/ifaces/nsIEditingSession.html
	 */
	public static final String NS_EDITINGSESSION_CONTRACTID="@mozilla.org/editor/editingsession;1"; //$NON-NLS-1$
	
	public static final String NS_IWEBBROWSER_CID = "F1EAC761-87E9-11d3-AF80-00A024FFC08C"; //$NON-NLS-1$
	public static final String NS_IAPPSHELL_CID = "2d96b3df-c051-11d1-a827-0040959a28c9"; //$NON-NLS-1$
	
	public static final long NS_ERROR_NO_INTERFACE =  0x80004002L;
	
	/**Stores all interfaces which extend nsISupports. */
	/* Lazy initialization of interfacesList is used to avoid loading
	 * of all these classes (>1000) by the class loader. */
	private static List<Class<? extends nsISupports>> interfacesList = null;
	
	private static Map<Class<? extends nsISupports>, String> interfaceIdByType
			= new HashMap<Class<? extends nsISupports>, String>();

	/**
	 * Queries given interface-<code>type</code> from {@code object}.
	 * <P>
	 * This method is intended to simplify long boilerplate XPCOM
	 * interfaces casting
	 * <pre>(nsIDOMNode) object.queryInterface(nsIDOMNode.NS_IDOMNODE_IID</pre>
	 * by a simpler call
	 * <pre>queryInterface(object, nsIDOMNode.class)</pre>
	 *  
	 * It is recommended to include the method using static import:
	 * <pre>import static org.jboss.tools.vpe.xulrunner.util.XPCOM.queryInterface</pre>
	 * 
	 * @throws XPCOMException when the {@code object} does not support
	 * the {@code type}.
	 * to 
	  
	 * @author Yahor Radtsevich (yradtsevich)
	 */
	public static <T extends nsISupports> T queryInterface(
			nsISupports object,	Class<T> type) throws XPCOMException {
		String interfaceId = getInterfaceId(type);
		return (T) object.queryInterface(interfaceId);
	}
	
	/**
	 * Returns XPCOM ID for the given {@code type}
	 * <P>
	 * Example:{@code getInterfaceId(nsIDOMNode.class)} will return value of
	 * {@link nsIDOMNode#NS_IDOMNODE_IID}.
	 *  
	 * @param type interface extending {@link nsISupports}
	 * 
	 * @author Yahor Radtsevich (yradtsevich)
	 */
	public static <T extends nsISupports> String getInterfaceId(Class<T> type) {
		String interfaceId = interfaceIdByType.get(type);
		if (interfaceId == null) {
			String interfaceIdFieldName = getInterfaceIdFieldName(type);
				try {
					interfaceId = (String) type.getField(interfaceIdFieldName).get(null);
				} catch (NoSuchFieldException e) {
					throw new RuntimeException(e);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			interfaceIdByType.put(type, interfaceId);
		}
		return interfaceId;
	}

	/**
	 * Returns field name which contains XPCOM ID for the given
	 * interface-{@code type}.
	 * <P>
	 * Examples:
	 *<pre>   getInterfaceIdFieldName(nsIDOMNode.class)="NS_IDOMNODE_IID"
	 *   getInterfaceIdFieldName(jsdIScript.class)="JSDISCRIPT_IID"</pre>
	 * 
	 * @param type interface extending {@link nsISupports}
	 * 
	 * @author Yahor Radtsevich (yradtsevich)
	 */
	private static <T extends nsISupports> String getInterfaceIdFieldName(Class<T> type) {
		String typeName = type.getSimpleName();

		String interfaceIdFieldName;
		if (typeName.startsWith("ns")) { //$NON-NLS-1$
			// e.g. "nsIDOMNode" becomes "NS_IDOMNODE"
			interfaceIdFieldName = "NS_" + typeName.substring(2).toUpperCase(); //$NON-NLS-1$
		} else {
			// e.g. "jsdIScript" becomes "JSDISCRIPT"
			interfaceIdFieldName = typeName.toUpperCase();
		}
		interfaceIdFieldName = interfaceIdFieldName + "_IID"; //$NON-NLS-1$
		return interfaceIdFieldName;
	}
	
	 /**
	  * Returns all interfaces supported by the {@code object}.
	  * 
	  * @deprecated This method tries to query every known XPCOM interface
	  * from given object and it performs very slow (seconds).
	  * For debug/test purposes only. Do not use it in the production code.
	  */
	public static List<Class<? extends nsISupports>> getSupportedInterfaces(
			nsISupports object) {
		List<Class<? extends nsISupports>> supportedInterfaces =
				new ArrayList<Class<? extends nsISupports>>();
		for (Class<? extends nsISupports> type : getInterfacesList()) {
			try {
				// try to get interface
				queryInterface(object, type);
				
				// if no error is thrown, than the interface is supported
				supportedInterfaces.add(type);
			} catch (XPCOMException e) {
				// it's OK
			}
		}
		
		return supportedInterfaces;
	}

	 /**
	  * Prints all interfaces supported by the {@code object} to the
	  * {@code System.out}.
	  * 
	  * @deprecated This method tries to query every known XPCOM interface
	  * from given object and it performs very slow (seconds).
	  * For debug purposes only. Do not use it in the production code.
	  */
	public static void printSupportedInterfaces(nsISupports object, boolean printMethods) {
		for (Class<? extends nsISupports> type : getSupportedInterfaces(object)) {
			System.out.println(type.getSimpleName());
			if (printMethods) {
				for (Method method : type.getMethods()) {
					System.out.println('\t' + method.getName());
				}
			}
		}
	}
	
	/**
	 * Prints all XPCOM interface ID for the given type
	 * to the {@code System.out}.
	 * 
	 * @deprecated For debug purposes only.
	 */
	public static void printAllContractIDs(Class<? extends nsISupports> type) {
		nsIComponentManager componentManager = Mozilla.getInstance().getComponentManager();
		nsIServiceManager serviceManager = Mozilla.getInstance().getServiceManager();
		
		nsISimpleEnumerator contractIDsEnumerator
				= queryInterface(componentManager, nsIComponentRegistrar.class)
						.enumerateContractIDs();

		List<String> contractIDs = new ArrayList<String>();
		while(contractIDsEnumerator.hasMoreElements()) {
			contractIDs.add( queryInterface(contractIDsEnumerator.getNext(),
					nsISupportsCString.class).getData() );
		}
		java.util.Collections.sort(contractIDs);
		for (String contractID : contractIDs) {
			boolean hasComponent = false;
			boolean hasService = false;
			
			try {
				nsISupports component = componentManager.createInstanceByContractID(
						contractID, null, nsISupports.NS_ISUPPORTS_IID);
				queryInterface(component, type);
				hasService = true;
			} catch (Throwable e) {
				// it's OK
			}
			
			try {
				nsISupports service = 
					serviceManager.getServiceByContractID(contractID, nsISupports.NS_ISUPPORTS_IID);//createInstanceByContractID(
								//contractID, null, nsISupports.NS_ISUPPORTS_IID);
				queryInterface(service, type);
				hasService = true;
			} catch (Throwable e) {
				// it's OK
			}
			
			if (hasComponent || hasService) {
				System.out.print(String.format("%s (hasComponent = %s, hasService = %s)",
						contractID, hasComponent, hasService));
			}
		}
	}

	/**
	 * Returns all XPCOM interfaces which extend nsISupports.
	 * 
	 * @deprecated This method loads tons of classes.
	 * For debug/test purposes only. Do not use it in the production code.
	 */
	public static List<Class<? extends nsISupports>> getInterfacesList() {
		if (interfacesList == null) {
			interfacesList = new ArrayList<Class<? extends nsISupports>>();
			
			interfacesList.add(org.mozilla.interfaces.extIApplication.class);
			interfacesList.add(org.mozilla.interfaces.extIConsole.class);
			interfacesList.add(org.mozilla.interfaces.extIEventItem.class);
			interfacesList.add(org.mozilla.interfaces.extIEventListener.class);
			interfacesList.add(org.mozilla.interfaces.extIEvents.class);
			interfacesList.add(org.mozilla.interfaces.extIExtension.class);
			interfacesList.add(org.mozilla.interfaces.extIExtensions.class);
			interfacesList.add(org.mozilla.interfaces.extIPreference.class);
			interfacesList.add(org.mozilla.interfaces.extIPreferenceBranch.class);
			interfacesList.add(org.mozilla.interfaces.extISessionStorage.class);
//			interfacesList.add(org.mozilla.interfaces.gfxIFormats.class);
//			interfacesList.add(org.mozilla.interfaces.gfxIImageFrame.class);
			interfacesList.add(org.mozilla.interfaces.IDispatch.class);
			interfacesList.add(org.mozilla.interfaces.imgICache.class);
			interfacesList.add(org.mozilla.interfaces.imgIContainer.class);
			interfacesList.add(org.mozilla.interfaces.imgIContainerObserver.class);
			interfacesList.add(org.mozilla.interfaces.imgIDecoder.class);
			interfacesList.add(org.mozilla.interfaces.imgIDecoderObserver.class);
			interfacesList.add(org.mozilla.interfaces.imgIEncoder.class);
			interfacesList.add(org.mozilla.interfaces.imgILoad.class);
			interfacesList.add(org.mozilla.interfaces.imgILoader.class);
			interfacesList.add(org.mozilla.interfaces.imgIRequest.class);
			interfacesList.add(org.mozilla.interfaces.imgITools.class);
			interfacesList.add(org.mozilla.interfaces.inICSSValueSearch.class);
			interfacesList.add(org.mozilla.interfaces.inIDeepTreeWalker.class);
			interfacesList.add(org.mozilla.interfaces.inIDOMUtils.class);
			interfacesList.add(org.mozilla.interfaces.inIDOMView.class);
			interfacesList.add(org.mozilla.interfaces.inIFlasher.class);
			interfacesList.add(org.mozilla.interfaces.inISearchObserver.class);
			interfacesList.add(org.mozilla.interfaces.inISearchProcess.class);
			interfacesList.add(org.mozilla.interfaces.jsdICallHook.class);
			interfacesList.add(org.mozilla.interfaces.jsdIContext.class);
			interfacesList.add(org.mozilla.interfaces.jsdIContextEnumerator.class);
			interfacesList.add(org.mozilla.interfaces.jsdIDebuggerService.class);
			interfacesList.add(org.mozilla.interfaces.jsdIEphemeral.class);
			interfacesList.add(org.mozilla.interfaces.jsdIErrorHook.class);
			interfacesList.add(org.mozilla.interfaces.jsdIExecutionHook.class);
			interfacesList.add(org.mozilla.interfaces.jsdIFilter.class);
			interfacesList.add(org.mozilla.interfaces.jsdIFilterEnumerator.class);
			interfacesList.add(org.mozilla.interfaces.jsdINestCallback.class);
			interfacesList.add(org.mozilla.interfaces.jsdIObject.class);
			interfacesList.add(org.mozilla.interfaces.jsdIProperty.class);
			interfacesList.add(org.mozilla.interfaces.jsdIScript.class);
			interfacesList.add(org.mozilla.interfaces.jsdIScriptEnumerator.class);
			interfacesList.add(org.mozilla.interfaces.jsdIScriptHook.class);
			interfacesList.add(org.mozilla.interfaces.jsdIStackFrame.class);
			interfacesList.add(org.mozilla.interfaces.jsdIValue.class);
			interfacesList.add(org.mozilla.interfaces.mozIJSSubScriptLoader.class);
			interfacesList.add(org.mozilla.interfaces.mozIPersonalDictionary.class);
			interfacesList.add(org.mozilla.interfaces.mozISpellCheckingEngine.class);
			interfacesList.add(org.mozilla.interfaces.mozISpellI18NManager.class);
			interfacesList.add(org.mozilla.interfaces.mozISpellI18NUtil.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageAggregateFunction.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageConnection.class);
//			interfacesList.add(org.mozilla.interfaces.mozIStorageDataSet.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageError.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageFunction.class);
			interfacesList.add(org.mozilla.interfaces.mozIStoragePendingStatement.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageProgressHandler.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageResultSet.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageRow.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageService.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageStatement.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageStatementCallback.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageStatementParams.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageStatementRow.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageStatementWrapper.class);
			interfacesList.add(org.mozilla.interfaces.mozIStorageValueArray.class);
			interfacesList.add(org.mozilla.interfaces.mozITXTToHTMLConv.class);
			interfacesList.add(org.mozilla.interfaces.nsIAboutModule.class);
			interfacesList.add(org.mozilla.interfaces.nsIAbstractWorker.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessible.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleCaretMoveEvent.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleCoordinateType.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleDocument.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleEditableText.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleEvent.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleHyperLink.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleHyperText.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleImage.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleProvider.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleRelation.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleRetrieval.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleRole.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleScrollType.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleSelectable.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleStateChangeEvent.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleStates.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleTable.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleTableChangeEvent.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleText.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleTextChangeEvent.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessibleValue.class);
//			interfacesList.add(org.mozilla.interfaces.nsIAccessNode.class);
			interfacesList.add(org.mozilla.interfaces.nsIAddonInstallListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIAddonRepository.class);
			interfacesList.add(org.mozilla.interfaces.nsIAddonSearchResult.class);
			interfacesList.add(org.mozilla.interfaces.nsIAddonSearchResultsCallback.class);
			interfacesList.add(org.mozilla.interfaces.nsIAddonUpdateCheckListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIAlertsService.class);
			interfacesList.add(org.mozilla.interfaces.nsIAnnotationObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIAnnotationService.class);
			interfacesList.add(org.mozilla.interfaces.nsIApplicationCache.class);
			interfacesList.add(org.mozilla.interfaces.nsIApplicationCacheChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIApplicationCacheContainer.class);
			interfacesList.add(org.mozilla.interfaces.nsIApplicationCacheNamespace.class);
			interfacesList.add(org.mozilla.interfaces.nsIApplicationCacheService.class);
			interfacesList.add(org.mozilla.interfaces.nsIApplicationUpdateService.class);
			interfacesList.add(org.mozilla.interfaces.nsIAppShell.class);
			interfacesList.add(org.mozilla.interfaces.nsIAppShellService.class);
			interfacesList.add(org.mozilla.interfaces.nsIAppStartup.class);
			interfacesList.add(org.mozilla.interfaces.nsIAppStartup2.class);
			interfacesList.add(org.mozilla.interfaces.nsIArray.class);
			interfacesList.add(org.mozilla.interfaces.nsIASN1Object.class);
			interfacesList.add(org.mozilla.interfaces.nsIASN1PrintableItem.class);
			interfacesList.add(org.mozilla.interfaces.nsIASN1Sequence.class);
			interfacesList.add(org.mozilla.interfaces.nsIASN1Tree.class);
			interfacesList.add(org.mozilla.interfaces.nsIAssociatedContentSecurity.class);
			interfacesList.add(org.mozilla.interfaces.nsIAsyncInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIAsyncOutputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIAsyncStreamCopier.class);
			interfacesList.add(org.mozilla.interfaces.nsIAtom.class);
			interfacesList.add(org.mozilla.interfaces.nsIAtomService.class);
			interfacesList.add(org.mozilla.interfaces.nsIAuthInformation.class);
			interfacesList.add(org.mozilla.interfaces.nsIAuthPrompt.class);
			interfacesList.add(org.mozilla.interfaces.nsIAuthPrompt2.class);
			interfacesList.add(org.mozilla.interfaces.nsIAuthPromptAdapterFactory.class);
			interfacesList.add(org.mozilla.interfaces.nsIAuthPromptCallback.class);
			interfacesList.add(org.mozilla.interfaces.nsIAuthPromptProvider.class);
			interfacesList.add(org.mozilla.interfaces.nsIAuthPromptWrapper.class);
			interfacesList.add(org.mozilla.interfaces.nsIAutoCompleteController.class);
			interfacesList.add(org.mozilla.interfaces.nsIAutoCompleteInput.class);
			interfacesList.add(org.mozilla.interfaces.nsIAutoCompleteObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIAutoCompletePopup.class);
			interfacesList.add(org.mozilla.interfaces.nsIAutoCompleteResult.class);
			interfacesList.add(org.mozilla.interfaces.nsIAutoCompleteSearch.class);
			interfacesList.add(org.mozilla.interfaces.nsIAutoCompleteSimpleResult.class);
			interfacesList.add(org.mozilla.interfaces.nsIAutoCompleteSimpleResultListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIBadCertListener2.class);
			interfacesList.add(org.mozilla.interfaces.nsIBaseWindow.class);
			interfacesList.add(org.mozilla.interfaces.nsIBidiKeyboard.class);
			interfacesList.add(org.mozilla.interfaces.nsIBidirectionalIterator.class);
			interfacesList.add(org.mozilla.interfaces.nsIBinaryInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIBinaryOutputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIBlocklistService.class);
			interfacesList.add(org.mozilla.interfaces.nsIBoxObject.class);
			interfacesList.add(org.mozilla.interfaces.nsIBrowserBoxObject.class);
			interfacesList.add(org.mozilla.interfaces.nsIBrowserDOMWindow.class);
			interfacesList.add(org.mozilla.interfaces.nsIBrowserHistory.class);
//			interfacesList.add(org.mozilla.interfaces.nsIBrowserHistory_MOZILLA_1_9_1_ADDITIONS.class);
			interfacesList.add(org.mozilla.interfaces.nsIBrowserInstance.class);
			interfacesList.add(org.mozilla.interfaces.nsIBrowserSearchService.class);
			interfacesList.add(org.mozilla.interfaces.nsIBufferedInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIBufferedOutputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIByteRangeRequest.class);
//			interfacesList.add(org.mozilla.interfaces.nsICache.class);
			interfacesList.add(org.mozilla.interfaces.nsICacheDeviceInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsICacheEntryDescriptor.class);
			interfacesList.add(org.mozilla.interfaces.nsICacheEntryInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsICacheListener.class);
			interfacesList.add(org.mozilla.interfaces.nsICacheMetaDataVisitor.class);
			interfacesList.add(org.mozilla.interfaces.nsICacheService.class);
			interfacesList.add(org.mozilla.interfaces.nsICacheSession.class);
			interfacesList.add(org.mozilla.interfaces.nsICacheVisitor.class);
			interfacesList.add(org.mozilla.interfaces.nsICachingChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsICancelable.class);
			interfacesList.add(org.mozilla.interfaces.nsICategoryManager.class);
			interfacesList.add(org.mozilla.interfaces.nsICertificateDialogs.class);
			interfacesList.add(org.mozilla.interfaces.nsICertOverrideService.class);
			interfacesList.add(org.mozilla.interfaces.nsICertPickDialogs.class);
			interfacesList.add(org.mozilla.interfaces.nsICertTree.class);
			interfacesList.add(org.mozilla.interfaces.nsICertTreeItem.class);
			interfacesList.add(org.mozilla.interfaces.nsICertVerificationListener.class);
			interfacesList.add(org.mozilla.interfaces.nsICertVerificationResult.class);
			interfacesList.add(org.mozilla.interfaces.nsIChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIChannelClassifier.class);
			interfacesList.add(org.mozilla.interfaces.nsIChannelEventSink.class);
			interfacesList.add(org.mozilla.interfaces.nsICharsetConverterManager.class);
			interfacesList.add(org.mozilla.interfaces.nsICharsetResolver.class);
			interfacesList.add(org.mozilla.interfaces.nsIChromeRegistry.class);
			interfacesList.add(org.mozilla.interfaces.nsICipherInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsICipherInfoService.class);
			interfacesList.add(org.mozilla.interfaces.nsICiter.class);
			interfacesList.add(org.mozilla.interfaces.nsIClassInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIClientAuthDialogs.class);
			interfacesList.add(org.mozilla.interfaces.nsIClientAuthUserDecision.class);
			interfacesList.add(org.mozilla.interfaces.nsIClipboard.class);
			interfacesList.add(org.mozilla.interfaces.nsIClipboardCommands.class);
			interfacesList.add(org.mozilla.interfaces.nsIClipboardDragDropHookList.class);
			interfacesList.add(org.mozilla.interfaces.nsIClipboardDragDropHooks.class);
			interfacesList.add(org.mozilla.interfaces.nsIClipboardHelper.class);
			interfacesList.add(org.mozilla.interfaces.nsIClipboardOwner.class);
			interfacesList.add(org.mozilla.interfaces.nsICMSMessageErrors.class);
			interfacesList.add(org.mozilla.interfaces.nsICMSSecureMessage.class);
			interfacesList.add(org.mozilla.interfaces.nsICollation.class);
			interfacesList.add(org.mozilla.interfaces.nsICollationFactory.class);
			interfacesList.add(org.mozilla.interfaces.nsICollection.class);
			interfacesList.add(org.mozilla.interfaces.nsICommandController.class);
			interfacesList.add(org.mozilla.interfaces.nsICommandHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsICommandHandlerInit.class);
			interfacesList.add(org.mozilla.interfaces.nsICommandLine.class);
			interfacesList.add(org.mozilla.interfaces.nsICommandLineHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsICommandLineValidator.class);
			interfacesList.add(org.mozilla.interfaces.nsICommandManager.class);
			interfacesList.add(org.mozilla.interfaces.nsICommandParams.class);
			interfacesList.add(org.mozilla.interfaces.nsIComponentManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIComponentManagerObsolete.class);
			interfacesList.add(org.mozilla.interfaces.nsIComponentRegistrar.class);
			interfacesList.add(org.mozilla.interfaces.nsIConsoleListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIConsoleMessage.class);
			interfacesList.add(org.mozilla.interfaces.nsIConsoleService.class);
			interfacesList.add(org.mozilla.interfaces.nsIContainerBoxObject.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentDispatchChooser.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentFilter.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentPolicy.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentPrefObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentPrefService.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentSniffer.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentURIGrouper.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentViewer.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentViewerContainer.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentViewerEdit.class);
			interfacesList.add(org.mozilla.interfaces.nsIContentViewerFile.class);
			interfacesList.add(org.mozilla.interfaces.nsIContextMenuInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIContextMenuListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIContextMenuListener2.class);
			interfacesList.add(org.mozilla.interfaces.nsIController.class);
			interfacesList.add(org.mozilla.interfaces.nsIControllerCommand.class);
			interfacesList.add(org.mozilla.interfaces.nsIControllerCommandGroup.class);
			interfacesList.add(org.mozilla.interfaces.nsIControllerCommandTable.class);
			interfacesList.add(org.mozilla.interfaces.nsIControllerContext.class);
			interfacesList.add(org.mozilla.interfaces.nsIControllers.class);
			interfacesList.add(org.mozilla.interfaces.nsIConverterInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIConverterOutputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsICookie.class);
			interfacesList.add(org.mozilla.interfaces.nsICookie2.class);
			interfacesList.add(org.mozilla.interfaces.nsICookieAcceptDialog.class);
			interfacesList.add(org.mozilla.interfaces.nsICookieManager.class);
			interfacesList.add(org.mozilla.interfaces.nsICookieManager2.class);
			interfacesList.add(org.mozilla.interfaces.nsICookiePermission.class);
			interfacesList.add(org.mozilla.interfaces.nsICookiePromptService.class);
			interfacesList.add(org.mozilla.interfaces.nsICookieService.class);
			interfacesList.add(org.mozilla.interfaces.nsICrashReporter.class);
			interfacesList.add(org.mozilla.interfaces.nsICRLInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsICRLManager.class);
			interfacesList.add(org.mozilla.interfaces.nsICryptoFIPSInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsICryptoHash.class);
			interfacesList.add(org.mozilla.interfaces.nsICryptoHMAC.class);
			interfacesList.add(org.mozilla.interfaces.nsICurrentCharsetListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIDataSignatureVerifier.class);
			interfacesList.add(org.mozilla.interfaces.nsIDataType.class);
			interfacesList.add(org.mozilla.interfaces.nsIDBusHandlerApp.class);
			interfacesList.add(org.mozilla.interfaces.nsIDebug.class);
			interfacesList.add(org.mozilla.interfaces.nsIDialogParamBlock.class);
			interfacesList.add(org.mozilla.interfaces.nsIDirectoryEnumerator.class);
			interfacesList.add(org.mozilla.interfaces.nsIDirectoryService.class);
			interfacesList.add(org.mozilla.interfaces.nsIDirectoryServiceProvider.class);
			interfacesList.add(org.mozilla.interfaces.nsIDirectoryServiceProvider2.class);
			interfacesList.add(org.mozilla.interfaces.nsIDirIndex.class);
			interfacesList.add(org.mozilla.interfaces.nsIDirIndexListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIDirIndexParser.class);
			interfacesList.add(org.mozilla.interfaces.nsIDNSListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIDNSRecord.class);
			interfacesList.add(org.mozilla.interfaces.nsIDNSService.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocCharset.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocShell.class);
//			interfacesList.add(org.mozilla.interfaces.nsIDocShell_MOZILLA_1_9_1.class);
//			interfacesList.add(org.mozilla.interfaces.nsIDocShell_MOZILLA_1_9_1_dns.class);
//			interfacesList.add(org.mozilla.interfaces.nsIDocShell_MOZILLA_1_9_1_SessionStorage.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocShellHistory.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocShellLoadInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocShellTreeItem.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocShellTreeNode.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocShellTreeOwner.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocumentCharsetInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocumentEncoder.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocumentEncoderNodeFixup.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocumentLoader.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocumentLoaderFactory.class);
			interfacesList.add(org.mozilla.interfaces.nsIDocumentStateListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOM3Attr.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOM3Document.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOM3DocumentEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOM3EventTarget.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOM3Node.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOM3Text.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOM3TypeInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMAbstractView.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMAttr.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMBarProp.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMBeforeUnloadEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCanvasGradient.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCanvasPattern.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCanvasRenderingContext2D.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCDATASection.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCharacterData.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMChromeWindow.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMClientInformation.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMClientRect.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMClientRectList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCommandEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMComment.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCounter.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCRMFObject.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCrypto.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCryptoDialogs.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSS2Properties.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSCharsetRule.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSFontFaceRule.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSImportRule.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSMediaRule.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSMozDocumentRule.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSPageRule.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSPrimitiveValue.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSRule.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSRuleList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSStyleDeclaration.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSStyleRule.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSStyleSheet.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSUnknownRule.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSValue.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMCSSValueList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDataContainerEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDataTransfer.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDocument.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDocumentCSS.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDocumentEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDocumentFragment.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDocumentRange.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDocumentStyle.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDocumentTraversal.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDocumentType.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDocumentView.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDocumentXBL.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDOMConfiguration.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDOMConstructor.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDOMException.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDOMImplementation.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDOMImplementationLS.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDOMStringList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMDragEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMElementCSSInlineStyle.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMEntity.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMEntityReference.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMEventGroup.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMEventListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMEventTarget.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMFile.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMFileException.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMFileList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMGeoGeolocation.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMGeoPosition.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMGeoPositionCallback.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMGeoPositionCoords.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMGeoPositionError.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMGeoPositionErrorCallback.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMGeoPositionOptions.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMGetSVGDocument.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHistory.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLAnchorElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLAppletElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLAreaElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLAudioElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLBaseElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLBaseFontElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLBodyElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLBRElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLButtonElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLByteRanges.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLCanvasElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLCollection.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLDirectoryElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLDivElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLDListElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLDocument.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLEmbedElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLFieldSetElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLFontElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLFormElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLFrameElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLFrameSetElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLHeadElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLHeadingElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLHRElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLHtmlElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLIFrameElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLImageElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLInputElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLIsIndexElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLLabelElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLLegendElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLLIElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLLinkElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLMapElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLMediaElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLMediaError.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLMenuElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLMetaElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLModElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLObjectElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLOListElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLOptGroupElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLOptionElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLOptionsCollection.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLParagraphElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLParamElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLPreElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLQuoteElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLScriptElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLSelectElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLSourceElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLStyleElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLTableCaptionElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLTableCellElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLTableColElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLTableElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLTableRowElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLTableSectionElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLTextAreaElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLTimeRanges.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLTitleElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLUListElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLVideoElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMHTMLVoidCallback.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMJSNavigator.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMJSWindow.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMKeyEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLinkStyle.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLoadStatus.class);
//			interfacesList.add(org.mozilla.interfaces.nsIDOMLoadStatusEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLocation.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLSException.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLSInput.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLSLoadEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLSOutput.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLSParser.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLSParserFilter.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLSProgressEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLSResourceResolver.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLSSerializer.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMLSSerializerFilter.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMMediaList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMMessageEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMMimeType.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMMimeTypeArray.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMModalContentWindow.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMMouseEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMMouseScrollEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMMutationEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNamedNodeMap.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNameList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNavigator.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNavigatorGeolocation.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNode.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNodeFilter.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNodeIterator.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNodeList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNodeSelector.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNotation.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNotifyPaintEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSCSS2Properties.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSDataTransfer.class);
//			interfacesList.add(org.mozilla.interfaces.nsIDOMNSDataTransfer_MOZILLA_1_9_1.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSDocument.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSDocumentStyle.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSEditableElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSEventTarget.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSFeatureFactory.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLAnchorElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLAnchorElement2.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLAreaElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLAreaElement2.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLButtonElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLDocument.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLFormControlList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLFormElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLFrameElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLHRElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLImageElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLInputElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLOptionCollection.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLOptionElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLSelectElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSHTMLTextAreaElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSRange.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSRGBAColor.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSUIEvent.class);
//			interfacesList.add(org.mozilla.interfaces.nsIDOMNSXBLFormControl.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMNSXPathExpression.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMOfflineResourceList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMPageTransitionEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMParser.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMParserJS.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMPkcs11.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMPlugin.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMPluginArray.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMPopupBlockedEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMProcessingInstruction.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMProgressEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMRange.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMRangeException.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMRect.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMRGBColor.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMScreen.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSerializer.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSimpleGestureEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSmartCardEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMStorage.class);
//			interfacesList.add(org.mozilla.interfaces.nsIDOMStorage2.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMStorageEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMStorageItem.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMStorageList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMStorageManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMStorageWindow.class);
//			interfacesList.add(org.mozilla.interfaces.nsIDOMStorageWindow_1_9_1.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMStyleSheet.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMStyleSheetList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAngle.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedAngle.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedBoolean.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedEnumeration.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedInteger.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedLength.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedLengthList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedNumber.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedNumberList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedPathData.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedPoints.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedPreserveAspectRatio.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedRect.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedString.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGAnimatedTransformList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGCircleElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGClipPathElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGComponentTransferFunctionElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGDefsElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGDescElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGDocument.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGEllipseElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGException.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEBlendElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEColorMatrixElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEComponentTransferElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFECompositeElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEConvolveMatrixElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEDiffuseLightingElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEDisplacementMapElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEDistantLightElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEFloodElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEFuncAElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEFuncBElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEFuncGElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEFuncRElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEGaussianBlurElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEImageElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEMergeElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEMergeNodeElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEMorphologyElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEOffsetElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFEPointLightElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFESpecularLightingElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFESpotLightElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFETileElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFETurbulenceElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFilterElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFilterPrimitiveStandardAttributes.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGFitToViewBox.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGForeignObjectElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGGElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGGradientElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGImageElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGLength.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGLengthList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGLinearGradientElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGLineElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGLocatable.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGMarkerElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGMaskElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGMatrix.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGMetadataElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGNumber.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGNumberList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSeg.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegArcAbs.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegArcRel.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegClosePath.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegCurvetoCubicAbs.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegCurvetoCubicRel.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegCurvetoCubicSmoothAbs.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegCurvetoCubicSmoothRel.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegCurvetoQuadraticAbs.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegCurvetoQuadraticRel.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegCurvetoQuadraticSmoothAbs.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegCurvetoQuadraticSmoothRel.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegLinetoAbs.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegLinetoHorizontalAbs.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegLinetoHorizontalRel.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegLinetoRel.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegLinetoVerticalAbs.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegLinetoVerticalRel.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegMovetoAbs.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPathSegMovetoRel.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPatternElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPoint.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPointList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPolygonElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPolylineElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGPreserveAspectRatio.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGRadialGradientElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGRect.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGRectElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGScriptElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGStopElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGStylable.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGStyleElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGSVGElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGSwitchElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGSymbolElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGTextContentElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGTextElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGTextPathElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGTextPositioningElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGTitleElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGTransform.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGTransformable.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGTransformList.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGTSpanElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGUnitTypes.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGURIReference.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGUseElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGViewSpec.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGZoomAndPan.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMSVGZoomEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMText.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMTextMetrics.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMToString.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMTreeWalker.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMUIEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMUserDataHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMViewCSS.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMWindow.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMWindow2.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMWindowCollection.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMWindowInternal.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMWindowUtils.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXMLDocument.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXPathEvaluator.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXPathException.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXPathExpression.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXPathNamespace.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXPathNSResolver.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXPathResult.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULButtonElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULCheckboxElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULCommandDispatcher.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULCommandEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULContainerElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULContainerItemElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULControlElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULDescriptionElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULDocument.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULImageElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULLabeledControlElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULLabelElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULMenuListElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULMultiSelectControlElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULPopupElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULSelectControlElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULSelectControlItemElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULTextBoxElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDOMXULTreeElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIDownload.class);
			interfacesList.add(org.mozilla.interfaces.nsIDownloader.class);
			interfacesList.add(org.mozilla.interfaces.nsIDownloadHistory.class);
			interfacesList.add(org.mozilla.interfaces.nsIDownloadManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIDownloadManagerUI.class);
			interfacesList.add(org.mozilla.interfaces.nsIDownloadObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIDownloadProgressListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIDragDropHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsIDragService.class);
			interfacesList.add(org.mozilla.interfaces.nsIDragSession.class);
			interfacesList.add(org.mozilla.interfaces.nsIDynamicContainer.class);
			interfacesList.add(org.mozilla.interfaces.nsIEditActionListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIEditingSession.class);
			interfacesList.add(org.mozilla.interfaces.nsIEditor.class);
			interfacesList.add(org.mozilla.interfaces.nsIEditorBoxObject.class);
			interfacesList.add(org.mozilla.interfaces.nsIEditorDocShell.class);
			interfacesList.add(org.mozilla.interfaces.nsIEditorIMESupport.class);
			interfacesList.add(org.mozilla.interfaces.nsIEditorLogging.class);
			interfacesList.add(org.mozilla.interfaces.nsIEditorMailSupport.class);
			interfacesList.add(org.mozilla.interfaces.nsIEditorObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIEditorSpellCheck.class);
			interfacesList.add(org.mozilla.interfaces.nsIEditorStyleSheets.class);
			interfacesList.add(org.mozilla.interfaces.nsIEffectiveTLDService.class);
			interfacesList.add(org.mozilla.interfaces.nsIEmbeddingSiteWindow.class);
			interfacesList.add(org.mozilla.interfaces.nsIEmbeddingSiteWindow2.class);
			interfacesList.add(org.mozilla.interfaces.nsIEncodedChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIEntityConverter.class);
			interfacesList.add(org.mozilla.interfaces.nsIEnumerator.class);
			interfacesList.add(org.mozilla.interfaces.nsIEnvironment.class);
			interfacesList.add(org.mozilla.interfaces.nsIErrorService.class);
			interfacesList.add(org.mozilla.interfaces.nsIEventTarget.class);
			interfacesList.add(org.mozilla.interfaces.nsIException.class);
			interfacesList.add(org.mozilla.interfaces.nsIExceptionManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIExceptionProvider.class);
			interfacesList.add(org.mozilla.interfaces.nsIExceptionService.class);
			interfacesList.add(org.mozilla.interfaces.nsIExpatSink.class);
			interfacesList.add(org.mozilla.interfaces.nsIExtendedExpatSink.class);
			interfacesList.add(org.mozilla.interfaces.nsIExtensionManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIExternalHelperAppService.class);
			interfacesList.add(org.mozilla.interfaces.nsIExternalProtocolHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsIExternalProtocolService.class);
			interfacesList.add(org.mozilla.interfaces.nsIFactory.class);
			interfacesList.add(org.mozilla.interfaces.nsIFastLoadFileControl.class);
			interfacesList.add(org.mozilla.interfaces.nsIFastLoadFileIO.class);
			interfacesList.add(org.mozilla.interfaces.nsIFastLoadReadControl.class);
			interfacesList.add(org.mozilla.interfaces.nsIFastLoadService.class);
			interfacesList.add(org.mozilla.interfaces.nsIFastLoadWriteControl.class);
			interfacesList.add(org.mozilla.interfaces.nsIFaviconService.class);
			interfacesList.add(org.mozilla.interfaces.nsIFeed.class);
			interfacesList.add(org.mozilla.interfaces.nsIFeedContainer.class);
			interfacesList.add(org.mozilla.interfaces.nsIFeedElementBase.class);
			interfacesList.add(org.mozilla.interfaces.nsIFeedEntry.class);
			interfacesList.add(org.mozilla.interfaces.nsIFeedGenerator.class);
			interfacesList.add(org.mozilla.interfaces.nsIFeedPerson.class);
			interfacesList.add(org.mozilla.interfaces.nsIFeedProcessor.class);
			interfacesList.add(org.mozilla.interfaces.nsIFeedProgressListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIFeedResult.class);
			interfacesList.add(org.mozilla.interfaces.nsIFeedResultListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIFeedTextConstruct.class);
			interfacesList.add(org.mozilla.interfaces.nsIFile.class);
			interfacesList.add(org.mozilla.interfaces.nsIFileChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIFileInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIFileOutputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIFilePicker.class);
			interfacesList.add(org.mozilla.interfaces.nsIFileProtocolHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsIFileURL.class);
//			interfacesList.add(org.mozilla.interfaces.nsIFileView.class);
			interfacesList.add(org.mozilla.interfaces.nsIFind.class);
			interfacesList.add(org.mozilla.interfaces.nsIFindService.class);
			interfacesList.add(org.mozilla.interfaces.nsIFlavorDataProvider.class);
			interfacesList.add(org.mozilla.interfaces.nsIFontEnumerator.class);
			interfacesList.add(org.mozilla.interfaces.nsIFormatConverter.class);
			interfacesList.add(org.mozilla.interfaces.nsIFormFillController.class);
			interfacesList.add(org.mozilla.interfaces.nsIFormHistory2.class);
			interfacesList.add(org.mozilla.interfaces.nsIFormHistoryImporter.class);
			interfacesList.add(org.mozilla.interfaces.nsIFormSigningDialog.class);
			interfacesList.add(org.mozilla.interfaces.nsIFormSubmitObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIForwardIterator.class);
			interfacesList.add(org.mozilla.interfaces.nsIFrameLoader.class);
			interfacesList.add(org.mozilla.interfaces.nsIFrameLoaderOwner.class);
			interfacesList.add(org.mozilla.interfaces.nsIFTPChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIFTPEventSink.class);
//			interfacesList.add(org.mozilla.interfaces.nsIFullScreen.class);
			interfacesList.add(org.mozilla.interfaces.nsIGConfService.class);
			interfacesList.add(org.mozilla.interfaces.nsIGeneratingKeypairInfoDialogs.class);
			interfacesList.add(org.mozilla.interfaces.nsIGeolocationPrompt.class);
			interfacesList.add(org.mozilla.interfaces.nsIGeolocationProvider.class);
			interfacesList.add(org.mozilla.interfaces.nsIGeolocationRequest.class);
			interfacesList.add(org.mozilla.interfaces.nsIGeolocationUpdate.class);
			interfacesList.add(org.mozilla.interfaces.nsIGlobalHistory.class);
			interfacesList.add(org.mozilla.interfaces.nsIGlobalHistory2.class);
			interfacesList.add(org.mozilla.interfaces.nsIGlobalHistory3.class);
			interfacesList.add(org.mozilla.interfaces.nsIGnomeVFSMimeApp.class);
			interfacesList.add(org.mozilla.interfaces.nsIGnomeVFSService.class);
			interfacesList.add(org.mozilla.interfaces.nsIHandlerApp.class);
			interfacesList.add(org.mozilla.interfaces.nsIHandlerInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIHandlerService.class);
			interfacesList.add(org.mozilla.interfaces.nsIHashable.class);
			interfacesList.add(org.mozilla.interfaces.nsIHelperAppLauncher.class);
			interfacesList.add(org.mozilla.interfaces.nsIHelperAppLauncherDialog.class);
			interfacesList.add(org.mozilla.interfaces.nsIHistoryEntry.class);
			interfacesList.add(org.mozilla.interfaces.nsIHTMLAbsPosEditor.class);
			interfacesList.add(org.mozilla.interfaces.nsIHTMLEditor.class);
			interfacesList.add(org.mozilla.interfaces.nsIHTMLInlineTableEditor.class);
			interfacesList.add(org.mozilla.interfaces.nsIHTMLObjectResizeListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIHTMLObjectResizer.class);
			interfacesList.add(org.mozilla.interfaces.nsIHttpActivityObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIHttpAuthenticator.class);
			interfacesList.add(org.mozilla.interfaces.nsIHttpAuthManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIHttpChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIHttpChannelInternal.class);
			interfacesList.add(org.mozilla.interfaces.nsIHttpEventSink.class);
			interfacesList.add(org.mozilla.interfaces.nsIHTTPHeaderListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIHttpHeaderVisitor.class);
			interfacesList.add(org.mozilla.interfaces.nsIHTTPIndex.class);
			interfacesList.add(org.mozilla.interfaces.nsIHttpProtocolHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsIIdentityInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIIdleService.class);
			interfacesList.add(org.mozilla.interfaces.nsIIDNService.class);
			interfacesList.add(org.mozilla.interfaces.nsIIFrameBoxObject.class);
			interfacesList.add(org.mozilla.interfaces.nsIImageDocument.class);
			interfacesList.add(org.mozilla.interfaces.nsIImageLoadingContent.class);
			interfacesList.add(org.mozilla.interfaces.nsIIncrementalDownload.class);
			interfacesList.add(org.mozilla.interfaces.nsIINIParser.class);
			interfacesList.add(org.mozilla.interfaces.nsIINIParserFactory.class);
			interfacesList.add(org.mozilla.interfaces.nsIInlineSpellChecker.class);
			interfacesList.add(org.mozilla.interfaces.nsIInputIterator.class);
			interfacesList.add(org.mozilla.interfaces.nsIInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIInputStreamCallback.class);
			interfacesList.add(org.mozilla.interfaces.nsIInputStreamChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIInputStreamPump.class);
			interfacesList.add(org.mozilla.interfaces.nsIInputStreamTee.class);
			interfacesList.add(org.mozilla.interfaces.nsIInstallLocation.class);
			interfacesList.add(org.mozilla.interfaces.nsIInterfaceRequestor.class);
			interfacesList.add(org.mozilla.interfaces.nsIIOService.class);
			interfacesList.add(org.mozilla.interfaces.nsIIOService2.class);
			interfacesList.add(org.mozilla.interfaces.nsIJARChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIJARProtocolHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsIJARURI.class);
			interfacesList.add(org.mozilla.interfaces.nsIJSCID.class);
			interfacesList.add(org.mozilla.interfaces.nsIJSID.class);
			interfacesList.add(org.mozilla.interfaces.nsIJSIID.class);
			interfacesList.add(org.mozilla.interfaces.nsIJSON.class);
			interfacesList.add(org.mozilla.interfaces.nsIJSXMLHttpRequest.class);
//			interfacesList.add(org.mozilla.interfaces.nsIJVMConfig.class);
//			interfacesList.add(org.mozilla.interfaces.nsIJVMConfigManager.class);
//			interfacesList.add(org.mozilla.interfaces.nsIJVMManager.class);
//			interfacesList.add(org.mozilla.interfaces.nsIJVMPluginInstance.class);
			interfacesList.add(org.mozilla.interfaces.nsIKeygenThread.class);
			interfacesList.add(org.mozilla.interfaces.nsIKeyObject.class);
			interfacesList.add(org.mozilla.interfaces.nsIKeyObjectFactory.class);
			interfacesList.add(org.mozilla.interfaces.nsILineInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIListBoxObject.class);
			interfacesList.add(org.mozilla.interfaces.nsILivemarkService.class);
			interfacesList.add(org.mozilla.interfaces.nsILoadContext.class);
			interfacesList.add(org.mozilla.interfaces.nsILoadGroup.class);
			interfacesList.add(org.mozilla.interfaces.nsILocale.class);
			interfacesList.add(org.mozilla.interfaces.nsILocaleService.class);
			interfacesList.add(org.mozilla.interfaces.nsILocalFile.class);
			interfacesList.add(org.mozilla.interfaces.nsILocalFileWin.class);
			interfacesList.add(org.mozilla.interfaces.nsILocalHandlerApp.class);
			interfacesList.add(org.mozilla.interfaces.nsILoginInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsILoginManager.class);
//			interfacesList.add(org.mozilla.interfaces.nsILoginManager_MOZILLA_1_9_1.class);
			interfacesList.add(org.mozilla.interfaces.nsILoginManagerIEMigrationHelper.class);
			interfacesList.add(org.mozilla.interfaces.nsILoginManagerPrompter.class);
			interfacesList.add(org.mozilla.interfaces.nsILoginManagerStorage.class);
			interfacesList.add(org.mozilla.interfaces.nsILoginMetaInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIMarkupDocumentViewer.class);
			interfacesList.add(org.mozilla.interfaces.nsIMemory.class);
			interfacesList.add(org.mozilla.interfaces.nsIMemoryReporter.class);
			interfacesList.add(org.mozilla.interfaces.nsIMemoryReporterManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIMenuBoxObject.class);
			interfacesList.add(org.mozilla.interfaces.nsIMIMEHeaderParam.class);
			interfacesList.add(org.mozilla.interfaces.nsIMIMEInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIMIMEInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIMIMEService.class);
			interfacesList.add(org.mozilla.interfaces.nsIModule.class);
			interfacesList.add(org.mozilla.interfaces.nsIModuleLoader.class);
			interfacesList.add(org.mozilla.interfaces.nsIMozIconURI.class);
			interfacesList.add(org.mozilla.interfaces.nsIMultiPartChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIMultiplexInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIMutable.class);
			interfacesList.add(org.mozilla.interfaces.nsIMutableArray.class);
			interfacesList.add(org.mozilla.interfaces.nsINativeAppSupport.class);
			interfacesList.add(org.mozilla.interfaces.nsINavBookmarkObserver.class);
//			interfacesList.add(org.mozilla.interfaces.nsINavBookmarkObserver_MOZILLA_1_9_1_ADDITIONS.class);
			interfacesList.add(org.mozilla.interfaces.nsINavBookmarksService.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryBatchCallback.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryContainerResultNode.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryFullVisitResultNode.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryObserver.class);
//			interfacesList.add(org.mozilla.interfaces.nsINavHistoryObserver_MOZILLA_1_9_1_ADDITIONS.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryQuery.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryQueryOptions.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryQueryResultNode.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryResult.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryResultNode.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryResultTreeViewer.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryResultViewer.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryService.class);
			interfacesList.add(org.mozilla.interfaces.nsINavHistoryVisitResultNode.class);
			interfacesList.add(org.mozilla.interfaces.nsINestedURI.class);
			interfacesList.add(org.mozilla.interfaces.nsINetUtil.class);
			interfacesList.add(org.mozilla.interfaces.nsINetworkLinkService.class);
			interfacesList.add(org.mozilla.interfaces.nsINonBlockingAlertService.class);
			interfacesList.add(org.mozilla.interfaces.nsINSSCertCache.class);
			interfacesList.add(org.mozilla.interfaces.nsINSSErrorsService.class);
			interfacesList.add(org.mozilla.interfaces.nsIObjectInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIObjectLoadingContent.class);
			interfacesList.add(org.mozilla.interfaces.nsIObjectOutputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIObserverService.class);
			interfacesList.add(org.mozilla.interfaces.nsIOCSPResponder.class);
			interfacesList.add(org.mozilla.interfaces.nsIOfflineCacheUpdate.class);
			interfacesList.add(org.mozilla.interfaces.nsIOfflineCacheUpdateObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIOfflineCacheUpdateService.class);
//			interfacesList.add(org.mozilla.interfaces.nsIOSChromeItem.class);
			interfacesList.add(org.mozilla.interfaces.nsIOutputIterator.class);
			interfacesList.add(org.mozilla.interfaces.nsIOutputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIOutputStreamCallback.class);
			interfacesList.add(org.mozilla.interfaces.nsIParentalControlsService.class);
			interfacesList.add(org.mozilla.interfaces.nsIPasswordManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIPasswordManagerInternal.class);
			interfacesList.add(org.mozilla.interfaces.nsIPermission.class);
			interfacesList.add(org.mozilla.interfaces.nsIPermissionManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIPersistentProperties.class);
			interfacesList.add(org.mozilla.interfaces.nsIPhonetic.class);
			interfacesList.add(org.mozilla.interfaces.nsIPipe.class);
			interfacesList.add(org.mozilla.interfaces.nsIPK11Token.class);
			interfacesList.add(org.mozilla.interfaces.nsIPK11TokenDB.class);
			interfacesList.add(org.mozilla.interfaces.nsIPKCS11.class);
			interfacesList.add(org.mozilla.interfaces.nsIPKCS11Module.class);
			interfacesList.add(org.mozilla.interfaces.nsIPKCS11ModuleDB.class);
			interfacesList.add(org.mozilla.interfaces.nsIPKCS11Slot.class);
			interfacesList.add(org.mozilla.interfaces.nsIPKIParamBlock.class);
			interfacesList.add(org.mozilla.interfaces.nsIPlaintextEditor.class);
			interfacesList.add(org.mozilla.interfaces.nsIPluginHost.class);
//			interfacesList.add(org.mozilla.interfaces.nsIPluginManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIPluginTag.class);
			interfacesList.add(org.mozilla.interfaces.nsIPopupBoxObject.class);
			interfacesList.add(org.mozilla.interfaces.nsIPopupWindowManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrefBranch.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrefBranch2.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrefBranchInternal.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrefetchService.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrefLocalizedString.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrefService.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrincipal.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrinterEnumerator.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrintingPrompt.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrintingPromptService.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrintOptions.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrintProgress.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrintProgressParams.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrintSettings.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrintSettingsService.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrintStatusFeedback.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrivateBrowsingService.class);
			interfacesList.add(org.mozilla.interfaces.nsIProcess.class);
//			interfacesList.add(org.mozilla.interfaces.nsIProcess2.class);
			interfacesList.add(org.mozilla.interfaces.nsIProfile.class);
			interfacesList.add(org.mozilla.interfaces.nsIProfileChangeStatus.class);
			interfacesList.add(org.mozilla.interfaces.nsIProfileLock.class);
			interfacesList.add(org.mozilla.interfaces.nsIProfileMigrator.class);
			interfacesList.add(org.mozilla.interfaces.nsIProfileStartup.class);
			interfacesList.add(org.mozilla.interfaces.nsIProfileUnlocker.class);
			interfacesList.add(org.mozilla.interfaces.nsIProgrammingLanguage.class);
			interfacesList.add(org.mozilla.interfaces.nsIProgressEventSink.class);
			interfacesList.add(org.mozilla.interfaces.nsIPrompt.class);
			interfacesList.add(org.mozilla.interfaces.nsIPromptFactory.class);
			interfacesList.add(org.mozilla.interfaces.nsIPromptService.class);
			interfacesList.add(org.mozilla.interfaces.nsIPromptService2.class);
			interfacesList.add(org.mozilla.interfaces.nsIProperties.class);
			interfacesList.add(org.mozilla.interfaces.nsIProperty.class);
			interfacesList.add(org.mozilla.interfaces.nsIPropertyBag.class);
			interfacesList.add(org.mozilla.interfaces.nsIPropertyBag2.class);
			interfacesList.add(org.mozilla.interfaces.nsIPropertyElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIProtectedAuthThread.class);
			interfacesList.add(org.mozilla.interfaces.nsIProtocolHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsIProtocolProxyCallback.class);
			interfacesList.add(org.mozilla.interfaces.nsIProtocolProxyFilter.class);
			interfacesList.add(org.mozilla.interfaces.nsIProtocolProxyService.class);
			interfacesList.add(org.mozilla.interfaces.nsIProtocolProxyService2.class);
			interfacesList.add(org.mozilla.interfaces.nsIProxiedChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIProxiedProtocolHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsIProxyAutoConfig.class);
			interfacesList.add(org.mozilla.interfaces.nsIProxyInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIProxyObjectManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIRandomAccessIterator.class);
			interfacesList.add(org.mozilla.interfaces.nsIRandomGenerator.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFBlob.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFCompositeDataSource.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFContainer.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFContainerUtils.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFDataSource.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFDate.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFDelegateFactory.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFInferDataSource.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFInMemoryDataSource.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFInt.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFLiteral.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFNode.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFPropagatableDataSource.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFPurgeableDataSource.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFRemoteDataSource.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFResource.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFService.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFXMLParser.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFXMLSerializer.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFXMLSink.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFXMLSinkObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIRDFXMLSource.class);
			interfacesList.add(org.mozilla.interfaces.nsIRecentBadCertsService.class);
			interfacesList.add(org.mozilla.interfaces.nsIRecyclingAllocator.class);
			interfacesList.add(org.mozilla.interfaces.nsIRefreshURI.class);
			interfacesList.add(org.mozilla.interfaces.nsIRelativeFilePref.class);
//			interfacesList.add(org.mozilla.interfaces.nsIRemoteService.class);
			interfacesList.add(org.mozilla.interfaces.nsIRequest.class);
			interfacesList.add(org.mozilla.interfaces.nsIRequestObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIRequestObserverProxy.class);
			interfacesList.add(org.mozilla.interfaces.nsIResProtocolHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsIResumableChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIRunnable.class);
			interfacesList.add(org.mozilla.interfaces.nsISafeOutputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsISaveAsCharset.class);
			interfacesList.add(org.mozilla.interfaces.nsISAXAttributes.class);
			interfacesList.add(org.mozilla.interfaces.nsISAXContentHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsISAXDTDHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsISAXErrorHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsISAXLexicalHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsISAXLocator.class);
			interfacesList.add(org.mozilla.interfaces.nsISAXMutableAttributes.class);
			interfacesList.add(org.mozilla.interfaces.nsISAXXMLFilter.class);
			interfacesList.add(org.mozilla.interfaces.nsISAXXMLReader.class);
			interfacesList.add(org.mozilla.interfaces.nsIScreen.class);
			interfacesList.add(org.mozilla.interfaces.nsIScreenManager.class);
//			interfacesList.add(org.mozilla.interfaces.nsIScreenManager_MOZILLA_1_9_1_BRANCH.class);
			interfacesList.add(org.mozilla.interfaces.nsIScriptableDateFormat.class);
			interfacesList.add(org.mozilla.interfaces.nsIScriptableInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIScriptableInterfaces.class);
			interfacesList.add(org.mozilla.interfaces.nsIScriptableInterfacesByID.class);
			interfacesList.add(org.mozilla.interfaces.nsIScriptableRegion.class);
			interfacesList.add(org.mozilla.interfaces.nsIScriptableUnescapeHTML.class);
			interfacesList.add(org.mozilla.interfaces.nsIScriptableUnicodeConverter.class);
			interfacesList.add(org.mozilla.interfaces.nsIScriptChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIScriptError.class);
			interfacesList.add(org.mozilla.interfaces.nsIScriptLoaderObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIScriptSecurityManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIScrollable.class);
			interfacesList.add(org.mozilla.interfaces.nsIScrollBoxObject.class);
			interfacesList.add(org.mozilla.interfaces.nsISearchableInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsISearchContext.class);
			interfacesList.add(org.mozilla.interfaces.nsISearchEngine.class);
			interfacesList.add(org.mozilla.interfaces.nsISearchSubmission.class);
			interfacesList.add(org.mozilla.interfaces.nsISecretDecoderRing.class);
			interfacesList.add(org.mozilla.interfaces.nsISecretDecoderRingConfig.class);
			interfacesList.add(org.mozilla.interfaces.nsISecureBrowserUI.class);
			interfacesList.add(org.mozilla.interfaces.nsISecurityCheckedComponent.class);
			interfacesList.add(org.mozilla.interfaces.nsISecurityEventSink.class);
			interfacesList.add(org.mozilla.interfaces.nsISecurityInfoProvider.class);
			interfacesList.add(org.mozilla.interfaces.nsISecurityWarningDialogs.class);
			interfacesList.add(org.mozilla.interfaces.nsISeekableStream.class);
//			interfacesList.add(org.mozilla.interfaces.nsISelectElement.class);
			interfacesList.add(org.mozilla.interfaces.nsISelection.class);
			interfacesList.add(org.mozilla.interfaces.nsISelection2.class);
			interfacesList.add(org.mozilla.interfaces.nsISelectionController.class);
			interfacesList.add(org.mozilla.interfaces.nsISelectionDisplay.class);
			interfacesList.add(org.mozilla.interfaces.nsISelectionListener.class);
			interfacesList.add(org.mozilla.interfaces.nsISelectionPrivate.class);
			interfacesList.add(org.mozilla.interfaces.nsISemanticUnitScanner.class);
			interfacesList.add(org.mozilla.interfaces.nsISerializable.class);
			interfacesList.add(org.mozilla.interfaces.nsIServerSocket.class);
			interfacesList.add(org.mozilla.interfaces.nsIServerSocketListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIServiceManager.class);
			interfacesList.add(org.mozilla.interfaces.nsISHContainer.class);
			interfacesList.add(org.mozilla.interfaces.nsISHEntry.class);
			interfacesList.add(org.mozilla.interfaces.nsISHistory.class);
			interfacesList.add(org.mozilla.interfaces.nsISHistoryInternal.class);
			interfacesList.add(org.mozilla.interfaces.nsISHistoryListener.class);
			interfacesList.add(org.mozilla.interfaces.nsISHTransaction.class);
			interfacesList.add(org.mozilla.interfaces.nsISidebar.class);
			interfacesList.add(org.mozilla.interfaces.nsISidebarExternal.class);
			interfacesList.add(org.mozilla.interfaces.nsISimpleEnumerator.class);
			interfacesList.add(org.mozilla.interfaces.nsISimpleStreamListener.class);
			interfacesList.add(org.mozilla.interfaces.nsISimpleUnicharStreamFactory.class);
			interfacesList.add(org.mozilla.interfaces.nsISliderListener.class);
			interfacesList.add(org.mozilla.interfaces.nsISMimeCert.class);
			interfacesList.add(org.mozilla.interfaces.nsISocketProvider.class);
			interfacesList.add(org.mozilla.interfaces.nsISocketProviderService.class);
			interfacesList.add(org.mozilla.interfaces.nsISocketTransport.class);
			interfacesList.add(org.mozilla.interfaces.nsISocketTransportService.class);
			interfacesList.add(org.mozilla.interfaces.nsISOCKSSocketInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsISound.class);
			interfacesList.add(org.mozilla.interfaces.nsISSLCertErrorDialog.class);
			interfacesList.add(org.mozilla.interfaces.nsISSLErrorListener.class);
			interfacesList.add(org.mozilla.interfaces.nsISSLSocketControl.class);
			interfacesList.add(org.mozilla.interfaces.nsISSLStatus.class);
			interfacesList.add(org.mozilla.interfaces.nsISSLStatusProvider.class);
			interfacesList.add(org.mozilla.interfaces.nsIStackFrame.class);
			interfacesList.add(org.mozilla.interfaces.nsIStandardURL.class);
			interfacesList.add(org.mozilla.interfaces.nsIStorageStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIStreamCipher.class);
			interfacesList.add(org.mozilla.interfaces.nsIStreamConverter.class);
			interfacesList.add(org.mozilla.interfaces.nsIStreamConverterService.class);
			interfacesList.add(org.mozilla.interfaces.nsIStreamListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIStreamListenerTee.class);
			interfacesList.add(org.mozilla.interfaces.nsIStreamLoader.class);
			interfacesList.add(org.mozilla.interfaces.nsIStreamLoaderObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIStreamTransportService.class);
			interfacesList.add(org.mozilla.interfaces.nsIStringBundle.class);
			interfacesList.add(org.mozilla.interfaces.nsIStringBundleOverride.class);
			interfacesList.add(org.mozilla.interfaces.nsIStringBundleService.class);
			interfacesList.add(org.mozilla.interfaces.nsIStringEnumerator.class);
			interfacesList.add(org.mozilla.interfaces.nsIStringInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIStyleSheetService.class);
			interfacesList.add(org.mozilla.interfaces.nsISupports.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsArray.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsChar.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsCString.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsDouble.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsFloat.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsID.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsInterfacePointer.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsPRBool.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsPrimitive.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsPRInt16.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsPRInt32.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsPRInt64.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsPriority.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsPRTime.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsPRUint16.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsPRUint32.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsPRUint64.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsPRUint8.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsString.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsVoid.class);
			interfacesList.add(org.mozilla.interfaces.nsISupportsWeakReference.class);
			interfacesList.add(org.mozilla.interfaces.nsISyncLoadDOMService.class);
			interfacesList.add(org.mozilla.interfaces.nsISyncStreamListener.class);
			interfacesList.add(org.mozilla.interfaces.nsISystemProxySettings.class);
			interfacesList.add(org.mozilla.interfaces.nsITableEditor.class);
			interfacesList.add(org.mozilla.interfaces.nsITaggingService.class);
			interfacesList.add(org.mozilla.interfaces.nsITextScroll.class);
			interfacesList.add(org.mozilla.interfaces.nsITextServicesFilter.class);
			interfacesList.add(org.mozilla.interfaces.nsITextToSubURI.class);
			interfacesList.add(org.mozilla.interfaces.nsIThread.class);
			interfacesList.add(org.mozilla.interfaces.nsIThreadEventFilter.class);
			interfacesList.add(org.mozilla.interfaces.nsIThreadInternal.class);
			interfacesList.add(org.mozilla.interfaces.nsIThreadManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIThreadObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIThreadPool.class);
			interfacesList.add(org.mozilla.interfaces.nsIThreadPoolListener.class);
			interfacesList.add(org.mozilla.interfaces.nsITimelineService.class);
			interfacesList.add(org.mozilla.interfaces.nsITimer.class);
			interfacesList.add(org.mozilla.interfaces.nsITimerCallback.class);
			interfacesList.add(org.mozilla.interfaces.nsITokenDialogs.class);
			interfacesList.add(org.mozilla.interfaces.nsITokenPasswordDialogs.class);
			interfacesList.add(org.mozilla.interfaces.nsIToolkitChromeRegistry.class);
			interfacesList.add(org.mozilla.interfaces.nsIToolkitProfile.class);
			interfacesList.add(org.mozilla.interfaces.nsIToolkitProfileService.class);
			interfacesList.add(org.mozilla.interfaces.nsITooltipListener.class);
			interfacesList.add(org.mozilla.interfaces.nsITooltipTextProvider.class);
			interfacesList.add(org.mozilla.interfaces.nsITraceableChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsITransaction.class);
			interfacesList.add(org.mozilla.interfaces.nsITransactionList.class);
			interfacesList.add(org.mozilla.interfaces.nsITransactionListener.class);
			interfacesList.add(org.mozilla.interfaces.nsITransactionManager.class);
			interfacesList.add(org.mozilla.interfaces.nsITransfer.class);
			interfacesList.add(org.mozilla.interfaces.nsITransferable.class);
			interfacesList.add(org.mozilla.interfaces.nsITransport.class);
			interfacesList.add(org.mozilla.interfaces.nsITransportEventSink.class);
			interfacesList.add(org.mozilla.interfaces.nsITransportSecurityInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsITreeBoxObject.class);
			interfacesList.add(org.mozilla.interfaces.nsITreeColumn.class);
			interfacesList.add(org.mozilla.interfaces.nsITreeColumns.class);
			interfacesList.add(org.mozilla.interfaces.nsITreeContentView.class);
			interfacesList.add(org.mozilla.interfaces.nsITreeSelection.class);
			interfacesList.add(org.mozilla.interfaces.nsITreeView.class);
			interfacesList.add(org.mozilla.interfaces.nsITXTToHTMLConv.class);
			interfacesList.add(org.mozilla.interfaces.nsITypeAheadFind.class);
			interfacesList.add(org.mozilla.interfaces.nsIUnicharInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIUnicharLineInputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIUnicharOutputStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIUnicharStreamListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIUnicharStreamLoader.class);
			interfacesList.add(org.mozilla.interfaces.nsIUnicharStreamLoaderObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIUnicodeNormalizer.class);
			interfacesList.add(org.mozilla.interfaces.nsIUpdate.class);
			interfacesList.add(org.mozilla.interfaces.nsIUpdateChecker.class);
			interfacesList.add(org.mozilla.interfaces.nsIUpdateCheckListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIUpdateItem.class);
			interfacesList.add(org.mozilla.interfaces.nsIUpdateManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIUpdatePatch.class);
			interfacesList.add(org.mozilla.interfaces.nsIUpdatePrompt.class);
			interfacesList.add(org.mozilla.interfaces.nsIUpdateTimerManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIUploadChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIURI.class);
			interfacesList.add(org.mozilla.interfaces.nsIURIChecker.class);
			interfacesList.add(org.mozilla.interfaces.nsIURIClassifier.class);
			interfacesList.add(org.mozilla.interfaces.nsIURIClassifierCallback.class);
			interfacesList.add(org.mozilla.interfaces.nsIURIContentListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIURIFixup.class);
			interfacesList.add(org.mozilla.interfaces.nsIURILoader.class);
			interfacesList.add(org.mozilla.interfaces.nsIURIRefObject.class);
			interfacesList.add(org.mozilla.interfaces.nsIURL.class);
			interfacesList.add(org.mozilla.interfaces.nsIURLFormatter.class);
			interfacesList.add(org.mozilla.interfaces.nsIURLParser.class);
			interfacesList.add(org.mozilla.interfaces.nsIUserCertPicker.class);
			interfacesList.add(org.mozilla.interfaces.nsIUserInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIUTF8ConverterService.class);
			interfacesList.add(org.mozilla.interfaces.nsIUTF8StringEnumerator.class);
			interfacesList.add(org.mozilla.interfaces.nsIUUIDGenerator.class);
			interfacesList.add(org.mozilla.interfaces.nsIVariant.class);
			interfacesList.add(org.mozilla.interfaces.nsIVersionComparator.class);
			interfacesList.add(org.mozilla.interfaces.nsIWeakReference.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebBrowser.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebBrowserChrome.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebBrowserChrome2.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebBrowserChromeFocus.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebBrowserFind.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebBrowserFindInFrames.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebBrowserFocus.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebBrowserPersist.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebBrowserPrint.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebBrowserSetup.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebBrowserStream.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebContentHandlerRegistrar.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebHandlerApp.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebNavigation.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebNavigationInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebPageDescriptor.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebProgress.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebProgressListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIWebProgressListener2.class);
			interfacesList.add(org.mozilla.interfaces.nsIWifiAccessPoint.class);
			interfacesList.add(org.mozilla.interfaces.nsIWifiListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIWifiMonitor.class);
			interfacesList.add(org.mozilla.interfaces.nsIWindowCreator.class);
			interfacesList.add(org.mozilla.interfaces.nsIWindowCreator2.class);
			interfacesList.add(org.mozilla.interfaces.nsIWindowDataSource.class);
			interfacesList.add(org.mozilla.interfaces.nsIWindowMediator.class);
			interfacesList.add(org.mozilla.interfaces.nsIWindowMediatorListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIWindowProvider.class);
			interfacesList.add(org.mozilla.interfaces.nsIWindowWatcher.class);
			interfacesList.add(org.mozilla.interfaces.nsIWorker.class);
			interfacesList.add(org.mozilla.interfaces.nsIWorkerErrorEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIWorkerGlobalScope.class);
			interfacesList.add(org.mozilla.interfaces.nsIWorkerMessageEvent.class);
			interfacesList.add(org.mozilla.interfaces.nsIWorkerMessagePort.class);
			interfacesList.add(org.mozilla.interfaces.nsIWorkerNavigator.class);
			interfacesList.add(org.mozilla.interfaces.nsIWorkerScope.class);
			interfacesList.add(org.mozilla.interfaces.nsIWritablePropertyBag.class);
			interfacesList.add(org.mozilla.interfaces.nsIWritablePropertyBag2.class);
			interfacesList.add(org.mozilla.interfaces.nsIWritableVariant.class);
			interfacesList.add(org.mozilla.interfaces.nsIWyciwygChannel.class);
			interfacesList.add(org.mozilla.interfaces.nsIX509Cert.class);
			interfacesList.add(org.mozilla.interfaces.nsIX509Cert2.class);
			interfacesList.add(org.mozilla.interfaces.nsIX509Cert3.class);
			interfacesList.add(org.mozilla.interfaces.nsIX509CertDB.class);
			interfacesList.add(org.mozilla.interfaces.nsIX509CertDB2.class);
			interfacesList.add(org.mozilla.interfaces.nsIX509CertList.class);
			interfacesList.add(org.mozilla.interfaces.nsIX509CertValidity.class);
//			interfacesList.add(org.mozilla.interfaces.nsIXBLAccessible.class);
			interfacesList.add(org.mozilla.interfaces.nsIXMLContentBuilder.class);
			interfacesList.add(org.mozilla.interfaces.nsIXMLHttpRequest.class);
			interfacesList.add(org.mozilla.interfaces.nsIXMLHttpRequestEventTarget.class);
			interfacesList.add(org.mozilla.interfaces.nsIXMLHttpRequestUpload.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCComponents.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCComponents_Classes.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCComponents_ClassesByID.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCComponents_Constructor.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCComponents_Exception.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCComponents_ID.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCComponents_Results.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCComponents_Utils.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCComponents_utils_Sandbox.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCConstructor.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCException.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCSecurityManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPCWrappedJSObjectGetter.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPIDialogService.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPIInstallInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPInstallManager.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPIProgressDialog.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPointerResult.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPTLoader.class);
			interfacesList.add(org.mozilla.interfaces.nsIXPTLoaderSink.class);
			interfacesList.add(org.mozilla.interfaces.nsIXSLTException.class);
			interfacesList.add(org.mozilla.interfaces.nsIXSLTProcessor.class);
			interfacesList.add(org.mozilla.interfaces.nsIXSLTProcessorObsolete.class);
			interfacesList.add(org.mozilla.interfaces.nsIXSLTProcessorPrivate.class);
			interfacesList.add(org.mozilla.interfaces.nsIXTFAttributeHandler.class);
			interfacesList.add(org.mozilla.interfaces.nsIXTFElement.class);
			interfacesList.add(org.mozilla.interfaces.nsIXTFElementFactory.class);
			interfacesList.add(org.mozilla.interfaces.nsIXTFElementWrapper.class);
			interfacesList.add(org.mozilla.interfaces.nsIXTFPrivate.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULAppInfo.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULAppInstall.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULBrowserWindow.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULBuilderListener.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULChromeRegistry.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULOverlayProvider.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULRuntime.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULSortService.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULTemplateBuilder.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULTemplateQueryProcessor.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULTemplateResult.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULTemplateRuleFilter.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULTreeBuilder.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULTreeBuilderObserver.class);
			interfacesList.add(org.mozilla.interfaces.nsIXULWindow.class);
			interfacesList.add(org.mozilla.interfaces.nsIZipEntry.class);
			interfacesList.add(org.mozilla.interfaces.nsIZipReader.class);
			interfacesList.add(org.mozilla.interfaces.nsIZipReaderCache.class);
			interfacesList.add(org.mozilla.interfaces.nsIZipWriter.class);
			interfacesList.add(org.mozilla.interfaces.nsPICommandUpdater.class);
			interfacesList.add(org.mozilla.interfaces.nsPIDNSService.class);
			interfacesList.add(org.mozilla.interfaces.nsPIEditorTransaction.class);
			interfacesList.add(org.mozilla.interfaces.nsPIExternalAppLauncher.class);
			interfacesList.add(org.mozilla.interfaces.nsPIPlacesDatabase.class);
			interfacesList.add(org.mozilla.interfaces.nsPISocketTransportService.class);
			interfacesList.add(org.mozilla.interfaces.rdfIDataSource.class);
			interfacesList.add(org.mozilla.interfaces.rdfISerializer.class);
			interfacesList.add(org.mozilla.interfaces.rdfITripleVisitor.class);
			interfacesList.add(org.mozilla.interfaces.txIEXSLTRegExFunctions.class);
			interfacesList.add(org.mozilla.interfaces.txIFunctionEvaluationContext.class);
			interfacesList.add(org.mozilla.interfaces.txINodeSet.class);
			interfacesList.add(org.mozilla.interfaces.txIXPathObject.class);
			interfacesList.add(org.mozilla.interfaces.xpcIJSModuleLoader.class);
			interfacesList.add(org.mozilla.interfaces.xpcIJSWeakReference.class);
		}

		return interfacesList;
	}
}
