/*
 * Morgan Stanley makes this available to you under the Apache License, Version 2.0 (the "License").
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
 * See the NOTICE file distributed with this work for additional information regarding copyright ownership.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ms.silverking.compression;

import java.io.IOException;

public class NullCodec implements Compressor, Decompressor {
  public NullCodec() {}

  public byte[] compress(byte[] rawValue, int offset, int length) throws IOException {
    byte[] b;

    b = new byte[length];
    System.arraycopy(rawValue, offset, b, 0, length);
    return b;
  }

  public byte[] decompress(byte[] value, int offset, int length, int uncompressedLength)
      throws IOException {
    byte[] b;

    if (length != uncompressedLength) {
      throw new RuntimeException("length != uncompressedLength");
    }
    b = new byte[length];
    System.arraycopy(value, offset, b, 0, length);
    return b;
  }
}
