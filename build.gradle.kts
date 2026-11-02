
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Forge strips @SideOnly(Side.CLIENT) code from a dedicated server, so server code that touches it works in
// singleplayer and then crashes the server; 1.0.0 shipped one in cleanMessage. This fails the build instead.
// The convention plugin applies the Java plugin after this script runs.
pluginManager.withPlugin("java") {
    val main = project.the<SourceSetContainer>()["main"]
    val java25 = project.extensions.getByType<JavaToolchainService>()
        .launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    val script = file("tools/SideCheck.java")
    val classes = main.output.classesDirs
    val classpath = main.compileClasspath
    val sideCheck = tasks.register<Exec>("sideCheck") {
        group = "verification"
        description = "Fails on server code using client-only classes or members, and client code using server-only ones."
        inputs.file(script)
        inputs.files(classes, classpath)
        dependsOn(tasks.named("classes"))
        executable = java25.get().executablePath.asFile.absolutePath
        argumentProviders.add(CommandLineArgumentProvider {
            listOf(
                script.absolutePath,
                classpath.asPath,
                classes.asPath,
                "com/enn3developer/gtblocker/client/",
                "com/enn3developer/gtblocker/ClientProxy")
        })
    }
    tasks.named("check") { dependsOn(sideCheck) }
}
