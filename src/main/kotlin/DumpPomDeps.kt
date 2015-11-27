package org.mcpkg.gradle2nix;

import org.apache.maven.model.*;
import org.apache.maven.model.building.*;
import org.mcpkg.gradle2nix.*;
import java.io.*;
import kotlin.*;
import java.util.*;

class DumpPomDeps(pomFile: File) {
    val model = readModel(pomFile);

    public fun getDependencies(): List<Dependency> {
        return model.dependencies;
    }

    public fun getRepositories(): List<Repository> {
        val result = ArrayList<Repository>();
        result.addAll(model.getRepositories());
        result.addAll(model.getPluginRepositories());
        return result.toList();
    }

    private fun readModel(file: File): Model {
        val factory = DefaultModelBuilderFactory();
        val builder = factory.newInstance();
        val req = DefaultModelBuildingRequest();
        req.setProcessPlugins(false);
        req.setPomFile(file);
        req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        return builder.build(req).effectiveModel;
    }
}
