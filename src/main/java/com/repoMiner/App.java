package com.repoMiner;

import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import java.io.IOException;

import static com.repoMiner.AetherLibraryDependenciesAnalyzer.loadPackageDependencies;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException, ClassNotFoundException, DependencyCollectionException, ArtifactDescriptorException, ArtifactResolutionException {

        loadPackageDependencies("com.rabbitmq:amqp-client:5.4.3");
    }
}
