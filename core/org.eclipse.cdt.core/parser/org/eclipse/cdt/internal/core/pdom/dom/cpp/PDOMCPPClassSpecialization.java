/*******************************************************************************
 * Copyright (c) 2007 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.core.pdom.dom.cpp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.IPDOMNode;
import org.eclipse.cdt.core.dom.IPDOMVisitor;
import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IField;
import org.eclipse.cdt.core.dom.ast.IScope;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.ITypedef;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPBase;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassScope;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPConstructor;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPField;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPMethod;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPSpecialization;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPTemplateDefinition;
import org.eclipse.cdt.core.index.IIndexBinding;
import org.eclipse.cdt.core.parser.util.ArrayUtil;
import org.eclipse.cdt.core.parser.util.ObjectMap;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPClassScope;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPSemantics;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPTemplates;
import org.eclipse.cdt.internal.core.index.IIndexScope;
import org.eclipse.cdt.internal.core.index.IIndexType;
import org.eclipse.cdt.internal.core.pdom.PDOM;
import org.eclipse.cdt.internal.core.pdom.db.PDOMNodeLinkedList;
import org.eclipse.cdt.internal.core.pdom.dom.BindingCollector;
import org.eclipse.cdt.internal.core.pdom.dom.IPDOMMemberOwner;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMBinding;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMName;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMNode;
import org.eclipse.core.runtime.CoreException;

/**
 * @author Bryan Wilkinson
 * 
 */
class PDOMCPPClassSpecialization extends PDOMCPPSpecialization implements
		ICPPClassType, ICPPClassScope, IPDOMMemberOwner, IIndexType, IIndexScope {

	private static final int FIRSTBASE = PDOMCPPSpecialization.RECORD_SIZE + 0;
	private static final int MEMBERLIST = PDOMCPPSpecialization.RECORD_SIZE + 4;
	
	/**
	 * The size in bytes of a PDOMCPPClassSpecialization record in the database.
	 */
	protected static final int RECORD_SIZE = PDOMCPPSpecialization.RECORD_SIZE + 8;
	
	public PDOMCPPClassSpecialization(PDOM pdom, PDOMNode parent, ICPPClassType classType, PDOMBinding specialized)
			throws CoreException {
		super(pdom, parent, (ICPPSpecialization) classType, specialized);
		if (specialized instanceof PDOMCPPClassTemplate) {
			((PDOMCPPClassTemplate)specialized).addMember(this);
		} else if (specialized instanceof PDOMCPPClassTemplateSpecialization) {
			((PDOMCPPClassTemplateSpecialization)specialized).addMember(this);
		}
	}

	public PDOMCPPClassSpecialization(PDOM pdom, int bindingRecord) {
		super(pdom, bindingRecord);
	}
	
	protected int getRecordSize() {
		return RECORD_SIZE;
	}

	public int getNodeType() {
		return PDOMCPPLinkage.CPP_CLASS_SPECIALIZATION;
	}

	public PDOMCPPBase getFirstBase() throws CoreException {
		int rec = pdom.getDB().getInt(record + FIRSTBASE);
		return rec != 0 ? new PDOMCPPBase(pdom, rec) : null;
	}

	private void setFirstBase(PDOMCPPBase base) throws CoreException {
		int rec = base != null ? base.getRecord() : 0;
		pdom.getDB().putInt(record + FIRSTBASE, rec);
	}
	
	public void addBase(PDOMCPPBase base) throws CoreException {
		PDOMCPPBase firstBase = getFirstBase();
		base.setNextBase(firstBase);
		setFirstBase(base);
	}
	
	public void removeBase(PDOMName pdomName) throws CoreException {
		PDOMCPPBase base= getFirstBase();
		PDOMCPPBase predecessor= null;
		int nameRec= pdomName.getRecord();
		while (base != null) {
			PDOMName name = base.getBaseClassSpecifierNameImpl();
			if (name != null && name.getRecord() == nameRec) {
				break;
			}
			predecessor= base;
			base= base.getNextBase();
		}
		if (base != null) {
			if (predecessor != null) {
				predecessor.setNextBase(base.getNextBase());
			}
			else {
				setFirstBase(base.getNextBase());
			}
			base.delete();
		}
	}
	
	public IField findField(String name) throws DOMException { fail(); return null; }
	public ICPPMethod[] getAllDeclaredMethods() throws DOMException { fail(); return null; }

	public ICPPBase[] getBases() throws DOMException {
		if (!(this instanceof ICPPTemplateDefinition) && 
				getSpecializedBinding() instanceof ICPPTemplateDefinition) {
			//this is an explicit specialization
			try {
				List list = new ArrayList();
				for (PDOMCPPBase base = getFirstBase(); base != null; base = base.getNextBase())
					list.add(base);
				Collections.reverse(list);
				ICPPBase[] bases = (ICPPBase[])list.toArray(new ICPPBase[list.size()]);
				return bases;
			} catch (CoreException e) {
				CCorePlugin.log(e);
			}
		} else {
			//this is an implicit specialization
			ICPPBase[] pdomBases = ((ICPPClassType) getSpecializedBinding()).getBases();

			if (pdomBases != null) {
				ICPPBase[] result = null;
				
				for (int i = 0; i < pdomBases.length; i++) {
					PDOMCPPBase pdomBase = (PDOMCPPBase) pdomBases[i];
					IType type = (IType) pdomBase.getBaseClass();
					type = CPPTemplates.instantiateType(type, getArgumentMap());
					type = CPPSemantics.getUltimateType(type, false);
					if (type instanceof IBinding) {
						result = (ICPPBase[]) ArrayUtil.append(ICPPBase.class, result,
								pdomBase.createSpecialization((IBinding) type));
					}
				}
				
				return (ICPPBase[]) ArrayUtil.trim(ICPPBase.class, result);
			}
		}
		
		return new ICPPBase[0];
	}

	private static class ConstructorCollector implements IPDOMVisitor {
		private List fConstructors = new ArrayList();
		public boolean visit(IPDOMNode node) throws CoreException {
			if (node instanceof ICPPConstructor)
				fConstructors.add(node);
			return false;
		}
		public void leave(IPDOMNode node) throws CoreException {
		}
		public ICPPConstructor[] getConstructors() {
			return (ICPPConstructor[])fConstructors.toArray(new ICPPConstructor[fConstructors.size()]);
		}
	}

	public ICPPConstructor[] getConstructors() throws DOMException {
		try {
			ConstructorCollector visitor= new ConstructorCollector();
			accept(visitor);
			return visitor.getConstructors();
		} catch (CoreException e) {
			CCorePlugin.log(e);
			return ICPPConstructor.EMPTY_CONSTRUCTOR_ARRAY;
		}
	}
	
	//ICPPClassType unimplemented
	public ICPPField[] getDeclaredFields() throws DOMException { fail(); return null; }
	public ICPPMethod[] getDeclaredMethods() throws DOMException { fail(); return null; }
	public IField[] getFields() throws DOMException { fail(); return null; }
	public IBinding[] getFriends() throws DOMException { fail(); return null; }
	public ICPPMethod[] getMethods() throws DOMException { fail(); return null; }
	public ICPPClassType[] getNestedClasses() throws DOMException { fail(); return null; }

	public IScope getCompositeScope() throws DOMException {
		return this;
	}

	public int getKey() throws DOMException {
		return ((ICPPClassType)getSpecializedBinding()).getKey();
	}

	public boolean isSameType(IType type) {
        if( type instanceof PDOMNode ) {
			PDOMNode node = (PDOMNode) type;
			if (node.getPDOM() == getPDOM() && node.getRecord() == getRecord()) {
				return true;
			}
        }
        if( type instanceof ITypedef )
            return ((ITypedef)type).isSameType( this );
        
        return false;
	}
	
	public Object clone() {fail();return null;}

	public ICPPClassType getClassType() {
		return this;
	}

	private class SpecializationFinder implements IPDOMVisitor {
		private ObjectMap specMap;
		public SpecializationFinder(IBinding[] specialized) {
			specMap = new ObjectMap(specialized.length);
			for (int i = 0; i < specialized.length; i++) {
				specMap.put(specialized[i], null);
			}
		}
		public boolean visit(IPDOMNode node) throws CoreException {
			if (node instanceof ICPPSpecialization) {
				IBinding specialized = ((ICPPSpecialization)node).getSpecializedBinding();
				if (specMap.containsKey(specialized)) {
					ICPPSpecialization specialization = (ICPPSpecialization) specMap.get(node);
					if (specialization == null) {
						specMap.remove(specialized);
						specMap.put(specialized, node);
					}
				}
			}
			return false;
		}
		public void leave(IPDOMNode node) throws CoreException {
		}
		public ICPPSpecialization[] getSpecializations() {
			ICPPSpecialization[] result = new ICPPSpecialization[specMap.size()];
			for (int i = 0; i < specMap.size(); i++) {
				ICPPSpecialization specialization = (ICPPSpecialization) specMap.getAt(i);
				if (specialization != null) {
					result[i] = specialization;
				} else {
					result[i] = CPPTemplates.createSpecialization(
							PDOMCPPClassSpecialization.this, (IBinding) specMap
									.keyAt(i), getArgumentMap());
				}
			}
			return result;
		}
	}
	
	public IBinding[] find(String name) throws DOMException {
		return find(name, false);
	}

	public IBinding[] find(String name, boolean prefixLookup)
			throws DOMException {
		if (!(this instanceof ICPPTemplateDefinition) && 
				getSpecializedBinding() instanceof ICPPTemplateDefinition) {
			//this is an explicit specialization
			try {
				BindingCollector visitor = new BindingCollector(getLinkageImpl(), name.toCharArray(), null, prefixLookup, !prefixLookup);
				accept(visitor);
				return visitor.getBindings();
			} catch (CoreException e) {
				CCorePlugin.log(e);
			}
		} else {
			//this is an implicit specialization
			try {						
				IBinding[] specialized = ((ICPPClassType) getSpecializedBinding())
						.getCompositeScope().find(name.toString(), prefixLookup);			
				SpecializationFinder visitor = new SpecializationFinder(specialized);
				accept(visitor);
				return visitor.getSpecializations();
			} catch (CoreException e) {
				CCorePlugin.log(e);
			}
		}
		return null;
	}
	
	public IBinding getBinding(IASTName name, boolean resolve)
			throws DOMException {
		if (!(this instanceof ICPPTemplateDefinition) && 
				getSpecializedBinding() instanceof ICPPTemplateDefinition) {
			//this is an explicit specialization
			try {
			    if (getDBName().equals(name.toCharArray())) {
			        if (CPPClassScope.isConstructorReference(name)){
			            return CPPSemantics.resolveAmbiguities(name, getConstructors());
			        }
		            //9.2 ... The class-name is also inserted into the scope of the class itself
		            return this;
			    }
				
			    BindingCollector visitor = new BindingCollector(getLinkageImpl(), name.toCharArray());
				accept(visitor);
				return CPPSemantics.resolveAmbiguities(name, visitor.getBindings());
			} catch (CoreException e) {
				CCorePlugin.log(e);
			}
		} else {
			//this is an implicit specialization
			try {			
			    if (getDBName().equals(name.toCharArray())) {
			        if (!CPPClassScope.isConstructorReference(name)){
			        	//9.2 ... The class-name is also inserted into the scope of the class itself
			        	return this;
			        }
			    }
				
				IBinding[] specialized = ((ICPPClassType) getSpecializedBinding())
						.getCompositeScope().find(name.toString());			
				SpecializationFinder visitor = new SpecializationFinder(specialized);
				accept(visitor);
				return CPPSemantics.resolveAmbiguities(name, visitor.getSpecializations());
			} catch (CoreException e) {
				CCorePlugin.log(e);
			}
		}
		
		return null;
	}
	
	private static class MethodCollector implements IPDOMVisitor {
		private final List methods;
		private final boolean acceptImplicit;
		private final boolean acceptAll;
		public MethodCollector(boolean acceptImplicit) {
			this(acceptImplicit, true);
		}
		public MethodCollector(boolean acceptImplicit, boolean acceptExplicit) {
			this.methods = new ArrayList();
			this.acceptImplicit= acceptImplicit;
			this.acceptAll= acceptImplicit && acceptExplicit;
		}
		public boolean visit(IPDOMNode node) throws CoreException {
			if (node instanceof ICPPMethod) {
				if (acceptAll || ((ICPPMethod) node).isImplicit() == acceptImplicit) {
					methods.add(node);
				}
			}
			return false; // don't visit the method
		}
		public void leave(IPDOMNode node) throws CoreException {
		}
		public ICPPMethod[] getMethods() {
			return (ICPPMethod[])methods.toArray(new ICPPMethod[methods.size()]); 
		}
	}
	
	public ICPPMethod[] getImplicitMethods() {
		try {
			MethodCollector methods = new MethodCollector(true, false);
			accept(methods);
			return methods.getMethods();
		} catch (CoreException e) {
			return new ICPPMethod[0];
		}
	}

	public IIndexBinding getScopeBinding() {
		return this;
	}

	public void addChild(PDOMNode member) throws CoreException {
		addMember(member);
	}
	
	public void addMember(PDOMNode member) throws CoreException {
		PDOMNodeLinkedList list = new PDOMNodeLinkedList(pdom, record + MEMBERLIST, getLinkageImpl());
		list.addMember(member);
	}

	public void accept(IPDOMVisitor visitor) throws CoreException {
		super.accept(visitor);
		PDOMNodeLinkedList list = new PDOMNodeLinkedList(pdom, record + MEMBERLIST, getLinkageImpl());
		list.accept(visitor);
	}
}
