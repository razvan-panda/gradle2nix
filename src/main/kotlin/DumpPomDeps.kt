package org.mcpkg.gradle2nix;

import org.apache.maven.model.Repository as MavenR;
import org.apache.maven.artifact.repository.ArtifactRepository as MavenAR;
import org.apache.maven.model.RepositoryPolicy as MavenRP;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy as MavenARP;
import org.apache.maven.artifact.repository.DefaultArtifactRepositoryFactory as DARF;
import org.apache.maven.artifact.Artifact as MavenArtifact;
import org.eclipse.aether.artifact.Artifact as AetherArtifact;
import org.apache.maven.model.Dependency as MavenDependency;
import org.eclipse.aether.graph.Dependency as AetherDependency;
import org.apache.maven.model.Exclusion as MavenExclusion;
import org.eclipse.aether.graph.Exclusion as AetherExclusion;
import org.eclipse.aether.*;
import org.eclipse.aether.internal.impl.*;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.artifact.*;
import org.eclipse.aether.metadata.*;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.repository.*;
import org.eclipse.aether.spi.connector.layout.*;
import org.eclipse.aether.spi.connector.transport.*;
import org.eclipse.aether.transfer.*;
import org.apache.maven.model.*;
import org.apache.maven.model.building.*;
import org.apache.maven.project.*;
import org.eclipse.aether.artifact.*;
import org.eclipse.aether.graph.*;
import org.mcpkg.gradle2nix.*;
import java.io.*;
import java.net.*;
import java.util.*;

data class ArtifactDownloadInfo(var url: String = "", var hash: String = "");

class DumpPomDeps(pomFile: File) {
    private val model = readModel(pomFile);

    private val project: MavenProject;
    private var repoSystem          = DefaultRepositorySystem();
    private var layoutProvider      = DefaultRepositoryLayoutProvider();
    private var transporterProvider = DefaultTransporterProvider();
    private var repoSession         = DefaultRepositorySystemSession();

    init {
         // Initialization code for repoSession goes here
         repoSession.setReadOnly();
         project = MavenProject(model);
         populateMavenProject(project);
    }

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

    private fun populateMavenProject(proj: MavenProject) {
        val repos = HashSet<Repository>();
        repos.addAll(model.repositories);
        repos.addAll(model.pluginRepositories);
        val artifactRepos = (repos.map { convertRtoAR(it) }).toArrayList();

        proj.pluginArtifactRepositories = artifactRepos;
    }

    private fun convertRtoAR(r: MavenR): MavenAR {
        val factory = DARF.DefaultArtifactRepositoryFactory();
        return factory.createArtifactRepository(r.id, r.url, r.layout,
                                                convertRPtoARP(r.snapshots),
                                                convertRPtoARP(r.releases));
    }

    private fun convertRPtoARP(rp: MavenRP): MavenARP
        = MavenARP(Boolean(rp.enabled), rp.updatePolicy, rp.checksumPolicy);

    private fun readModel(file: File): Model {
        val factory = DefaultModelBuilderFactory();
        val builder = factory.newInstance();
        val req = DefaultModelBuildingRequest();
        req.setProcessPlugins(false);
        req.setPomFile(file);
        req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
        return builder.build(req).effectiveModel;
    }

    private fun emitArtifactBody(art: Artifact,
                                 deps: Collection<AetherDependency>?)
        = emitArtifactBody(art, deps, 0);

    private fun emitArtifactBody(art: Artifact,
                                 deps: Collection<AetherDependency>?,
                                 indent: Int) {
        val istr = "  ".repeat(indent);
        println("${istr}artifactId: ${art.artifactId}");
        println("${istr}groupId: ${art.groupId}");
        println("${istr}version: ${art.version}");
        println("${istr}classifier: ${art.classifier}");
        println("${istr}extension: ${art.extension}");
        if(deps != null) {
            println("${istr}dependencies:");
            for(dep in deps) {
                emitArtifactBody(dep.artifact, null, indent + 1);
                println("${istr}  scope: ${dep.scope}");
                println("${istr}  optional: ${dep.isOptional()}");
                println("${istr}  exclusions:");
                for(excl in dep.exclusions) {
                    println("${istr}    artifactId: ${excl.artifactId}");
                    println("${istr}    classifier: ${excl.classifier}");
                    println("${istr}    extension: ${excl.extension}");
                    println("${istr}    groupId: ${excl.groupId}");
                }
            }
        }
    }

    fun handleDependency(dep:     AetherDependency,
                         repos:   List<RemoteRepository>,
                         work:    MutableSet<AetherDependency>,
                         printed: MutableSet<Artifact>) {
        var art = dep.artifact;
        var metadataInfo: ArtifactDownloadInfo? = null;
        val unresolvedVersion = art.version;
        if(art.isSnapshot()) {
            val vReq = VersionRequest(art, repos, null);
            val res: VersionResult;
            try {
                res = repoSystem.resolveVersion(repoSession, vReq);
            } catch(e: VersionResolutionException) {
                throw Exception("Resolving version of ${art}", e);
            }
            if(res.version != art.version) {
                art = DefaultArtifact(art.groupId,
                                      art.artifactId,
                                      art.classifier,
                                      art.extension,
                                      res.version);
                if(res.repository is RemoteRepository) {
                    val repo = res.repository as RemoteRepository;
                    val metadata
                        = DefaultMetadata(art.groupId,
                                          art.artifactId,
                                          unresolvedVersion,
                                          "maven-metadata.xml",
                                          Metadata.Nature.RELEASE_OR_SNAPSHOT);
                    val layout: RepositoryLayout;
                    try {
                        layout = layoutProvider.newRepositoryLayout(repoSession, repo);
                    } catch(e: NoRepositoryLayoutException) {
                        throw Exception("Getting repository layout", e);
                    }
                    val base = repo.url;
                    /* TODO: Open the transporters all at
                     * once */
                    transporterProvider.newTransporter(repoSession, repo).use {
                        try {
                            metadataInfo = getDownloadInfo(metadata, layout,
                                                           base, it);
                        } catch(e: NoTransporterException) {
                            throw Exception("No transporter for ${art}", e);
                        }
                    }
                }
            }
        }
        val req = ArtifactDescriptorRequest(art, repos, null);
        val res: ArtifactDescriptorResult;
        try {
            res = repoSystem.readArtifactDescriptor(repoSession, req);
        } catch(e: ArtifactDescriptorException) {
            throw Exception("getting descriptor for ${art}", e);
        }

        /* Ensure we're keying on the things we care about */
        val artKey = DefaultArtifact(art.groupId,
                                     art.artifactId,
                                     art.classifier,
                                     art.extension,
                                     unresolvedVersion);
        if(printed.add(artKey)) {
            emitArtifactBody(art, res.dependencies);
            if(metadataInfo != null) {
                println("unresolved-version: ${unresolvedVersion}");
                println("repository-id: ${res.repository.id}");
                println("metadata:");
                println("  url: ${metadataInfo!!.url}");
                println("  sha1: ${metadataInfo!!.hash}");
            }
            val repo = res.repository;
            if(repo is RemoteRepository) {
                println("authenticated: ${repo.authentication != null}");
                val layout: RepositoryLayout;
                try {
                    layout = layoutProvider.newRepositoryLayout(repoSession, repo);
                } catch(e: NoRepositoryLayoutException) {
                    throw Exception("Getting repository layout", e);
                }

                val base = repo.url;
                /* TODO: Open the transporters all at once */
                transporterProvider.newTransporter(repoSession, repo).use {
                    try {
                        var info = getDownloadInfo(art, layout, base, it);
                        println("url: ${info.url}");
                        println("sha1: ${info.hash}");

                        println("relocations:");
                        for(rel in res.relocations) {
                            info = getDownloadInfo(art, layout, base, it);
                            println("  url: ${info.url}");
                            println("  sha1: ${info.hash}");
                        }
                    } catch(e: NoTransporterException) {
                        throw Exception("No transporter for ${art}", e);
                    }
                }
            }
        }

        if(art.extension != "pom") {
            val pomArt = DefaultArtifact(art.groupId,
                                         art.artifactId,
                                         null, "pom",
                                         unresolvedVersion);
            val pomDep = AetherDependency(pomArt,
                                          "compile",
                                          false,
                                          dep.exclusions);
            work.add(pomDep);
        }

        for(subDep in res.dependencies) {
            if(subDep.optional) { continue; }
            val scope = subDep.scope;
            if(scope == "provided" || scope == "test" || scope == "system") {
                continue;
            }
            val subArt = subDep.artifact;
            val excls = HashSet<AetherExclusion>();
            var excluded = false;
            for(excl in dep.exclusions) {
                if(excl.artifactId == subArt.artifactId &&
                   excl.groupId == subArt.groupId) {
                    excluded = true;
                    break;
                }
                excls.add(excl);
            }

            if(excluded) { continue; }

            for(excl in subDep.exclusions) { excls.add(excl); }

            val newDep = AetherDependency(subArt, dep.scope,
                                          dep.optional, excls);

            work.add(newDep);
        }
    }


    private fun downloadInfo(base: String,
                             fileLoc: URI,
                             checksums: List<RepositoryLayout.Checksum>,
                             desc: String,
                             transport: Transporter): ArtifactDownloadInfo {

        val abs: URI;
        try {
            abs = URI(base + "/" + fileLoc);
        } catch(e: URISyntaxException) {
            throw Exception("Parsing repository URI", e);
        }

        var res = ArtifactDownloadInfo();
        res.url = abs.toString();

        var task: GetTask? = null;
        for(ck in checksums) {
            if(ck.algorithm == "SHA-1") {
                task = GetTask(ck.getLocation());
                break;
            }
        }

        if(task == null) { throw Exception("No SHA-1 for ${desc}"); }

        try {
            transport.get(task);
        } catch(e: Exception) {
            throw Exception("Downloading SHA-1 for ${desc}", e);
        }

        try {
            res.hash = String(task.getDataBytes(), 0, 40, "UTF-8");
        } catch(e: UnsupportedEncodingException) {
            throw Exception("Your JVM doesn't support UTF-8", e);
        }
        return res;
    }

    private fun getDownloadInfo(art:       Artifact,
                                layout:    RepositoryLayout,
                                base:      String,
                                transport: Transporter): ArtifactDownloadInfo {
        val fileLoc = layout.getLocation(art, false);
        val checksums = layout.getChecksums(art, false, fileLoc);
        return downloadInfo(base, fileLoc, checksums,
                            art.toString(), transport);
    }

    private fun getDownloadInfo(metadata:  Metadata,
                                layout:    RepositoryLayout,
                                base:      String,
                                transport: Transporter): ArtifactDownloadInfo {
        val fileLoc = layout.getLocation(metadata, false);
        val checksums = layout.getChecksums(metadata, false, fileLoc);
        return downloadInfo(base, fileLoc, checksums,
                            metadata.toString(), transport);
    }

    public fun execute() {
        val work = HashSet<AetherDependency>();
        val seen = HashSet<AetherDependency>();
        val printed = HashSet<Artifact>();
        for(p in project.getBuildPlugins()) {
            val art = DefaultArtifact(p.groupId, p.artifactId,
                                      null, "jar", p.version);
            val dep = AetherDependency(art, "compile");
            work.add(dep);
            for(subDep in p.dependencies) {
                work.add(fromMavenDependency(subDep));
            }
        }
        for(dep in project.dependencies) {
            work.add(fromMavenDependency(dep));
        }
        println("project:");
        if(project.artifact != null) {
            emitArtifactBody(fromMavenArtifact(project.artifact), work, 1);
        }

        println("dependencies:");

        println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        println("| activeProfiles              | ${project.activeProfiles}");
        println("| artifact                    | ${project.artifact}");
        println("| artifactId                  | ${project.artifactId}");
        println("| artifactMap                 | ${project.artifactMap}");
        println("| artifacts                   | ${project.artifacts}");
        println("| attachedArtifacts           | ${project.attachedArtifacts}");
        println("| basedir                     | ${project.basedir}");
        println("| build                       | ${project.build}");
        println("| buildExtensions             | ${project.buildExtensions}");
        println("| buildPlugins                | ${project.buildPlugins}");
        println("| ciManagement                | ${project.ciManagement}");
        println("| classRealm                  | ${project.classRealm}");
        println("| collectedProjects           | ${project.collectedProjects}");
        println("| compileClasspathElements    | ${project.compileClasspathElements}");
        println("| compileSourceRoots          | ${project.compileSourceRoots}");
        println("| contributors                | ${project.contributors}");
        println("| defaultGoal                 | ${project.defaultGoal}");
        println("| dependencies                | ${project.dependencies}");
        println("| dependencyManagement        | ${project.dependencyManagement}");
        println("| description                 | ${project.description}");
        println("| developers                  | ${project.developers}");
        println("| distributionManagement      | ${project.distributionManagement}");
        println("| executionProject            | ${project.executionProject}");
        println("| extensionDependencyFilter   | ${project.extensionDependencyFilter}");
        println("| file                        | ${project.file}");
        println("| filters                     | ${project.filters}");
        println("| groupId                     | ${project.groupId}");
        println("| id                          | ${project.id}");
        println("| inceptionYear               | ${project.inceptionYear}");
        println("| injectedProfileIds          | ${project.injectedProfileIds}");
        println("| issueManagement             | ${project.issueManagement}");
        println("| licenses                    | ${project.licenses}");
        println("| mailingLists                | ${project.mailingLists}");
        println("| managedVersionMap           | ${project.managedVersionMap}");
        println("| model                       | ${project.model}");
        println("| modelVersion                | ${project.modelVersion}");
        println("| modules                     | ${project.modules}");
        println("| name                        | ${project.name}");
        println("| organization                | ${project.organization}");
        println("| originalModel               | ${project.originalModel}");
        println("| packaging                   | ${project.packaging}");
        println("| parent                      | ${project.parent}");
        println("| parentArtifact              | ${project.parentArtifact}");
        println("| parentFile                  | ${project.parentFile}");
        println("| pluginArtifactMap           | ${project.pluginArtifactMap}");
        println("| pluginArtifactRepositories  | ${project.pluginArtifactRepositories}");
        println("| pluginArtifacts             | ${project.pluginArtifacts}");
        println("| pluginManagement            | ${project.pluginManagement}");
        println("| pluginRepositories          | ${project.pluginRepositories}");
        println("| prerequisites               | ${project.prerequisites}");
        println("| projectReferences           | ${project.projectReferences}");
        println("| properties                  | ${project.properties}");
        println("| remoteArtifactRepositories  | ${project.remoteArtifactRepositories}");
        println("| remotePluginRepositories    | ${project.remotePluginRepositories}");
        println("| remoteProjectRepositories   | ${project.remoteProjectRepositories}");
        println("| repositories                | ${project.repositories}");
        println("| resources                   | ${project.resources}");
        println("| runtimeClasspathElements    | ${project.runtimeClasspathElements}");
        println("| scm                         | ${project.scm}");
        println("| testClasspathElements       | ${project.testClasspathElements}");
        println("| testCompileSourceRoots      | ${project.testCompileSourceRoots}");
        println("| testResources               | ${project.testResources}");
        println("| url                         | ${project.url}");
        println("| version                     | ${project.version}");
        println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");
        println("DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG DEBUG");

        val repos = HashSet<RemoteRepository>();
        repos.addAll(project.remoteProjectRepositories);
        repos.addAll(project.remotePluginRepositories);
        val rl = repos.toList();

        while(!work.isEmpty()) {
            val iter = work.iterator();
            val dep = iter.next();
            iter.remove();

            if(seen.add(dep)) { handleDependency(dep, rl, work, printed); }
        }
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
