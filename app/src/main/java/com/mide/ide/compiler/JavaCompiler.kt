package com.mide.ide.compiler

import com.mide.ide.MIDEApplication
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.compiler.Compiler
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies
import org.eclipse.jdt.internal.compiler.ICompilerRequestor
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
import org.eclipse.jdt.internal.compiler.env.INameEnvironment
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory
import java.io.File
import java.util.Locale

class JavaCompiler {

    data class CompilationResult(
        val success: Boolean,
        val errors: List<BuildError>,
        val warnings: List<BuildError>
    )

    fun compile(
        sourceFiles: List<File>,
        classesOutputDir: File,
        classpath: List<File>,
        onLog: (String) -> Unit
    ): CompilationResult {
        classesOutputDir.mkdirs()

        val errors = mutableListOf<BuildError>()
        val warnings = mutableListOf<BuildError>()

        val compilationUnits = sourceFiles.map { file ->
            object : ICompilationUnit {
                override fun getContents(): CharArray = file.readText().toCharArray()
                override fun getMainTypeName(): CharArray =
                    file.nameWithoutExtension.toCharArray()
                override fun getPackageName(): Array<CharArray> {
                    val content = file.readText()
                    val packageMatch = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
                        .find(content)
                    return packageMatch?.groupValues?.get(1)
                        ?.split(".")
                        ?.map { it.toCharArray() }
                        ?.toTypedArray()
                        ?: emptyArray()
                }
                override fun ignoreOptionalProblems(): Boolean = false
                override fun getFileName(): CharArray = file.absolutePath.toCharArray()
            }
        }

        val nameEnvironment = object : INameEnvironment {
            private val classpathEntries = classpath.map { ZipNameEnvironmentEntry(it) }

            override fun findType(compoundTypeName: Array<CharArray>): NameEnvironmentAnswer? {
                val className = compoundTypeName.joinToString("/") { String(it) }
                return findClass(className)
            }

            override fun findType(
                typeName: CharArray,
                packageName: Array<CharArray>
            ): NameEnvironmentAnswer? {
                val pkg = packageName.joinToString("/") { String(it) }
                val className = if (pkg.isEmpty()) String(typeName) else "$pkg/${String(typeName)}"
                return findClass(className)
            }

            private fun findClass(className: String): NameEnvironmentAnswer? {
                val classFile = File(classesOutputDir, "$className.class")
                if (classFile.exists()) {
                    val reader = ClassFileReader(classFile.readBytes(), classFile.absolutePath.toCharArray(), true)
                    return NameEnvironmentAnswer(reader, null)
                }
                classpathEntries.forEach { entry ->
                    val answer = entry.findClass(className)
                    if (answer != null) return answer
                }
                return null
            }

            override fun isPackage(parentPackageName: Array<CharArray>?, packageName: CharArray): Boolean {
                val pkg = buildString {
                    parentPackageName?.forEach { append(String(it)).append('/') }
                    append(String(packageName))
                }
                return classpathEntries.any { it.isPackage(pkg) }
            }

            override fun cleanup() {
                classpathEntries.forEach { it.close() }
            }
        }

        val policy: IErrorHandlingPolicy = DefaultErrorHandlingPolicies.proceedWithAllProblems()
        val options = CompilerOptions().apply {
            sourceLevel = ClassFileConstants.JDK17
            complianceLevel = ClassFileConstants.JDK17
            targetJDK = ClassFileConstants.JDK17
            generateClassFiles = true
        }

        val requestor = ICompilerRequestor { result ->
            result.classFiles.forEach { classFile ->
                val className = classFile.fileName().joinToString(File.separator) { String(it) }
                val outputFile = File(classesOutputDir, "$className.class")
                outputFile.parentFile?.mkdirs()
                outputFile.writeBytes(classFile.bytes)
            }
            result.problems?.forEach { problem ->
                val severity = if (problem.isError) ErrorSeverity.ERROR else ErrorSeverity.WARNING
                val error = BuildError(
                    file = String(problem.originatingFileName ?: charArrayOf()),
                    line = problem.sourceLineNumber,
                    column = problem.sourceStart,
                    message = problem.message,
                    severity = severity
                )
                if (problem.isError) errors.add(error)
                else warnings.add(error)
                onLog("${if (problem.isError) "ERROR" else "WARNING"}: ${problem.message} at line ${problem.sourceLineNumber}")
            }
        }

        val compiler = Compiler(
            nameEnvironment,
            policy,
            options,
            requestor,
            DefaultProblemFactory(Locale.getDefault())
        )

        compiler.compile(compilationUnits.toTypedArray())
        nameEnvironment.cleanup()

        return CompilationResult(
            success = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    private inner class ZipNameEnvironmentEntry(private val file: File) {
        private val entries = mutableMapOf<String, ByteArray>()

        init {
            if (file.exists() && file.extension == "jar") {
                java.util.zip.ZipFile(file).use { zip ->
                    zip.entries().asSequence().forEach { entry ->
                        entries[entry.name] = zip.getInputStream(entry).readBytes()
                    }
                }
            }
        }

        fun findClass(className: String): NameEnvironmentAnswer? {
            val entry = entries["$className.class"] ?: return null
            val reader = ClassFileReader(entry, "$className.class".toCharArray(), true)
            return NameEnvironmentAnswer(reader, null)
        }

        fun isPackage(packagePath: String): Boolean {
            return entries.keys.any { it.startsWith("$packagePath/") }
        }

        fun close() {}
    }

    private object ClassFileConstants {
        const val JDK17 = 3407872L
    }
}
