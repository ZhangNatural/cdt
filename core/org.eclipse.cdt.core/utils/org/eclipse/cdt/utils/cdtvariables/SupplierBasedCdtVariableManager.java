/*******************************************************************************
 * Copyright (c) 2007 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intel Corporation - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.utils.cdtvariables;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.cdt.core.cdtvariables.ICdtVariable;

public class SupplierBasedCdtVariableManager {
	/**
	 * @param macroName
	 * @param contextInfo
	 * @param includeParentContexts
	 * @return
	 */
	static public ICdtVariable getVariable(String macroName, IVariableContextInfo contextInfo, boolean includeParentContexts) {
 		if(contextInfo == null || macroName == null)
			return null;
		
		do{
			ICdtVariableSupplier suppliers[] = contextInfo.getSuppliers();
			if(suppliers != null){
				for(int i = 0; i < suppliers.length; i++){
					ICdtVariable macro = suppliers[i].getVariable(macroName, contextInfo);
					if(macro != null)
						return macro;
				}
			}
		}while(includeParentContexts && (contextInfo = contextInfo.getNext()) != null);
		
		return null;
	}

	/**
	 * @param contextInfo
	 * @param includeParentContexts
	 * @return
	 */
	static public ICdtVariable[] getVariables(IVariableContextInfo contextInfo,
			boolean includeParentContexts) {
		if(contextInfo == null)
			return new ICdtVariable[0];
		
		Map map = new HashMap();
		IVariableContextInfo infos[] = includeParentContexts ? 
				getAllVariableContextInfos(contextInfo) :
					new IVariableContextInfo[]{contextInfo};
		
		for(int k = infos.length - 1; k >= 0; k--){
			contextInfo = infos[k];
			ICdtVariableSupplier suppliers[] = contextInfo.getSuppliers();
			if(suppliers != null){
				for(int i = suppliers.length - 1; i >= 0; i--){
					ICdtVariable macros[] = suppliers[i].getVariables(contextInfo);
					if(macros != null){
						for(int j = 0; j < macros.length; j++){
							map.put(macros[j].getName(),macros[j]);
						}
					}
				}
			}
		}
		
		Collection values = map.values();
		return (ICdtVariable[])values.toArray(new ICdtVariable[values.size()]);
	}

	/*
	 * returns an array of the IMacroContextInfo that holds the context informations
	 * starting from the one passed to this method and including all subsequent parents
	 */
	private static IVariableContextInfo[] getAllVariableContextInfos(IVariableContextInfo contextInfo){
		if(contextInfo == null)
			return null;
			
		List list = new ArrayList();
	
		list.add(contextInfo);
			
		while((contextInfo = contextInfo.getNext()) != null)
			list.add(contextInfo);
		
		return (IVariableContextInfo[])list.toArray(new IVariableContextInfo[list.size()]);
	}
	
	/*
	 * returns true if the first passed contextInfo is the child of the second one
	 */
	public static boolean checkParentContextRelation(IVariableContextInfo child, IVariableContextInfo parent){
		if(child == null || parent == null)
			return false;

		IVariableContextInfo enumInfo = child;
		do{
			if(parent.equals(enumInfo))
				return true;
		}while((enumInfo = enumInfo.getNext()) != null);
		return false;
	}

}
