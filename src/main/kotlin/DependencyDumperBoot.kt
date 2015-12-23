package org.mcpkg.gradle2nix;

import java.util.Arrays;
import java.util.ArrayList;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;

import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferEvent;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

public class DependencyDumperBoot {
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
                                                      exception: Throwable)
                                                     : Unit {
                exception.printStackTrace();
            }
        });

        return locator.getService(RepositorySystem::class.java);
    }

    public fun newRSSession(system: RepositorySystem)
                           : DefaultRepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession();

        val localRepo = LocalRepository("target/local-repo");
        val repoManager = system.newLocalRepositoryManager(session, localRepo);
        session.setLocalRepositoryManager(repoManager);
        session.setTransferListener(ConsoleTransferListener());
        session.setRepositoryListener(ConsoleRepositoryListener());

        return session;
    }

    public fun newRepositories(system:  RepositorySystem,
                               session: RepositorySystemSession)
                              : ArrayList<RemoteRepository> {
        return ArrayList(Arrays.asList(newCentralRepository()));
    }

    private fun newCentralRepository(): RemoteRepository {
        val mavenCentralURL = "http://central.maven.org/maven2/";
        val builder = RemoteRepository.Builder("central", "default",
                                               mavenCentralURL);
        return builder.build();
    }
}

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
