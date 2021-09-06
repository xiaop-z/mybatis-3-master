/**
 *    Copyright ${license.git.copyrightYears} the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.scripting.xmltags;

import java.util.regex.Pattern;

import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.scripting.ScriptingException;
import org.apache.ibatis.type.SimpleTypeRegistry;

/**
 * @author Clinton Begin
 */
public class TextSqlNode implements SqlNode {
	private final String text;
	private final Pattern injectionFilter;

	public TextSqlNode(String text) {
		this(text, null);
	}

	public TextSqlNode(String text, Pattern injectionFilter) {
		this.text = text;
		this.injectionFilter = injectionFilter;
	}

	public boolean isDynamic() {
		DynamicCheckerTokenParser checker = new DynamicCheckerTokenParser();
		// 创建分词解析器，主要用来解析SQL字符串，包括#{}和${}，具体对于#{}和${}的处理，交给TokenHandler子类去处理
		GenericTokenParser parser = createParser(checker);
		// 进行解析，如果是${}，此处直接设置Dynamic为true
		parser.parse(text);
		return checker.isDynamic();
	}

	@Override
	public boolean apply(DynamicContext context) {
		GenericTokenParser parser = createParser(new BindingTokenParser(context, injectionFilter));
		context.appendSql(parser.parse(text));
		return true;
	}

	private GenericTokenParser createParser(TokenHandler handler) {
		return new GenericTokenParser("${", "}", handler);
	}

	private static class BindingTokenParser implements TokenHandler {

		private DynamicContext context;
		private Pattern injectionFilter;

		public BindingTokenParser(DynamicContext context, Pattern injectionFilter) {
			this.context = context;
			this.injectionFilter = injectionFilter;
		}

		@Override
		public String handleToken(String content) {
			// 获取入参对象
			Object parameter = context.getBindings().get("_parameter");
			// 如果入参对象为null或者入参对象是简单类型，不需要关注${}中的名称，直接设置${}的参数名称必须为value
			// 如果content不为value，则取不出数据，会报错
			if (parameter == null) {
				// context.getBindings().put("value", null);
				return "";
			} else if (SimpleTypeRegistry.isSimpleType(parameter.getClass())) {
				// context.getBindings().put("value", parameter);
				return String.valueOf(parameter);
			}
			// 使用Ognl API，通过content中的表达式去获取入参对象中的指定属性值
			// context.getBindings()就是一个ContextMap对象，也是HashMap的子对象
			Object value = OgnlCache.getValue(content, parameter);
			String srtValue = value == null ? "" : String.valueOf(value); // issue #274 return "" instead of "null"
			checkInjection(srtValue);
			return srtValue;
		}

		private void checkInjection(String value) {
			if (injectionFilter != null && !injectionFilter.matcher(value).matches()) {
				throw new ScriptingException("Invalid input. Please conform to regex" + injectionFilter.pattern());
			}
		}
	}

	private static class DynamicCheckerTokenParser implements TokenHandler {

		private boolean isDynamic;

		public DynamicCheckerTokenParser() {
			// Prevent Synthetic Access
		}

		public boolean isDynamic() {
			return isDynamic;
		}

		@Override
		public String handleToken(String content) {
			this.isDynamic = true;
			return null;
		}
	}

}