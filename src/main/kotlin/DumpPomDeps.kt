package org.mcpkg.gradle2nix;

import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.ModelBuilder
import org.apache.maven.model.building.ModelBuildingRequest
import org.mcpkg.gradle2nix.FileRoot
import org.mcpkg.gradle2nix.FileRootNative
import org.mcpkg.gradle2nix.FileWrapper
import java.io.File

class DumpPomDeps {
    fun doDump(inputFile: File) {
        val factory = DefaultModelBuilderFactory();
        val builder = factory.newInstance();
        val req = DefaultModelBuildingRequest();
        req.setProcessPlugins(false);
        req.setPomFile(inputFile);
        req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        val model = builder.build(req).effectiveModel;
        for(d in model.dependencies) {
            println("everything: ${d}");
        }
        println("test: ${model}");
    }
}
