package dev.kbroom.fluent.gradle

import dev.kbroom.fluent.codegen.model.FluentLayout
import dev.kbroom.fluent.codegen.model.ScaffoldMode
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * `fluent { ... }` extension for consumer projects.
 */
abstract class FluentExtension @Inject constructor(objects: ObjectFactory) {
    /** Roots containing the FTL locale tree. */
    abstract val sourceDirs: ConfigurableFileCollection

    /** Path layout under each source dir. */
    abstract val layout: Property<FluentLayout>

    /** Reference locale for codegen, validation, and scaffolding. */
    abstract val defaultLocale: Property<String>

    /** Package for generated Kotlin sources. */
    abstract val packageName: Property<String>

    abstract val generateTypedAccessors: Property<Boolean>
    abstract val generateIdConstants: Property<Boolean>
    abstract val generateResourceIds: Property<Boolean>
    abstract val generateL10n: Property<Boolean>
    abstract val generateKdoc: Property<Boolean>
    abstract val generateComposeAccessors: Property<Boolean>
    abstract val strictJunk: Property<Boolean>
    abstract val checkMissingTranslations: Property<Boolean>
    abstract val checkArgParity: Property<Boolean>

    /** Directory for generated Kotlin (defaults to `build/generated/fluent/kotlin`). */
    abstract val outputDir: DirectoryProperty

    val scaffold: FluentScaffoldExtension = objects.newInstance(FluentScaffoldExtension::class.java)

    fun scaffold(action: Action<FluentScaffoldExtension>) {
        action.execute(scaffold)
    }

    init {
        layout.convention(FluentLayout.LocaleBundle)
        defaultLocale.convention("en-US")
        packageName.convention("fluent.generated")
        generateTypedAccessors.convention(true)
        generateIdConstants.convention(true)
        generateResourceIds.convention(true)
        generateL10n.convention(true)
        generateKdoc.convention(true)
        generateComposeAccessors.convention(false)
        strictJunk.convention(true)
        checkMissingTranslations.convention(true)
        checkArgParity.convention(true)
    }
}

@Suppress("AbstractClassCanBeInterface")
abstract class FluentScaffoldExtension {
    abstract val mode: Property<ScaffoldMode>
    abstract val overwrite: Property<Boolean>

    /** Target locale for `fluentScaffoldLocale` (also overridable via `-Pfluent.scaffold.to=`). */
    abstract val to: Property<String>

    init {
        mode.convention(ScaffoldMode.CopyAsPlaceholder)
        overwrite.convention(false)
    }
}
