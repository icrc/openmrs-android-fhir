/*
* BSD 3-Clause License
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice, this
*    list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*
* 3. Neither the name of the copyright holder nor the names of its
*    contributors may be used to endorse or promote products derived from
*    this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
* FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
* DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
* SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
* CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package org.openmrs.android.fhir.forms.diagnosis

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * A processed copy of a list of diagnoses, kept in the private cache of the app so that the next
 * start does not read the CSV again: reading the CSV (quoted fields, one row for each code, names
 * cleaned and sorted) is most of the time a list takes to load, and what is stored here is the
 * result. The CSV stays the only input; this file is never the source of anything and can be
 * deleted at any time (the system may do it): the list is then read from the CSV and written again.
 *
 * The file starts with a [key] that says which CSV it was made from (the build of the app that
 * wrote it), and ends with a marker, so a file written for another build or cut short is ignored.
 */
internal object DiagnosisCatalogCache {
  private const val MAGIC = 0x44584341 // "DXCA"
  private const val FORMAT = 1
  private const val BUFFER_BYTES = 64 * 1024

  /** The diagnoses stored in [file] for [key], or null when there is no usable copy. */
  fun read(file: File, key: String): List<Diagnosis>? {
    if (!file.isFile) return null
    return try {
      DataInputStream(BufferedInputStream(FileInputStream(file), BUFFER_BYTES)).use { input ->
        if (input.readInt() != MAGIC || input.readInt() != FORMAT || input.readUTF() != key) {
          return null
        }
        val count = input.readInt()
        if (count <= 0) return null
        // The same few language tags (fr, es...) come back in every diagnosis: one string each.
        val languages = HashMap<String, String>()
        val diagnoses = ArrayList<Diagnosis>(count)
        repeat(count) {
          val uuid = input.readUTF()
          val name = input.readUTF()
          val codes = List(input.readUnsignedByte()) { input.readUTF() }
          val translated = input.readUnsignedByte()
          val translations =
            if (translated == 0) {
              emptyMap()
            } else {
              HashMap<String, String>(translated * 2).also { map ->
                repeat(translated) {
                  val language = input.readUTF().let { tag -> languages.getOrPut(tag) { tag } }
                  map[language] = input.readUTF()
                }
              }
            }
          diagnoses.add(Diagnosis(uuid, name, codes, translations))
        }
        if (input.readInt() != MAGIC) return null
        diagnoses
      }
    } catch (e: Exception) {
      // A cache that cannot be read is no cache (a cut file, a changed format): the CSV is read.
      null
    }
  }

  /**
   * Stores [diagnoses] in [file]. It is written to another file and renamed, so a reader never sees
   * half of it.
   */
  fun write(file: File, key: String, diagnoses: List<Diagnosis>) {
    val temporary = File(file.parentFile, file.name + ".tmp")
    try {
      DataOutputStream(BufferedOutputStream(FileOutputStream(temporary), BUFFER_BYTES)).use { out ->
        out.writeInt(MAGIC)
        out.writeInt(FORMAT)
        out.writeUTF(key)
        out.writeInt(diagnoses.size)
        diagnoses.forEach { diagnosis ->
          out.writeUTF(diagnosis.uuid)
          out.writeUTF(diagnosis.name)
          out.writeByte(diagnosis.icd.size)
          diagnosis.icd.forEach { out.writeUTF(it) }
          out.writeByte(diagnosis.translations.size)
          diagnosis.translations.forEach { (language, text) ->
            out.writeUTF(language)
            out.writeUTF(text)
          }
        }
        out.writeInt(MAGIC)
      }
      if (!temporary.renameTo(file)) {
        file.delete()
        check(temporary.renameTo(file)) { "The cache of the diagnoses cannot be moved into place" }
      }
    } catch (e: Exception) {
      temporary.delete()
      throw e
    }
  }
}
