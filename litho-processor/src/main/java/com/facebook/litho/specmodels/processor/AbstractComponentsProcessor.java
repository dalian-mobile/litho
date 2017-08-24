/*
 * Copyright (c) 2017-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.litho.specmodels.processor;

import static com.facebook.litho.specmodels.processor.ProcessorUtils.validate;

import com.facebook.litho.specmodels.model.DependencyInjectionHelper;
import com.facebook.litho.specmodels.model.SpecModel;
import com.squareup.javapoet.JavaFile;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

@SupportedSourceVersion(SourceVersion.RELEASE_7)
public abstract class AbstractComponentsProcessor extends AbstractProcessor {

  private final List<SpecModelFactory> mSpecModelFactories;

  protected AbstractComponentsProcessor(
      List<SpecModelFactory> specModelFactories) {
    mSpecModelFactories = specModelFactories;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      return false;
    }

    for (SpecModelFactory specModelFactory : mSpecModelFactories) {
      final Set<Element> elements = specModelFactory.extract(roundEnv);

      for (Element element : elements) {
        try {
          final DependencyInjectionHelper dependencyInjectionHelper =
              getDependencyInjectionGenerator((TypeElement) element);
          final SpecModel specModel =
              specModelFactory.create(
                  processingEnv.getElementUtils(),
                  (TypeElement) element,
                  dependencyInjectionHelper);

          validate(specModel);
          generate(specModel);
        } catch (PrintableException e) {
          e.print(processingEnv.getMessager());
        } catch (Exception e) {
          processingEnv
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.ERROR,
                  "Unexpected error thrown when generating this component spec. "
                      + "Please report stack trace to the components team.",
                  element);
          e.printStackTrace();
        }
      }
    }

    return false;
  }

  protected abstract DependencyInjectionHelper getDependencyInjectionGenerator(
      TypeElement typeElement);

  protected void generate(SpecModel specModel) throws IOException {
    JavaFile.builder(
        getPackageName(specModel.getComponentTypeName().toString()), specModel.generate())
        .skipJavaLangImports(true)
        .build()
        .writeTo(processingEnv.getFiler());
  }

  protected static String getPackageName(String qualifiedName) {
    return qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
  }
}
