/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.data

import org.andstatus.app.context.MyStorage
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import java.io.File
import java.util.*

class DownloadFile(filename: String?) : IsEmpty {
    private val filename: String?
    private val file: File? = null

    /** Existence is checked at the moment of the object creation  */
    val existed = false
    override fun isEmpty(): Boolean {
        return file == null
    }

    fun existsNow(): Boolean {
        return nonEmpty() && file.exists() && file.isFile()
    }

    fun getFile(): File? {
        return file
    }

    fun getFilePath(): String {
        return if (file == null) "" else file.absolutePath
    }

    fun getSize(): Long {
        return if (existsNow()) file.length() else 0
    }

    fun getFilename(): String? {
        return filename
    }

    /** returns true if the file existed and was deleted  */
    fun delete(): Boolean {
        return deleteFileLogged(file)
    }

    private fun deleteFileLogged(file: File?): Boolean {
        var deleted = false
        if (existsNow()) {
            deleted = file.delete()
            if (deleted) {
                MyLog.v(this) { "Deleted file $file" }
            } else {
                MyLog.e(this, "Couldn't delete file $file")
            }
        }
        return deleted
    }

    override fun toString(): String {
        return (MyStringBuilder.Companion.objToTag(this)
                + " filename:" + filename
                + if (existed) ", existed" else "")
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + filename.hashCode()
        return result
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val other = o as DownloadFile?
        return filename == other.filename
    }

    companion object {
        val EMPTY: DownloadFile? = DownloadFile("")
    }

    init {
        Objects.requireNonNull(filename)
        this.filename = filename
        if (filename.isNullOrEmpty()) {
            file = null
            existed = false
        } else {
            file = MyStorage.newMediaFile(filename)
            existed = existsNow()
        }
    }
}