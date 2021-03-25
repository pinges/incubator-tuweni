/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.tuweni.bytes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkElementIndex;

import java.nio.ByteBuffer;
import java.util.Arrays;

class ByteBufferWrappingBytes32 extends ByteBufferWrappingBytes implements Bytes32 {

  ByteBufferWrappingBytes32(ByteBuffer byteBuffer) {
    this(byteBuffer, 0, byteBuffer.limit());
  }

  ByteBufferWrappingBytes32(ByteBuffer byteBuffer, int offset, int length) {
    super(byteBuffer, offset, length);
    checkArgument(length == SIZE, "Expected %s bytes but got %s", SIZE, length);
  }

  // MUST be overridden by mutable implementations
  @Override
  public Bytes32 copy() {
    if (offset == 0 && length == byteBuffer.limit()) {
      return this;
    }
    return new ArrayWrappingBytes32(toArray());
  }

  @Override
  public MutableBytes32 mutableCopy() {
    return new MutableArrayWrappingBytes32(toArray());
  }
}
