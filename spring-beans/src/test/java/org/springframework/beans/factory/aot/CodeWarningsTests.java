/*
 * Copyright 2002-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.aot;

import java.util.function.Consumer;

import javax.lang.model.element.Modifier;

import org.junit.jupiter.api.Test;

import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.beans.testfixture.beans.factory.aot.DeferredTypeBuilder;
import org.springframework.core.test.tools.Compiled;
import org.springframework.core.test.tools.TestCompiler;
import org.springframework.javapoet.MethodSpec;
import org.springframework.javapoet.MethodSpec.Builder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CodeWarnings}.
 *
 * @author Stephane Nicoll
 */
class CodeWarningsTests {

	private static final TestCompiler TEST_COMPILER = TestCompiler.forSystem()
			.withCompilerOptions("-Xlint:all", "-Werror");

	private final CodeWarnings codeWarnings;

	private final TestGenerationContext generationContext;

	CodeWarningsTests() {
		this.codeWarnings = new CodeWarnings();
		this.generationContext = new TestGenerationContext();
	}

	@Test
	void registerNoWarningDoesNotIncludeAnnotation() {
		compile(method -> {
			this.codeWarnings.suppress(method);
			method.addStatement("$T bean = $S", String.class, "Hello");
		}, compiled -> assertThat(compiled.getSourceFile()).doesNotContain("@SuppressWarnings"));
	}

	@Test
	@SuppressWarnings("deprecation")
	void registerWarningSuppressesIt() {
		Class<?> deprecatedBeanClass = org.springframework.beans.testfixture.beans.factory.generator.deprecation.DeprecatedBean.class;
		this.codeWarnings.register("deprecation");
		compile(method -> {
			this.codeWarnings.suppress(method);
			method.addStatement("$T bean = new $T()", deprecatedBeanClass, deprecatedBeanClass);
		}, compiled -> assertThat(compiled.getSourceFile())
				.contains("@SuppressWarnings(\"deprecation\")"));
	}

	@Test
	@SuppressWarnings({ "deprecation", "removal" })
	void registerSeveralWarningsSuppressesThem() {
		Class<?> deprecatedBeanClass = org.springframework.beans.testfixture.beans.factory.generator.deprecation.DeprecatedBean.class;
		Class<?> deprecatedForRemovalBeanClass = org.springframework.beans.testfixture.beans.factory.generator.deprecation.DeprecatedForRemovalBean.class;
		this.codeWarnings.register("deprecation");
		this.codeWarnings.register("removal");
		compile(method -> {
			this.codeWarnings.suppress(method);
			method.addStatement("$T bean = new $T()", deprecatedBeanClass, deprecatedBeanClass);
			method.addStatement("$T another = new $T()", deprecatedForRemovalBeanClass, deprecatedForRemovalBeanClass);
		}, compiled -> assertThat(compiled.getSourceFile())
				.contains("@SuppressWarnings({ \"deprecation\", \"removal\" })"));
	}

	@Test
	@SuppressWarnings("deprecation")
	void detectDeprecationOnAnnotatedElementWithDeprecated() {
		Class<?> deprecatedBeanClass = org.springframework.beans.testfixture.beans.factory.generator.deprecation.DeprecatedBean.class;
		this.codeWarnings.detectDeprecation(deprecatedBeanClass);
		assertThat(this.codeWarnings.getWarnings()).containsExactly("deprecation");
	}

	@Test
	@SuppressWarnings("removal")
	void detectDeprecationOnAnnotatedElementWithDeprecatedForRemoval() {
		Class<?> deprecatedForRemovalBeanClass = org.springframework.beans.testfixture.beans.factory.generator.deprecation.DeprecatedForRemovalBean.class;
		this.codeWarnings.detectDeprecation(deprecatedForRemovalBeanClass);
		assertThat(this.codeWarnings.getWarnings()).containsExactly("removal");
	}

	@Test
	void toStringIncludeWarnings() {
		this.codeWarnings.register("deprecation");
		this.codeWarnings.register("rawtypes");
		assertThat(this.codeWarnings).hasToString("CodeWarnings[deprecation, rawtypes]");
	}

	private void compile(Consumer<Builder> method, Consumer<Compiled> result) {
		DeferredTypeBuilder typeBuilder = new DeferredTypeBuilder();
		this.generationContext.getGeneratedClasses().addForFeature("TestCode", typeBuilder);
		typeBuilder.set(type -> {
			type.addModifiers(Modifier.PUBLIC);
			Builder methodBuilder = MethodSpec.methodBuilder("apply")
					.addModifiers(Modifier.PUBLIC);
			method.accept(methodBuilder);
			type.addMethod(methodBuilder.build());
		});
		this.generationContext.writeGeneratedContent();
		TEST_COMPILER.with(this.generationContext).compile(result);
	}

}
