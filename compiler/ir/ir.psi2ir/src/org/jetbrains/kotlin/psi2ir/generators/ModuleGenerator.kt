/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.psi2ir.generators

import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrModuleFragmentImpl
import org.jetbrains.kotlin.ir.util.ExternalDependenciesGenerator
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.descriptors.findPackageFragmentForFile

class ModuleGenerator(override val context: GeneratorContext) : Generator {

    private val annotationGenerator = TypeTranslator(context.moduleDescriptor, context.symbolTable).annotationGenerator

    fun generateModuleFragment(ktFiles: Collection<KtFile>): IrModuleFragment =
        generateModuleFragmentWithoutDependencies(ktFiles).also { irModule ->
            generateUnboundSymbolsAsDependencies(irModule)
        }

    fun generateModuleFragmentWithoutDependencies(ktFiles: Collection<KtFile>): IrModuleFragment =
        IrModuleFragmentImpl(context.moduleDescriptor, context.irBuiltIns).also { irModule ->
            irModule.files.addAll(generateFiles(ktFiles))
        }

    fun generateUnboundSymbolsAsDependencies(irModule: IrModuleFragment) {
        ExternalDependenciesGenerator(
            irModule.descriptor, context.symbolTable, context.irBuiltIns
        ).generateUnboundSymbolsAsDependencies(irModule)
    }

    private fun generateFiles(ktFiles: Collection<KtFile>): List<IrFile> {
        val irDeclarationGenerator = DeclarationGenerator(context)

        return ktFiles.map { ktFile ->
            generateSingleFile(irDeclarationGenerator, ktFile)
        }
    }

    private fun generateSingleFile(irDeclarationGenerator: DeclarationGenerator, ktFile: KtFile): IrFileImpl {
        val irFile = createEmptyIrFile(ktFile)

        for (ktAnnotationEntry in ktFile.annotationEntries) {
            val annotationDescriptor = getOrFail(BindingContext.ANNOTATION, ktAnnotationEntry)
            irFile.fileAnnotations.add(annotationDescriptor)
            irFile.annotations.add(annotationGenerator.generateAnnotationConstructorCall(annotationDescriptor))
        }

        for (ktDeclaration in ktFile.declarations) {
            irFile.declarations.add(irDeclarationGenerator.generateMemberDeclaration(ktDeclaration))
        }

        return irFile
    }

    private fun createEmptyIrFile(ktFile: KtFile): IrFileImpl {
        val fileEntry = context.sourceManager.getOrCreateFileEntry(ktFile)
        val packageFragmentDescriptor = context.moduleDescriptor.findPackageFragmentForFile(ktFile)!!
        val irFile = IrFileImpl(fileEntry, packageFragmentDescriptor)
        context.sourceManager.putFileEntry(irFile, fileEntry)
        return irFile
    }
}
