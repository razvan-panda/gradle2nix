package org.mcpkg.gradle2nix;

import java.io.File;
import org.mcpkg.gradle2nix.ReadPOM;
import org.eclipse.aether.artifact.DefaultArtifact;

fun main(args: Array<String>) {
    val inputFile = File(args[0]);
    val artifact = DefaultArtifact("org.apache.maven:maven-aether-provider:3.1.0");
    println(ResolveDependencies().getDependencies(artifact));
}