package org.mcpkg.gradle2nix;

import java.io.File;
import java.util.HashSet;
import java.util.ArrayList;

import org.apache.maven.artifact.Artifact as MavenArtifact;
import org.apache.maven.model.Dependency  as MavenDependency;
import org.apache.maven.model.Exclusion   as MavenExclusion;
import org.apache.maven.model.Repository  as MavenRepository;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.DefaultModelBuilderFactory;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.DefaultModelBuildingRequest;

import org.eclipse.aether.artifact.Artifact        as AetherArtifact;
import org.eclipse.aether.artifact.DefaultArtifact as AetherDefaultArtifact;
import org.eclipse.aether.graph.Dependency         as AetherDependency;
import org.eclipse.aether.graph.Exclusion          as AetherExclusion;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

public class DumpPomDeps(pomFile: File) {
    private val model = readModel(pomFile);

    public fun getDependencies(): List<AetherDependency>
        = model.dependencies.map { fromMavenDependency(it) };

    public fun getRepositories(): List<MavenRepository> {
        val result = HashSet<MavenRepository>();
        result.addAll(model.repositories);
        result.addAll(model.pluginRepositories);
        return result.toList();
    }

    private fun fromMavenArtifact(art: MavenArtifact): AetherArtifact {
        return AetherDefaultArtifact(art.groupId, art.artifactId,
                                     art.classifier, art.type, art.version);
    }

    private fun fromMavenDependency(dep: MavenDependency): AetherDependency {
        val artifact   = AetherDefaultArtifact(dep.groupId, dep.artifactId,
                                               dep.type, dep.version);
        val scope      = dep.scope;
        val optional   = dep.optional == "true";
        val exclusions = dep.exclusions.map { fromMavenExclusion(it) };
        return AetherDependency(artifact, scope, optional, exclusions);
    }

    private fun fromMavenExclusion(ex: MavenExclusion)
        = AetherExclusion(ex.groupId, ex.artifactId, null, null);

    private fun readModel(file: File): Model {
        val factory = DefaultModelBuilderFactory();
        val builder = factory.newInstance();
        val req = DefaultModelBuildingRequest();
        req.setProcessPlugins(false);
        req.setPomFile(file);
        req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        return builder.build(req).effectiveModel;
    }

    public fun getDependencies(input: AetherArtifact): ArrayList<AetherArtifact> {
         val booter = DependencyDumperBoot();

         val system = booter.newRS();

         val session = booter.newRSSession(system);

         session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
         session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

         val descRequest = ArtifactDescriptorRequest();
         descRequest.setArtifact(input);
         descRequest.setRepositories(booter.newRepositories(system, session));

         val descResult = system.readArtifactDescriptor(session, descRequest);

         val collectRequest = CollectRequest();
         collectRequest.setRootArtifact(descResult.artifact);
         collectRequest.setDependencies(descResult.dependencies);
         collectRequest.setManagedDependencies(descResult.managedDependencies);
         collectRequest.setRepositories(descRequest.repositories);

         val collectResult = system.collectDependencies(session,
                                                        collectRequest);

         val dumper = ConsoleDependencyGraphDumper();

         collectResult.root.accept(dumper);

         return dumper.depList;
     }
}

private class ConsoleDependencyGraphDumper: DependencyVisitor {
    public val depList = ArrayList<AetherArtifact>();

    override public fun visitEnter(node: DependencyNode): Boolean {
        depList.add(node.artifact);
        return true;
    }

    override public fun visitLeave(node: DependencyNode): Boolean {
        return true;
    }
}

private data class ArtifactDownloadInfo(var url: String = "",
                                        var hash: String = "");
