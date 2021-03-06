/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ChangeSignatureBaseTest;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.testFramework.IdeaTestUtil;

public class ChangeSignatureTouchLambdaTest extends ChangeSignatureBaseTest {
 
  public void testVariableDeclaration() {
    doTestConflict();
  }

  public void testMethodArgument() throws Exception {
    doTestConflict();
  }

  public void testDefaultMethodTouched() throws Exception {
    doTest(null, null, null, new ParameterInfoImpl[] {new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN)}, new ThrownExceptionInfo[0], false);
  }

  public void testDelegateInInterface() throws Exception {
    doTest(null, null, null, new ParameterInfoImpl[] {new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "false")}, new ThrownExceptionInfo[0], true);
  }

  private void doTestConflict() {
    try {
      doTest(null, null, null, new ParameterInfoImpl[] {new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN)}, new ThrownExceptionInfo[0], false);
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException ignored) { }
  }

  @Override
  protected String getRelativePath() {
    return "/codeInsight/daemonCodeAnalyzer/lambda/changeSignature/";
  }
}
