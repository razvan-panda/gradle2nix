package org.mcpkg.gradle2nix;

import java.util.ArrayList;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

public class ResolveDependencies {
    val booter = DependencyDumperBoot();
    val system = booter.newRS();
    val session = booter.newRSSession(system);
    
    public fun getDependencies(input: Artifact): ArrayList<Artifact> {
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

         val visitor = ResolveDependenciesVisitor();

         collectResult.root.accept(visitor);

         return visitor.depList;
     }
}

private class ResolveDependenciesVisitor: DependencyVisitor {
    public val depList = ArrayList<Artifact>();

    override public fun visitEnter(node: DependencyNode): Boolean {
        depList.add(node.artifact);
        return true;
    }

    override public fun visitLeave(node: DependencyNode): Boolean = true;
}
