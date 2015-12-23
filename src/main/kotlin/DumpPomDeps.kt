package org.mcpkg.gradle2nix;

// FIXME: remove unused dependencies

import java.io.*;
import java.io.PrintStream;
import java.net.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List as JavaList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.artifact.Artifact as MavenArtifact;
import org.apache.maven.artifact.repository.ArtifactRepository as MavenAR;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy as MavenARP;
import org.apache.maven.model.*;
import org.apache.maven.model.Dependency as MavenDependency;
import org.apache.maven.model.Exclusion as MavenExclusion;
import org.apache.maven.model.Repository as MavenR;
import org.apache.maven.model.RepositoryPolicy as MavenRP;
import org.apache.maven.model.building.*;
import org.apache.maven.project.*;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.*;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.*;
import org.eclipse.aether.artifact.Artifact as AetherArtifact;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.*;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.*;
import org.eclipse.aether.graph.Dependency as AetherDependency;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.graph.Exclusion as AetherExclusion;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.internal.impl.*;
import org.eclipse.aether.metadata.*;
import org.eclipse.aether.repository.*;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.layout.*;
import org.eclipse.aether.spi.connector.transport.*;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.*;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.MetadataNotFoundException;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transfer.TransferResource;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.graph.manager.*;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.*;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.mcpkg.gradle2nix.*;

private class ConsoleTransferListener: AbstractTransferListener() {
    override public fun transferInitiated(event: TransferEvent)  {}
    override public fun transferProgressed(event: TransferEvent) {}
    override public fun transferSucceeded(event: TransferEvent)  {}
    override public fun transferFailed(event: TransferEvent)     {}

    override public fun transferCorrupted(event: TransferEvent) {
        event.exception.printStackTrace();
    }
}

private class ConsoleRepositoryListener(): AbstractRepositoryListener() {
    override public fun artifactDeployed(event: RepositoryEvent) {}
    override public fun artifactDeploying(event: RepositoryEvent) {}
    override public fun artifactDescriptorInvalid(event: RepositoryEvent) {}
    override public fun artifactDescriptorMissing(event: RepositoryEvent) {}
    override public fun artifactInstalled(event: RepositoryEvent) {}
    override public fun artifactInstalling(event: RepositoryEvent) {}
    override public fun artifactResolved(event: RepositoryEvent) {}
    override public fun artifactDownloading(event: RepositoryEvent) {}
    override public fun artifactDownloaded(event: RepositoryEvent) {}
    override public fun artifactResolving(event: RepositoryEvent) {}
    override public fun metadataDeployed(event: RepositoryEvent) {}
    override public fun metadataDeploying(event: RepositoryEvent) {}
    override public fun metadataInstalled(event: RepositoryEvent) {}
    override public fun metadataInstalling(event: RepositoryEvent) {}
    override public fun metadataInvalid(event: RepositoryEvent) {}
    override public fun metadataResolved(event: RepositoryEvent) {}
    override public fun metadataResolving(event: RepositoryEvent) {}
}

private class ConsoleDependencyGraphDumper: DependencyVisitor {
    public val depList = ArrayList<Artifact>();

    override public fun visitEnter(node: DependencyNode): Boolean {
        depList.add(node.artifact);
        return true;
    }

    override public fun visitLeave(node: DependencyNode): Boolean {
        return true;
    }
}

private class Booter {
    public fun newRS(): RepositorySystem {
        val locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory::class.java,
                           BasicRepositoryConnectorFactory::class.java);
        locator.addService(TransporterFactory::class.java,
                           FileTransporterFactory::class.java);
        locator.addService(TransporterFactory::class.java,
                           HttpTransporterFactory::class.java);

        locator.setErrorHandler(object: DefaultServiceLocator.ErrorHandler() {
            override public fun serviceCreationFailed(type: Class<*>,
                                                      impl: Class<*>,
                                                      exception: Throwable): Unit {
                exception.printStackTrace();
            }
        });

        return locator.getService(RepositorySystem::class.java);
    }

    public fun newRSSession(system: RepositorySystem): DefaultRepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession();

        val localRepo = LocalRepository("target/local-repo");
        val repoManager = system.newLocalRepositoryManager(session, localRepo);
        session.setLocalRepositoryManager(repoManager);
        session.setTransferListener(ConsoleTransferListener());
        session.setRepositoryListener(ConsoleRepositoryListener());

        return session;
    }

    public fun newRepositories(system:  RepositorySystem,
                               session: RepositorySystemSession): ArrayList<RemoteRepository> {
        return ArrayList<RemoteRepository>(Arrays.asList(newCentralRepository()));
    }

    private fun newCentralRepository(): RemoteRepository {
        val mavenCentralURL = "http://central.maven.org/maven2/";
        val builder = RemoteRepository.Builder("central", "default",
                                               mavenCentralURL);
        return builder.build();
    }
}

data class ArtifactDownloadInfo(var url: String = "", var hash: String = "");

class DumpPomDeps(pomFile: File) {
    private val model = readModel(pomFile);

    public fun getDependencies(): List<AetherDependency>
        = model.dependencies.map { fromMavenDependency(it) };

    public fun getRepositories(): List<Repository> {
        val result = HashSet<Repository>();
        result.addAll(model.repositories);
        result.addAll(model.pluginRepositories);
        return result.toList();
    }

    private fun fromMavenArtifact(art: MavenArtifact): AetherArtifact {
        return DefaultArtifact(art.groupId, art.artifactId,
                               art.classifier, art.type, art.version);
    }

    private fun fromMavenDependency(dep: MavenDependency): AetherDependency {
        val artifact   = DefaultArtifact(dep.groupId, dep.artifactId,
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

    public fun getDependencies(input: Artifact): ArrayList<Artifact> {
         val system = Booter().newRS();

         val session = Booter().newRSSession(system);

         session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
         session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

         val descRequest = ArtifactDescriptorRequest();
         descRequest.setArtifact(input);
         descRequest.setRepositories(Booter().newRepositories(system, session));

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

