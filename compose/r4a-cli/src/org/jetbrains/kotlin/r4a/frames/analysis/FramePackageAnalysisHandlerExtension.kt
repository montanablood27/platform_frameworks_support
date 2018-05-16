/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.r4a.frames.analysis

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.r4a.frames.*
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.descriptorUtil.isSubclassOf
import org.jetbrains.kotlin.resolve.jvm.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.ResolveSession

class PackageAnalysisHandlerExtension : AnalysisHandlerExtension {
    companion object {
        fun doAnalysis(module: ModuleDescriptor, bindingTrace: BindingTrace, files: Collection<KtFile>, resolveSession: ResolveSession) {
            val componentDescriptor = module.findTopLevel(componentClassName) as? ClassDescriptor ?: error("Could not find the Compoennt descriptor")
            for (file in files) {
                for (declaration in file.declarations) {
                    val ktClass = declaration as? KtClass ?: continue
                    val holderDescriptor = resolveSession.resolveToDescriptor(declaration) as? ClassDescriptor ?: continue
                    if (!holderDescriptor.isSubclassOf(componentDescriptor)) continue
                    val classFqName = ktClass.fqName!!
                    val recordFqName = classFqName.parent().child(Name.identifier("${classFqName.shortName()}\$Record"))
                    val recordSimpleName = recordFqName.shortName()
                    val recordPackage = SyntheticFramePackageDescriptor(module, recordFqName.parent())
                    val baseTypeDescriptor = module.findTopLevel(abstractRecordClassName)
                    val recordDescriptor = module.findTopLevel(recordClassName)
                    val baseType = baseTypeDescriptor.defaultType
                    val frameClass = FrameRecordClassDescriptor(recordSimpleName, recordPackage, recordDescriptor,
                            holderDescriptor, listOf(baseType))

                    recordPackage.setClassDescriptor(frameClass)
                    bindingTrace.record(FrameWritableSlices.RECORD_CLASS, classFqName, frameClass)
                    bindingTrace.record(FrameWritableSlices.HOLDER_DESCRIPTOR, classFqName, holderDescriptor)
                }
            }
        }
    }

    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider
    ): AnalysisResult? {
        val resolveSession = componentProvider.get<ResolveSession>()
        doAnalysis(module, bindingTrace, files, resolveSession)
        return null
    }
}
