/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Anton Leherbauer (Wind River Systems) - initial API and implementation
 *   Markus Schorn (Wind River Systems)
 *   Mike Kucera (IBM)
 *******************************************************************************/
package org.eclipse.cdt.core.dom.parser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.cdt.core.dom.ICodeReaderFactory;
import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTCompletionNode;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorMacroExpansion;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.model.AbstractLanguage;
import org.eclipse.cdt.core.model.ICLanguageKeywords;
import org.eclipse.cdt.core.model.IContributedModelBuilder;
import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.core.parser.CodeReader;
import org.eclipse.cdt.core.parser.IParserLogService;
import org.eclipse.cdt.core.parser.IScanner;
import org.eclipse.cdt.core.parser.IScannerInfo;
import org.eclipse.cdt.core.parser.KeywordSetKey;
import org.eclipse.cdt.core.parser.ParserLanguage;
import org.eclipse.cdt.core.parser.ParserMode;
import org.eclipse.cdt.core.parser.util.CharArrayIntMap;
import org.eclipse.cdt.internal.core.dom.parser.AbstractGNUSourceCodeParser;
import org.eclipse.cdt.internal.core.parser.scanner.CPreprocessor;
import org.eclipse.cdt.internal.core.parser.token.KeywordSets;
import org.eclipse.cdt.internal.core.util.ICancelable;
import org.eclipse.cdt.internal.core.util.ICanceler;
import org.eclipse.core.runtime.CoreException;

/**
 * This class provides a skeletal implementation of the ILanguage interface
 * for the DOM parser framework.
 * 
 * This class uses the template method pattern, derived classes need only implement
 * {@link AbstractCLikeLanguage#getScannerExtensionConfiguration()},
 * {@link AbstractCLikeLanguage#getParserLanguage()} and
 * {@link AbstractCLikeLanguage#createParser(IScanner scanner, ParserMode parserMode,
 *                                           IParserLogService logService, IIndex index)}.
 * 
 * <p>
 * <strong>EXPERIMENTAL</strong>. This class or interface has been added as
 * part of a work in progress. There is no guarantee that this API will work or
 * that it will remain the same. Please do not use this API without consulting
 * with the CDT team.
 * </p>
 * 
 * @see AbstractScannerExtensionConfiguration
 * 
 * @since 5.0
 */
public abstract class AbstractCLikeLanguage extends AbstractLanguage implements ICLanguageKeywords {

	
	static class NameCollector extends ASTVisitor {
		{
			shouldVisitNames= true;
		}

		private List<IASTName> nameList= new ArrayList<IASTName>();

		@Override public int visit(IASTName name) {
			nameList.add(name);
			return PROCESS_CONTINUE;
		}

		public IASTName[] getNames() {
			return nameList.toArray(new IASTName[nameList.size()]);
		}
	}
	
	
	/**
	 * @return the scanner extension configuration for this language, may not
	 *         return <code>null</code>
	 */
	protected abstract IScannerExtensionConfiguration getScannerExtensionConfiguration();

	
	/**
	 * @returns the actual parser object.
	 */
	protected abstract ISourceCodeParser createParser(IScanner scanner, ParserMode parserMode,
			IParserLogService logService, IIndex index);
	
	
	/**
	 * @return The ParserLanguage value corresponding to the language supported.
	 */
	protected abstract ParserLanguage getParserLanguage();
	
	
	
	public IASTTranslationUnit getASTTranslationUnit(CodeReader reader, IScannerInfo scanInfo,
			ICodeReaderFactory fileCreator, IIndex index, IParserLogService log) throws CoreException {
		return getASTTranslationUnit(reader, scanInfo, fileCreator, index, 0, log);
	}
	
	
	@Override
	public IASTTranslationUnit getASTTranslationUnit(CodeReader reader, IScannerInfo scanInfo,
			ICodeReaderFactory codeReaderFactory, IIndex index, int options, IParserLogService log) throws CoreException {

		final IScanner scanner= createScanner(reader, scanInfo, codeReaderFactory, log);
		scanner.setScanComments((options & OPTION_ADD_COMMENTS) != 0);
		scanner.setComputeImageLocations((options & OPTION_NO_IMAGE_LOCATIONS) == 0);

		final ISourceCodeParser parser= createParser(scanner, log, index, false, options);

		// make parser cancelable by reconciler - http://bugs.eclipse.org/226682
		ICanceler canceler= null;
		if (log instanceof ICanceler) {
			canceler= (ICanceler) log;
			canceler.setCancelable(new ICancelable() {
				public void cancel() {
					scanner.cancel();
					parser.cancel();
				}});
		}
		
		try {
			// Parse
			IASTTranslationUnit ast= parser.parse();
			ast.setIsHeaderUnit((options & OPTION_IS_SOURCE_UNIT) == 0);
			return ast;
		} finally {
			if (canceler != null) {
				canceler.setCancelable(null);
			}
		}
	}
	

	public IASTCompletionNode getCompletionNode(CodeReader reader, IScannerInfo scanInfo,
			ICodeReaderFactory fileCreator, IIndex index, IParserLogService log, int offset) throws CoreException {

		IScanner scanner= createScanner(reader, scanInfo, fileCreator, log);
		scanner.setContentAssistMode(offset);

		ISourceCodeParser parser= createParser(scanner, log, index, true, 0);

		// Run the parse and return the completion node
		parser.parse();
		IASTCompletionNode node= parser.getCompletionNode();
		return node;
	}
	
	
	/**
	 * Create the parser.
	 * 
	 * @param scanner  the IScanner to get tokens from
	 * @param log  the parser log service
	 * @param index  the index to help resolve bindings
	 * @param forCompletion  whether the parser is used for code completion
	 * @param options for valid options see {@link AbstractLanguage#getASTTranslationUnit(CodeReader, IScannerInfo, ICodeReaderFactory, IIndex, int, IParserLogService)}
	 * @return  an instance of ISourceCodeParser
	 */
	protected ISourceCodeParser createParser(IScanner scanner, IParserLogService log, IIndex index, boolean forCompletion, int options) {
		ParserMode mode;
		if(forCompletion)
			mode= ParserMode.COMPLETION_PARSE;
		else if((options & OPTION_SKIP_FUNCTION_BODIES) != 0)
			mode= ParserMode.STRUCTURAL_PARSE;
		else
			mode= ParserMode.COMPLETE_PARSE;

		ISourceCodeParser parser= createParser(scanner, mode, log, index);
		if ((options & OPTION_SKIP_TRIVIAL_EXPRESSIONS_IN_AGGREGATE_INITIALIZERS) != 0) {
			if (parser instanceof AbstractGNUSourceCodeParser) {
				((AbstractGNUSourceCodeParser) parser).setSkipTrivialExpressionsInAggregateInitializers(true);
			}
		}
		return parser;
	}
	
	
	/**
	 * Create the scanner to be used with the parser.
	 * 
	 * @param reader  the code reader for the main file
	 * @param scanInfo  the scanner information (macros, include pathes)
	 * @param fileCreator  the code reader factory for inclusions
	 * @param log  the log for debugging
	 * @return an instance of IScanner
	 */
	protected IScanner createScanner(CodeReader reader, IScannerInfo scanInfo, ICodeReaderFactory fileCreator, IParserLogService log) {
		return new CPreprocessor(reader, scanInfo, getParserLanguage(), log, getScannerExtensionConfiguration(), fileCreator);
	}
	
	
	
	public IASTName[] getSelectedNames(IASTTranslationUnit ast, int start, int length) {
		IASTNode selectedNode= ast.getNodeSelector(null).findNode(start, length);

		if (selectedNode == null)
			return new IASTName[0];

		if (selectedNode instanceof IASTName)
			return new IASTName[] { (IASTName) selectedNode };
		
		if (selectedNode instanceof IASTPreprocessorMacroExpansion) {
			return new IASTName[] {((IASTPreprocessorMacroExpansion) selectedNode).getMacroReference()};
		}

		NameCollector collector = new NameCollector();
		selectedNode.accept(collector);
		return collector.getNames();
	}
	
	
	public IContributedModelBuilder createModelBuilder(ITranslationUnit tu) {
		// use default model builder
		return null;
	}

	
	private String[] keywords = null;
	private String[] builtinTypes = null;
	private String[] preprocessorKeywords = null;
	
	
	public String[] getKeywords() {
		if(keywords == null) {
			Set<String> keywordSet = new HashSet<String>(KeywordSets.getKeywords(KeywordSetKey.KEYWORDS, getParserLanguage()));
			
			CharArrayIntMap additionalKeywords = getScannerExtensionConfiguration().getAdditionalKeywords();
			if (additionalKeywords != null) {
				for (Iterator<char[]> iterator = additionalKeywords.toList().iterator(); iterator.hasNext(); ) {
					char[] name = iterator.next();
					keywordSet.add(new String(name));
				}
			}
			keywords = keywordSet.toArray(new String[keywordSet.size()]);
		}
		return keywords;
	}


	public String[] getBuiltinTypes() {
		if(builtinTypes == null) {
			Set<String> types = KeywordSets.getKeywords(KeywordSetKey.TYPES, getParserLanguage());
			builtinTypes = types.toArray(new String[types.size()]);
		}
		return builtinTypes;
	}


	public String[] getPreprocessorKeywords() {
		if(preprocessorKeywords == null) {
			Set<String> keywords = new HashSet<String>(KeywordSets.getKeywords(KeywordSetKey.PP_DIRECTIVE, getParserLanguage()));
			CharArrayIntMap additionalKeywords= getScannerExtensionConfiguration().getAdditionalPreprocessorKeywords();
			if (additionalKeywords != null) {
				for (Iterator<char[]> iterator = additionalKeywords.toList().iterator(); iterator.hasNext(); ) {
					char[] name = iterator.next();
					keywords.add(new String(name));
				}
			}
			preprocessorKeywords = keywords.toArray(new String[keywords.size()]);
		}
		return preprocessorKeywords;
	}
	
}
