package com.termux.shared.file.filesystem

import android.system.Os
import androidx.annotation.NonNull
import com.termux.shared.logger.Logger
import java.io.File

object FileTypes {

    /** Flags to represent regular, directory and symlink file types defined by [FileType] */
    @JvmField
    val FILE_TYPE_NORMAL_FLAGS = FileType.REGULAR.value or FileType.DIRECTORY.value or FileType.SYMLINK.value

    /** Flags to represent any file type defined by [FileType] */
    @JvmField
    val FILE_TYPE_ANY_FLAGS = Int.MAX_VALUE // 1111111111111111111111111111111 (31 1's)

    /**
     * Convert file type flags to a comma-separated string of file type names.
     *
     * @param fileTypeFlags The file type flags to convert.
     * @return Returns the comma-separated string of file type names.
     */
    fun convertFileTypeFlagsToNamesString(fileTypeFlags: Int): String {
        val fileTypeFlagsStringBuilder = StringBuilder()

        val fileTypes = arrayOf(
            FileType.REGULAR,
            FileType.DIRECTORY,
            FileType.SYMLINK,
            FileType.CHARACTER,
            FileType.FIFO,
            FileType.BLOCK,
            FileType.UNKNOWN
        )
        for (fileType in fileTypes) {
            if ((fileTypeFlags and fileType.value) > 0)
                fileTypeFlagsStringBuilder.append(fileType.name).append(",")
        }

        var fileTypeFlagsString = fileTypeFlagsStringBuilder.toString()

        if (fileTypeFlagsString.endsWith(","))
            fileTypeFlagsString = fileTypeFlagsString.substring(0, fileTypeFlagsString.lastIndexOf(","))

        return fileTypeFlagsString
    }

    /**
     * Checks the type of file that exists at `filePath`.
     *
     * Returns:
     * - [FileType.NO_EXIST] if `filePath` is `null`, empty, an exception is raised
     *      or no file exists at `filePath`.
     * - [FileType.REGULAR] if file at `filePath` is a regular file.
     * - [FileType.DIRECTORY] if file at `filePath` is a directory file.
     * - [FileType.SYMLINK] if file at `filePath` is a symlink file and `followLinks` is `false`.
     * - [FileType.CHARACTER] if file at `filePath` is a character special file.
     * - [FileType.FIFO] if file at `filePath` is a fifo special file.
     * - [FileType.BLOCK] if file at `filePath` is a block special file.
     * - [FileType.UNKNOWN] if file at `filePath` is of unknown type.
     *
     * The `File.isFile()` and `File.isDirectory()` uses `Os.stat(String)` system
     * call (not `Os.lstat(String)`) to check file type and does follow symlinks.
     *
     * The `File.exists()` uses `Os.access(String, int)` system call to check if file is
     * accessible and does not follow symlinks. However, it returns `false` for dangling symlinks,
     * on android at least. Check https://stackoverflow.com/a/57747064/14686958
     *
     * Basically `File` API is not reliable to check for symlinks.
     *
     * So we get the file type directly with `Os.lstat(String)` if `followLinks` is
     * `false` and `Os.stat(String)` if `followLinks` is `true`. All exceptions
     * are assumed as non-existence.
     *
     * The `org.apache.commons.io.FileUtils.isSymlink(File)` can also be used for checking
     * symlinks but `FileAttributes` will provide access to more attributes if necessary,
     * including getting other special file types considering that `File.exists()` can't be
     * used to reliably check for non-existence and exclude the other 3 file types. commons.io is
     * also not compatible with android < 8 for many things.
     *
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/java/io/File.java;l=793
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/java/io/UnixFileSystem.java;l=248
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/native/UnixFileSystem_md.c;l=121
     * https://cs.android.com/android/_/android/platform/libcore/+/001ac51d61ad7443ba518bf2cf7e086efe698c6d
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/luni/src/main/java/libcore/io/Os.java;l=51
     * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/luni/src/main/java/libcore/io/Libcore.java;l=45
     * https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/app/ActivityThread.java;l=7530
     *
     * @param filePath The `path` for file to check.
     * @param followLinks The `boolean` that decides if symlinks will be followed while
     *                       finding type. If set to `true`, then type of symlink target will
     *                       be returned if file at `filePath` is a symlink. If set to
     *                       `false`, then type of file at `filePath` itself will be
     *                       returned.
     * @return Returns the [FileType] of file.
     */
    @JvmStatic
    @NonNull
    fun getFileType(filePath: String?, followLinks: Boolean): FileType {
        if (filePath == null || filePath.isEmpty()) return FileType.NO_EXIST

        try {
            val fileAttributes = FileAttributes.get(filePath, followLinks)
            return getFileType(fileAttributes)
        } catch (e: Exception) {
            // If not a ENOENT (No such file or directory) exception
            val message = e.message
            if (message != null && !message.contains("ENOENT"))
                Logger.logError("Failed to get file type for file at path \"$filePath\": $message")
            return FileType.NO_EXIST
        }
    }

    fun getFileType(@NonNull fileAttributes: FileAttributes): FileType {
        return when {
            fileAttributes.isRegularFile() -> FileType.REGULAR
            fileAttributes.isDirectory() -> FileType.DIRECTORY
            fileAttributes.isSymbolicLink() -> FileType.SYMLINK
            fileAttributes.isSocket() -> FileType.SOCKET
            fileAttributes.isCharacter() -> FileType.CHARACTER
            fileAttributes.isFifo() -> FileType.FIFO
            fileAttributes.isBlock() -> FileType.BLOCK
            else -> FileType.UNKNOWN
        }
    }
}
