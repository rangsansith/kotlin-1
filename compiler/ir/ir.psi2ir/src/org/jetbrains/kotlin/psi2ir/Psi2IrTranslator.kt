/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi2ir

import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.AnnotationGenerator
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.generators.GeneratorContext
import org.jetbrains.kotlin.psi2ir.generators.ModuleGenerator
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.psi2ir.transformations.insertImplicitCasts
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.utils.SmartList

class Psi2IrTranslator(val configuration: Psi2IrConfiguration = Psi2IrConfiguration()) {
    interface PostprocessingStep {
        fun postprocess(context: GeneratorContext, irElement: IrElement)
    }

    private val postprocessingSteps = SmartList<PostprocessingStep>()

    fun add(step: PostprocessingStep) {
        postprocessingSteps.add(step)
    }

    fun generateModule(moduleDescriptor: ModuleDescriptor, ktFiles: Collection<KtFile>, bindingContext: BindingContext): IrModuleFragment {
        val context = createGeneratorContext(moduleDescriptor, bindingContext)
        return generateModuleFragment(context, ktFiles)
    }

    fun createGeneratorContext(moduleDescriptor: ModuleDescriptor, bindingContext: BindingContext) =
        GeneratorContext(configuration, moduleDescriptor, bindingContext)

    fun generateModuleFragment(context: GeneratorContext, ktFiles: Collection<KtFile>): IrModuleFragment {
        val moduleGenerator = ModuleGenerator(context)
        val irModule = moduleGenerator.generateModuleFragmentWithoutDependencies(ktFiles)
        postprocess(context, irModule)
        moduleGenerator.generateUnboundSymbolsAsDependencies(irModule)
        return irModule
    }

    private fun postprocess(context: GeneratorContext, irElement: IrElement) {
        insertImplicitCasts(irElement, context)
        generateAnnotationsForDeclarations(context, irElement)

        postprocessingSteps.forEach { it.postprocess(context, irElement) }

        irElement.patchDeclarationParents()
    }

    private fun generateAnnotationsForDeclarations(context: GeneratorContext, irElement: IrElement) {
        val annotationGenerator = TypeTranslator(context.moduleDescriptor, context.symbolTable).annotationGenerator
        irElement.acceptVoid(annotationGenerator)
    }
}
