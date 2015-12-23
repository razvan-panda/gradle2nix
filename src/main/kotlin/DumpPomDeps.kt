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

public class ConsoleTransferListener(outStream: PrintStream = System.out): AbstractTransferListener() {
    private val out = outStream;

    private val downloads = ConcurrentHashMap<TransferResource, Long>();

    private var lastLength: Int = 0;

    override public fun transferInitiated(event: TransferEvent) {
        val message = if(event.requestType == TransferEvent.RequestType.PUT)
                        "Uploading"
                      else
                        "Downloading";
        out.print("${message}: ${event.resource.repositoryUrl}");
        out.println("${event.resource.resourceName}");
    }

    override public fun transferProgressed(event: TransferEvent) {
        val resource = event.resource;
        downloads.put(resource, event.transferredBytes.toLong());

        val buffer = StringBuilder(64);

        for(entry in downloads.entrySet()) {
            val total = entry.key.contentLength;
            val complete = entry.value.longValue();
            buffer.append("${getStatus(complete, total)}  ");
        }

        var pad = lastLength - buffer.length();
        lastLength = buffer.length();
        pad = pad(buffer, pad);
        buffer.append('\r');

        out.print(buffer);
    }

    private fun getStatus(complete: Long, total: Long): String {
        if(total >= 1024) {
            return "${toKB(complete)}/${toKB(total)} KB ";
        } else if(total >= 0) {
            return "${complete}/${total} B ";
        } else if(complete >= 1024) {
            return "${toKB(complete)} KB ";
        } else {
            return "${complete} B ";
        }
    }

    private fun pad(buffer: StringBuilder, spaces: Int): Int {
        return spaces;
    }

    override public fun transferSucceeded(event: TransferEvent) {
        transferCompleted(event);

        val resource = event.resource;
        val contentLength = event.transferredBytes;
        if(contentLength >= 0) {
            val type = if(event.requestType == TransferEvent.RequestType.PUT)
                         "Uploaded"
                       else
                         "Downloaded";
            val len  = if(contentLength >= 1024)
                         "${toKB(contentLength)} KB"
                       else
                         "${contentLength} B";

            var throughput = "";
            val duration = System.currentTimeMillis()
                         - resource.transferStartTime;
            if(duration > 0) {
                val bytes = contentLength - resource.resumeOffset;
                val format = DecimalFormat("0.0", DecimalFormatSymbols(Locale.ENGLISH));
                val kbPerSec = (bytes / 1024.0) / (duration / 1000.0);
                throughput = " at ${format.format(kbPerSec)} KB/sec";
            }

            out.println("${type}: ${resource.repositoryUrl}${resource.resourceName} (${len}${throughput})");
        }
    }

    override public fun transferFailed(event: TransferEvent) {
        transferCompleted(event);

        if(event.exception !is MetadataNotFoundException) {
            event.exception.printStackTrace(out);
        }
    }

    private fun transferCompleted(event: TransferEvent) {
        downloads.remove(event.resource);
        val buffer = StringBuilder(64);
        lastLength = pad(buffer, lastLength);
        buffer.append('\r');
        out.print(buffer);
    }

    override public fun transferCorrupted(event: TransferEvent) {
        event.exception.printStackTrace(out);
    }

    protected fun toKB(bytes: Long): Long {
        return (bytes + 1023) / 1024;
    }
}

private class ConsoleRepositoryListener(outStream: PrintStream = System.out): AbstractRepositoryListener() {
    private val out = outStream;

    private fun printArtifactMessage(event:       RepositoryEvent
                                     verb:        String,
                                     preposition: String,
                                     remainder:   String) {
        out.println("${verb} ${event.artifact} ${preposition} ${remainder}");
    }

    override public fun artifactDeployed(event: RepositoryEvent) {
        printArtifactMessage(event,
                             "Deployed", "to",
                             "${event.repository}");
    }

    override public fun artifactDeploying(event: RepositoryEvent) {
        printArtifactMessage(event,
                             "Deploying", "to",
                             "${event.repository}");
    }

    override public fun artifactDescriptorInvalid(event: RepositoryEvent) {
        printArtifactMessage(event,
                             "Invalid artifact descriptor for", "--",
                             "${event.exception.message}");
    }

    override public fun artifactDescriptorMissing(event: RepositoryEvent) {
        printArtifactMessage(event, "Missing artifact descriptor for", "", "");
    }

    override public fun artifactInstalled(event: RepositoryEvent) {
        printArtifactMessage(event, "Installed", "to", "${event.file}");
    }

    override public fun artifactInstalling(event: RepositoryEvent) {
        printArtifactMessage(event, "Installing", "to", "${event.file}");
    }

    override public fun artifactResolved(event: RepositoryEvent) {
        out.println("Resolved artifact ${event.artifact} from ${event.repository}");
    }

    override public fun artifactDownloading(event: RepositoryEvent) {
        out.println("Downloading artifact ${event.artifact} from ${event.repository}");
    }

    override public fun artifactDownloaded(event: RepositoryEvent) {
        out.println("Downloaded artifact ${event.artifact} from ${event.repository}");
    }

    override public fun artifactResolving(event: RepositoryEvent) {
        out.println("Resolving artifact ${event.artifact}");
    }

    override public fun metadataDeployed(event: RepositoryEvent) {
        out.println("Deployed ${event.metadata} to ${event.repository}");
    }

    override public fun metadataDeploying(event: RepositoryEvent) {
        out.println("Deploying ${event.metadata} to ${event.repository}");
    }

    override public fun metadataInstalled(event: RepositoryEvent) {
        out.println("Installed ${event.metadata} to ${event.file}");
    }

    override public fun metadataInstalling(event: RepositoryEvent) {
        out.println("Installing ${event.metadata} to ${event.file}");
    }

    override public fun metadataInvalid(event: RepositoryEvent) {
        out.println("Invalid metadata ${event.metadata}");
    }

    override public fun metadataResolved(event: RepositoryEvent) {
        out.println("Resolved metadata ${event.metadata} from ${event.repository}");
    }

    override public fun metadataResolving(event: RepositoryEvent) {
        out.println("Resolving metadata ${event.metadata} from ${event.repository}");
    }
}

private class ChildInfo(initialCount: Int) {
    private val count = initialCount;

    public var index: Int = 0;

    public fun formatIndentation(end: Boolean): String {
        val last = (index + 1) >= count;
        if(end) { return (if(last) "\\- " else "+- "); }
        return (if(last) "   " else "|  ");
    }
}

private class ConsoleDependencyGraphDumper(outStream: PrintStream = System.out): DependencyVisitor {
    private val out = outStream;
    private val childInfos = ArrayList<ChildInfo>();

    override public fun visitEnter(node: DependencyNode): Boolean {
        out.println(formatIndentation() + formatNode(node));
        childInfos.add(ChildInfo(node.children.size()));
        return true;
    }

    private fun formatIndentation(): String {
        val buffer = StringBuilder(128);
        val iter = childInfos.iterator();
        while(iter.hasNext()) {
            buffer.append(iter.next().formatIndentation(!iter.hasNext()));
        }
        return buffer.toString();
    }

    private fun formatNode(node: DependencyNode): String {
        val buffer = StringBuilder(128);
        val a = node.artifact;
        val d = node.dependency;
        buffer.append(a);
        if(d != null && d.scope.length() > 0) {
            val scopeStr    = "${d.scope}";
            val optionalStr = (if(d.optional) ", optional" else "");
            buffer.append(" [${scopeStr}${optionalStr}]");
            val pmVersion = DependencyManagerUtils.getPremanagedVersion(node);
            if(pmVersion != null && pmVersion != a.baseVersion) {
                buffer.append(" (version managed from ${pmVersion})")
            }
            val pmScope = DependencyManagerUtils.getPremanagedScope(node);
            if(pmScope != null && !pmScope.equals(d.scope)) {
                buffer.append(" (scope managed from ${pmScope})");
            }
        }
        val winner = node.data.get(ConflictResolver.NODE_DATA_WINNER)
                     as? DependencyNode;
        if(winner != null && !ArtifactIdUtils.equalsId(a, winner.artifact)) {
            val w = winner.artifact;
            val verMatch = (   ArtifactIdUtils.toVersionlessId(a)
                            == ArtifactIdUtils.toVersionlessId(w));
            buffer.append(" (conflicts with ${if(verMatch) w.version else w})");
        }
        return buffer.toString();
    }

    override public fun visitLeave(node: DependencyNode): Boolean {
        if(!childInfos.empty) {
            childInfos.remove(childInfos.size() - 1);
        }
        if(!childInfos.empty) {
            childInfos.get(childInfos.size() - 1).index++;
        }
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

    public fun exampleCode() {
         println("------------------------------------------------------------");

         val system = Booter().newRS();

         val session = Booter().newRSSession(system);

         session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
         session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);

         val artifact = DefaultArtifact("org.apache.maven:maven-aether-provider:3.1.0");

         val descriptorRequest = ArtifactDescriptorRequest();
         descriptorRequest.setArtifact(artifact);
         descriptorRequest.setRepositories(Booter().newRepositories(system, session));

         val descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

         val collectRequest = CollectRequest();
         collectRequest.setRootArtifact(descriptorResult.artifact);
         collectRequest.setDependencies(descriptorResult.dependencies);
         collectRequest.setManagedDependencies(descriptorResult.managedDependencies);
         collectRequest.setRepositories(descriptorRequest.repositories);

         val collectResult = system.collectDependencies(session, collectRequest);

         collectResult.root.accept(ConsoleDependencyGraphDumper());
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

// Create a remote download repository.
// Parameters:
//   id        - the unique identifier of the repository
//   url       - the URL of the repository
//   layout    - the layout of the repository
//   snapshots - the policies to use for snapshots
//   releases  - the policies to use for releases
//
// MavenArtifactRepository(String id,
//                         String url,
//                         ArtifactRepositoryLayout layout,
//                         ArtifactRepositoryPolicy snapshots,
//                         ArtifactRepositoryPolicy releases)
