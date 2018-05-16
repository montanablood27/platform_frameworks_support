/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.r4a.frames.analysis

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.r4a.frames.FrameRecordClassDescriptor
import org.jetbrains.kotlin.util.slicedMap.BasicWritableSlice
import org.jetbrains.kotlin.util.slicedMap.RewritePolicy
import org.jetbrains.kotlin.util.slicedMap.WritableSlice

object FrameWritableSlices {
    val RECORD_CLASS: WritableSlice<FqName, FrameRecordClassDescriptor> = BasicWritableSlice(RewritePolicy.DO_NOTHING)
    val HOLDER_DESCRIPTOR: WritableSlice<FqName, ClassDescriptor> = BasicWritableSlice(RewritePolicy.DO_NOTHING)
}