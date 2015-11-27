package org.mcpkg.gradle2nix;

import java.io.File;
import org.mcpkg.gradle2nix.DumpPomDeps;

fun main(args: Array<String>) {
    val inputFile = File(args[0]);
    val dumper = DumpPomDeps();
    dumper.doDump(inputFile);
}
