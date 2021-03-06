/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.java.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Set;

public class JavaLexer extends LexerBase {
  private static final Set<String> KEYWORDS = ContainerUtil.newTroveSet(
    "abstract", "default", "if", "private", "this", "boolean", "do", "implements", "protected", "throw", "break", "double", "import",
    "public", "throws", "byte", "else", "instanceof", "return", "transient", "case", "extends", "int", "short", "try", "catch", "final",
    "interface", "static", "void", "char", "finally", "long", "strictfp", "volatile", "class", "float", "native", "super", "while",
    "const", "for", "new", "switch", "continue", "goto", "package", "synchronized", "true", "false", "null");

  public static boolean isKeyword(String id, @NotNull LanguageLevel level) {
    return KEYWORDS.contains(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_4) && "assert".equals(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_5) && "enum".equals(id);
  }

  private final _JavaLexer myFlexLexer;
  private CharSequence myBuffer;
  private char[] myBufferArray;
  private int myBufferIndex;
  private int myBufferEndOffset;
  private int myTokenEndOffset;  // positioned after the last symbol of the current token
  private IElementType myTokenType;

  public JavaLexer(@NotNull LanguageLevel level) {
    myFlexLexer = new _JavaLexer(level);
  }

  @Override
  public final void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myBufferArray = CharArrayUtil.fromSequenceWithoutCopying(buffer);
    myBufferIndex = startOffset;
    myBufferEndOffset = endOffset;
    myTokenType = null;
    myTokenEndOffset = startOffset;
    myFlexLexer.reset(myBuffer, startOffset, endOffset, 0);
  }

  @Override
  public int getState() {
    return 0;
  }

  @Override
  public final IElementType getTokenType() {
    if (myTokenType == null) _locateToken();

    return myTokenType;
  }

  @Override
  public final int getTokenStart() {
    return myBufferIndex;
  }

  @Override
  public final int getTokenEnd() {
    if (myTokenType == null) _locateToken();
    return myTokenEndOffset;
  }

  @Override
  public final void advance() {
    if (myTokenType == null) _locateToken();
    myTokenType = null;
  }

  private void _locateToken() {
    if (myTokenEndOffset == myBufferEndOffset) {
      myTokenType = null;
      myBufferIndex = myBufferEndOffset;
      return;
    }

    myBufferIndex = myTokenEndOffset;

    final char c = myBufferArray != null ? myBufferArray[myBufferIndex]:myBuffer.charAt(myBufferIndex);
    switch (c) {
      case ' ':
      case '\t':
      case '\n':
      case '\r':
      case '\f':
        myTokenType = TokenType.WHITE_SPACE;
        myTokenEndOffset = getWhitespaces(myBufferIndex + 1);
        break;

      case '/':
        if (myBufferIndex + 1 >= myBufferEndOffset) {
          myTokenType = JavaTokenType.DIV;
          myTokenEndOffset = myBufferEndOffset;
        }
        else {
          char nextChar = myBufferArray != null ? myBufferArray[myBufferIndex + 1] : myBuffer.charAt(myBufferIndex + 1);
          if (nextChar == '/') {
            myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
            myTokenEndOffset = getLineTerminator(myBufferIndex + 2);
          }
          else if (nextChar == '*') {
            if (myBufferIndex + 2 >= myBufferEndOffset ||
                (myBufferArray != null ? myBufferArray[myBufferIndex + 2] : myBuffer.charAt(myBufferIndex + 2)) != '*' ||
                (myBufferIndex + 3 < myBufferEndOffset &&
                 (myBufferArray != null ? myBufferArray[myBufferIndex + 3] : myBuffer.charAt(myBufferIndex + 3)) == '/')) {
              myTokenType = JavaTokenType.C_STYLE_COMMENT;
              myTokenEndOffset = getClosingComment(myBufferIndex + 2);
            }
            else {
              myTokenType = JavaDocElementType.DOC_COMMENT;
              myTokenEndOffset = getClosingComment(myBufferIndex + 3);
            }
          }
          else {
            flexLocateToken();
          }
        }
        break;

      case '"':
      case '\'':
        myTokenType = c == '"' ? JavaTokenType.STRING_LITERAL : JavaTokenType.CHARACTER_LITERAL;
        myTokenEndOffset = getClosingParenthesis(myBufferIndex + 1, c);
        break;

      default:
        flexLocateToken();
    }

    if (myTokenEndOffset > myBufferEndOffset) {
      myTokenEndOffset = myBufferEndOffset;
    }
  }

  private int getWhitespaces(int pos) {
    if (pos >= myBufferEndOffset) return myBufferEndOffset;
    final CharSequence lBuffer = myBuffer;
    final char[] lBufferArray = myBufferArray;

    char c = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);

    while (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
      pos++;
      if (pos == myBufferEndOffset) return pos;
      c = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);
    }

    return pos;
  }

  private void flexLocateToken() {
    try {
      myFlexLexer.goTo(myBufferIndex);
      myTokenType = myFlexLexer.advance();
      myTokenEndOffset = myFlexLexer.getTokenEnd();
    }
    catch (IOException e) { /* impossible */ }
    catch (Error e) {
      throw new IllegalArgumentException("Non-parsable text `" + myBuffer + "`");
    }
  }

  private int getClosingParenthesis(int offset, char c) {
    int pos = offset;
    final int lBufferEnd = myBufferEndOffset;
    if (pos >= lBufferEnd) return lBufferEnd;

    final CharSequence lBuffer = myBuffer;
    final char[] lBufferArray = myBufferArray;
    char cur = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);

    while (true) {
      while (cur != c && cur != '\n' && cur != '\r' && cur != '\\') {
        pos++;
        if (pos >= lBufferEnd) return lBufferEnd;
        cur = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);
      }

      if (cur == '\\') {
        pos++;
        if (pos >= lBufferEnd) return lBufferEnd;
        cur = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);
        if (cur == '\n' || cur == '\r') continue;
        pos++;
        if (pos >= lBufferEnd) return lBufferEnd;
        cur = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);
      }
      else if (cur == c) {
        break;
      }
      else {
        pos--;
        break;
      }
    }

    return pos + 1;
  }

  private int getClosingComment(int offset) {
    int pos = offset;

    final int lBufferEnd = myBufferEndOffset;
    final CharSequence lBuffer = myBuffer;
    final char[] lBufferArray = myBufferArray;

    while (pos < lBufferEnd - 1) {
      final char c = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);

      if (c == '*' && (lBufferArray != null ? lBufferArray[pos + 1]:lBuffer.charAt(pos + 1)) == '/') {
        break;
      }
      pos++;
    }

    return pos + 2;
  }

  private int getLineTerminator(int offset) {
    int pos = offset;
    final int lBufferEnd = myBufferEndOffset;
    final CharSequence lBuffer = myBuffer;
    final char[] lBufferArray = myBufferArray;

    while (pos < lBufferEnd) {
      final char c = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);
      if (c == '\r' || c == '\n') break;
      pos++;
    }

    return pos;
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public final int getBufferEnd() {
    return myBufferEndOffset;
  }
}
