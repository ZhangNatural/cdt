/*******************************************************************************
 * Copyright (c) 2004, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM - Initial API and implementation
 * Markus Schorn (Wind River Systems)
 * Anton Leherbauer (Wind River Systems)
 * Emanuel Graf (IFS)
 *******************************************************************************/
package org.eclipse.cdt.internal.core.parser.scanner2;

import org.eclipse.cdt.core.dom.ast.IASTProblem;
import org.eclipse.cdt.core.dom.ast.IMacroBinding;
import org.eclipse.cdt.core.parser.CodeReader;

/**
 * @author jcamelon
 */
public interface IScannerPreprocessorLog {

    public void startTranslationUnit(CodeReader tu_reader);

    public void endTranslationUnit(int offset);

    public void startInclusion(CodeReader reader, int offset, int endOffset, int nameOffset, int nameEndoffset, char[] name, boolean systemInclude);

    public void endInclusion(CodeReader reader, int offset);

    public void startObjectStyleMacroExpansion(IMacroDefinition macro,
            int startOffset, int endOffset);

    public void endObjectStyleMacroExpansion(IMacroDefinition macro, int offset);

    
    public void startFunctionStyleExpansion(IMacroDefinition macro,
            char[][] parameters, int startOffset, int endOffset, Object[] objects);

    public void endFunctionStyleExpansion(IMacroDefinition macro, int offset);

    public interface IMacroDefinition {
        public char[] getName();
        public char[] getExpansion();
        
        public IMacroBinding getBinding();
        public void setBinding( IMacroBinding b );
    }

    public IMacroDefinition defineObjectStyleMacro(ObjectStyleMacro m,
            int startOffset, int nameOffset, int nameEndOffset, int endOffset);

    public IMacroDefinition defineFunctionStyleMacro(FunctionStyleMacro m,
            int startOffset, int nameOffset, int nameEndOffset, int endOffset);

    public void encounterPoundIf(int startOffset, int endOffset, boolean taken, char[] condition);

    public void encounterPoundIfdef(int startOffset, int endOffset,
            boolean taken, char[] condition);

    public void encounterPoundIfndef(int startOffset, int endOffset,
            boolean taken, char[] condition);

    public void encounterPoundElse(int startOffset, int endOffset, boolean taken);

    public void encounterPoundElif(int startOffset, int endOffset, boolean taken, char[] condition);

    public void encounterPoundEndIf(int startOffset, int endOffset);

    public void encounterPoundPragma(int startOffset, int endOffset, char[] msg);

    public void encounterPoundError(int startOffset, int endOffset, char[] msg);

    public void encounterPoundWarning(int startOffset, int endOffset, char[] msg);
    
    public void encounterPoundUndef(int startOffset, int endOffset,
            char[] symbol, int nameOffset, IMacroDefinition macroDefinition);

	public void encounterPoundInclude(int startOffset, int nameOffset, int nameEndOffset, int endOffset, char[] name, boolean systemInclude, boolean active);

	public void encounterProblem(IASTProblem problem);

    public IMacroDefinition registerBuiltinObjectStyleMacro(ObjectStyleMacro macro);

    public IMacroDefinition registerBuiltinFunctionStyleMacro(FunctionStyleMacro macro);

    public IMacroDefinition registerBuiltinDynamicFunctionStyleMacro(DynamicFunctionStyleMacro macro);

    public IMacroDefinition registerBuiltinDynamicStyleMacro(DynamicStyleMacro macro);

}
