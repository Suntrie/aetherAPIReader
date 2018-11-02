package com.repoMiner;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.*;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AetherLibraryDependenciesAnalyzer {

    private static DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    private static RepositorySystem system = newRepositorySystem(locator);
    private static RepositorySystemSession session = newSession(system);

    private static RemoteRepository central = new RemoteRepository.
            Builder("central", "default", "http://repo1.maven.org/maven2/").build();


    private String getArtifactCoords(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }


    public static void loadPackageDependencies(String coords) throws DependencyCollectionException, ArtifactDescriptorException,
            ClassNotFoundException, IOException, ArtifactResolutionException {

        Set<Artifact> loadedArtifacts = new HashSet<>();

        Artifact artifact = new DefaultArtifact(coords);

        CollectResult transitiveDependencies = getArtifactTransitiveDependencies(artifact);

        CustomClassLoader customClassLoader = new CustomClassLoader();

        List<Dependency> artifactTransitiveDependenciesList = getArtifactTransitiveDependenciesList(artifact);

        List<Dependency> artifactDirectDependencies = getArtifactDirectDependencies(artifact);

        for (Dependency dependency : artifactDirectDependencies) {
            if (!artifactTransitiveDependenciesList.contains(dependency))
                loadArtifactDirectDependency(dependency, customClassLoader);
        }

        // Загрузка прямых зависимостей
        // Загрузка транзитивных зависимостей

        TreeDependencyVisitor treeDependencyVisitor = new TreeDependencyVisitor(new DependencyVisitor() {

            @Override
            public boolean visitEnter(DependencyNode dependencyNode) {
                if (dependencyNode.getChildren().size() == 0) {
                    try {

                        List<Dependency> artifactDirectDependencies = getArtifactDirectDependencies(dependencyNode.getArtifact());

                        for (Dependency dependency : artifactDirectDependencies) {
                            loadArtifactDirectDependency(dependency, customClassLoader);
                        }

                        logAndLoadDependency(dependencyNode, true, customClassLoader);
                    } catch (IOException | ArtifactResolutionException | ClassNotFoundException | ArtifactDescriptorException | DependencyCollectionException e) {
                        e.printStackTrace();
                    }
                }

                return true;
            }

            @Override
            public boolean visitLeave(DependencyNode dependencyNode) {

                if (dependencyNode.getChildren().size() != 0) {

                    /*System.out.println("exit " + dependencyNode.getArtifact().getArtifactId());

                    System.out.println("dependencies of it");

                    for (DependencyNode dependencyNode1 : dependencyNode.getChildren())
                        System.out.println(dependencyNode1.getArtifact().getArtifactId());

                    System.out.println("dependencies by collectResult listed.");
*//*
                    Artifact artifact = new DefaultArtifact(dependencyNode.getArtifact().getGroupId() + ":" + dependencyNode.getArtifact().getArtifactId() + ":"
                            + dependencyNode.getArtifact().getVersion());

                    processDirectDependencies(system, session, artifact, "according to description", "dependencies by description listed.");
*/

                    try {

                        List<Dependency> artifactDirectDependencies = getArtifactDirectDependencies(dependencyNode.getArtifact());
                        List<Dependency> artifactTransitiveDependenciesList = getArtifactTransitiveDependenciesList(dependencyNode.getArtifact());

                        for (Dependency dependency : artifactDirectDependencies) {

                            if (!artifactTransitiveDependenciesList.contains(dependency))
                                loadArtifactDirectDependency(dependency, customClassLoader);
                        }

                        logAndLoadDependency(dependencyNode, false, customClassLoader);
                    } catch (IOException | ArtifactResolutionException | ClassNotFoundException | ArtifactDescriptorException | DependencyCollectionException e) {
                        e.printStackTrace();
                    }
                }
                return true;
            }


        });


        DependencyNode node = transitiveDependencies.getRoot();

        node.accept(treeDependencyVisitor);

    }

    private static CollectResult getArtifactTransitiveDependencies(Artifact artifact) throws DependencyCollectionException {

        CollectRequest collectRequest = new CollectRequest(
                new Dependency(artifact, null),
                Collections.singletonList(central));
        return system.collectDependencies(session, collectRequest);
    }

    private static List<Dependency> getArtifactTransitiveDependenciesList(Artifact artifact) throws DependencyCollectionException {
        return getArtifactTransitiveDependencies(artifact).getRoot().getChildren().stream().map(DependencyNode::getDependency).collect(Collectors.toList());
    }

    private static void loadArtifactDirectDependency(Dependency dependency,
                                                     CustomClassLoader customClassLoader) throws ArtifactDescriptorException, IOException, ArtifactResolutionException, ClassNotFoundException, DependencyCollectionException {

        //if (getDependencyNodeFromDependency(dependency).g)

        logAndLoadDependency(getDependencyNodeFromDependency(dependency), true, customClassLoader);

    }

    private static DependencyNode getDependencyNodeFromDependency(Dependency dependency) throws DependencyCollectionException {

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.addRepository(central);

        collectRequest.setRoot(dependency);
        return system.collectDependencies(session, collectRequest).getRoot();

    }

    private static void logAndLoadDependency(DependencyNode dependencyNode, Boolean leaf, CustomClassLoader customClassLoader) throws IOException, ArtifactResolutionException, ClassNotFoundException, ArtifactDescriptorException, DependencyCollectionException {

        ArtifactResult artifactResult = system.resolveArtifact(session,
                new ArtifactRequest(dependencyNode).setRepositories(Collections.singletonList(central)));

        Artifact artifact = artifactResult.getArtifact();

        String leafDependencyPath = artifact.getFile().getCanonicalPath();

        System.out.println(artifact.getFile().getName() + (leaf ? " - leaf, " : "- non-leaf, ") + dependencyNode.getDependency().getScope());

        customClassLoader.loadLibraryClassSet(leafDependencyPath);
    }


    private static List<Dependency> getArtifactDirectDependencies(Artifact artifact) throws ArtifactDescriptorException {

        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifact);
        ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

        return descriptorResult.getDependencies();
    }

    private static void processDirectDependencies(RepositorySystem system, RepositorySystemSession session, Artifact artifact, String s, String s2) {
        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifact);
        try {
            ArtifactDescriptorResult descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);

            System.out.println(s);

            for (Dependency dependency : descriptorResult.getDependencies()) {
                System.out.println(dependency.getArtifact().getArtifactId() + " " + dependency.getScope());
            }


            System.out.println(s2);

        } catch (ArtifactDescriptorException e) {
            e.printStackTrace();
        }
    }


    private static RepositorySystem newRepositorySystem(DefaultServiceLocator locator) {
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    static List<Dependency> dependencies=new ArrayList<>();

    private static RepositorySystemSession newSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository("C:\\Users\\Neverland\\.m2\\repository\\");
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        session.setDependencySelector(new DependencySelector() {
            @Override
            public boolean selectDependency(Dependency dependency) {

                dependencies.add(dependency);

                return true;
            }

            @Override
            public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
                return this;
            }
        });

        return session;
    }

}
