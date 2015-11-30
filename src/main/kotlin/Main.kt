package org.mcpkg.gradle2nix;

import java.io.File;
import org.mcpkg.gradle2nix.DumpPomDeps;

fun main(args: Array<String>) {
    val inputFile = File(args[0]);
    val dumper = DumpPomDeps(inputFile);
    for(d in dumper.getDependencies()) {
        println("dependency: ${d}");
    }
    for(r in dumper.getRepositories()) {
        println("repository: ${r}");
    }
    dumper.exampleCode();
}


